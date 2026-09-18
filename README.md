# FlutterAssetsGenerator (Modern)

A small Android Studio / IntelliJ plugin that regenerates a flat, type-safe
`Assets.dart` index for a Flutter project's `assets/` directory. Press
**Alt+G** (⌥G on macOS) and it's done - no dialogs, no manual selection.

This is a from-scratch, modern-API reimplementation that reproduces the
useful behavior of the old **FlutterAssetsGenerator 2.4.2** plugin
(`com.crzsc.FlutterAssetsGenerator`, https://github.com/cr1992/FlutterAssetsGenerator),
which is no longer compatible with current Android Studio. It does **not**
copy any of that plugin's code or its old, now-incompatible IntelliJ APIs -
it's built fresh against the current IntelliJ Platform Gradle Plugin (2.x).

## What behavior was reproduced from 2.4.2, and how we know

The upstream project's `master` branch has since been rewritten into a 3.x
generator with a completely different, *hierarchical* API
(`Assets.images.logo`, typed `AssetGenImage`/`SvgGenImage` wrappers, YAML
configuration in `pubspec.yaml`, etc.) - not what this task asked for.
However, that same upstream README documents an explicit
**`style: legacy`** compatibility mode that it added specifically so 3.x
users could keep the old 2.x output shape:

> **Legacy Compatibility**: Supports `style: legacy` to generate flat
> variable names (e.g. `Assets.imagesLogo`) for easy migration.
>
> ...`named_with_parent: true` - Prefix variable names with parent
> directory names. Default: `true`
>
> ...`leaf_type` - Default: `class` for `robust`, **`string` for `legacy`**

That confirms, in the authors' own words, exactly the three behaviors this
plugin reproduces from 2.4.2:

1. **Flat, camelCase identifiers** combining every parent directory name
   under the assets root with the filename (minus extension) -
   `Assets.imagesLogo`, not `Assets.images.logo`.
2. **Directory name(s) prefixed onto the identifier** (`named_with_parent`)
   so that, e.g., `icons/home.png` and `images/home.png` both stay
   distinguishable (`iconsHome` / `imagesHome`) instead of colliding.
3. **Plain `static const String` constants** (`leaf_type: string`), not
   typed asset-wrapper classes - matching the plugin's own historical
   `2.4.2` marketplace listing, whose only usage example is a bare string
   constant (`Assets.imageLoading`) passed straight into `Image.asset(...)`.

Two things were **not** carried over from 2.4.2, deliberately:

- **File-watching / auto-regeneration on save.** You asked specifically for
  "press Alt+G", not a background watcher, to keep this small and
  predictable.
- **Any Android-plugin-specific or pre-2.x IntelliJ API.** This plugin is
  built against the current `org.jetbrains.intellij.platform` Gradle
  plugin (2.x) and only uses generic, stable IntelliJ Platform APIs (file
  I/O, actions, notifications, persistent settings) - nothing from
  `org.jetbrains.android` and nothing deprecated.

If you find a specific 2.4.2 naming edge case this doesn't match, it's easy
to adjust in one place: `AssetNameConverter.kt` (see "Architecture" below).

## Requirements

- **JDK 17+**
- **Gradle 9.0+** (the included wrapper handles this for you)
- Android Studio Ladybug (2024.2) or newer to *run/install* the built plugin
  (see [Compatibility](#android-studio-compatibility) below for how to
  target older/newer versions)

## Building

```bash
cd FlutterAssetsGenerator
./gradlew buildPlugin
```

This produces:

```text
build/distributions/FlutterAssetsGenerator-1.0.0.zip
```

> The very first build downloads a full Android Studio installer as the
> compile/test target (`androidStudio(...)` in `build.gradle.kts`), so it
> can be a large (~1 GB+) one-time download. If you just want a faster
> local dev loop while editing the plugin, temporarily swap that one line
> for `intellijIdeaCommunity(properties("platformVersion"))` - this plugin
> doesn't use any Android-specific classes, so either target compiles and
> runs the same code.

Other useful tasks:

```bash
./gradlew runIde          # launch a sandboxed IDE with the plugin installed, for manual testing
./gradlew verifyPlugin     # run the IntelliJ Plugin Verifier against the configured IDE targets
./gradlew test             # (no tests included by default, but wired up and ready)
```

## Installing

1. Run `./gradlew buildPlugin`.
2. In Android Studio: **Settings → Plugins → ⚙️ (gear icon) → Install
   Plugin from Disk...**
3. Select `build/distributions/FlutterAssetsGenerator-1.0.0.zip`.
4. Restart Android Studio when prompted.

## Usage

1. Open a Flutter project (one with a `pubspec.yaml` at its root) in
   Android Studio.
2. Make sure it has an `assets/` directory (configurable - see
   [Settings](#settings)).
3. Press **Alt+G** (⌥G on macOS) anywhere in the IDE, or use
   **Tools → Generate Flutter Assets** from the menu.
4. `lib/generated/Assets.dart` is created (or completely rewritten) to
   reflect exactly what's currently under `assets/`. A notification balloon
   confirms how many assets were written, or explains what went wrong.

Run it again any time after adding, deleting, or renaming assets - the file
is fully regenerated from scratch every time, so it never accumulates stale
or duplicate entries.

### Settings

**Settings → Tools → Flutter Assets Generator**

| Setting            | Default                    |
| ------------------- | --------------------------- |
| Assets directory     | `assets`                    |
| Generated file        | `lib/generated/Assets.dart` |
| Class name            | `Assets`                    |

All three are optional - the plugin works immediately after installation
with no configuration.

## How Alt+G works

`GenerateAssetsAction` (in `actions/GenerateAssetsAction.kt`) is registered
in `plugin.xml` with a default `alt G` keyboard shortcut. When triggered it:

1. Runs inside a background `Task` (via `ProgressManager`) so scanning a
   large assets tree never freezes the IDE UI.
2. Confirms the project looks like a Flutter project (`pubspec.yaml` exists
   at the project root) and that the configured assets directory exists -
   if not, it shows a notification and stops (never throws/crashes the IDE).
3. Delegates the actual filesystem walk to `AssetScanner.scan(...)`.
4. Delegates identifier generation + duplicate-collision handling to
   `DartAssetsGenerator.buildEntries(...)`.
5. Delegates Dart source rendering to `DartAssetsGenerator.renderDartFile(...)`.
6. Writes the result to disk and asynchronously refreshes the IDE's virtual
   file system so the editor picks up the change immediately.
7. Shows a success/warning/error notification.

If the shortcut conflicts with something already bound in your keymap, the
IDE's standard "keyboard shortcut conflict" dialog will offer to reassign it
on plugin install - after that you can always change it yourself under
**Settings → Keymap → Plugins → Flutter Assets Generator (Modern) → Generate
Flutter Assets**.

## How the asset-name conversion works

Implemented in `core/AssetNameConverter.kt`, driven by the asset's path
*relative to the assets root* (e.g. `images/profile/user.png`, not
`assets/images/profile/user.png`):

1. Split the relative path into: each parent directory segment under the
   assets root, plus the filename **without its extension**.
2. Split every one of those segments further into "words" - any run of
   ASCII letters/digits is a word; everything else (spaces, `-`, `_`, `.`,
   `@`, parentheses, ...) is treated as a separator and dropped. This is
   what makes special characters safe.
3. Join all the words camelCase-style: the very first word is lowercased in
   full (this becomes the leading directory-name segment), every following
   word gets its first letter capitalized.
4. If the resulting identifier would start with a digit (e.g. an asset
   literally named `2x/icon.png` sitting directly under the assets root),
   prefix it with `a` so it stays a syntactically valid Dart identifier.
5. If the resulting identifier happens to be a Dart reserved word (e.g. an
   asset named `class.png`), append `Asset` to it.
6. If two different assets would still produce the *same* identifier after
   all of the above (a true collision, not just sharing a directory), the
   second one gets a numeric suffix (`imagesHome`, `imagesHome2`, ...)
   instead of silently overwriting the first, and a warning is included in
   the notification listing exactly which assets collided.

Deterministic ordering: `AssetScanner` sorts all scanned files by their
relative path before anything else happens, so re-running Alt+G on an
unchanged asset tree always produces byte-identical output.

## Example asset trees → generated output

**Example 1** - the tree from the original request:

```text
assets/
├── images/
│   ├── logo.png
│   ├── home.png
│   └── profile/
│       └── user.png
├── icons/
│   └── settings.svg
└── sounds/
    └── click.mp3
```

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND
// Generated by FlutterAssetsGenerator - press Alt+G to regenerate.

// ignore_for_file: type=lint

class Assets {
  Assets._();

  static const String iconsSettings = 'assets/icons/settings.svg';
  static const String imagesHome = 'assets/images/home.png';
  static const String imagesLogo = 'assets/images/logo.png';
  static const String imagesProfileUser = 'assets/images/profile/user.png';
  static const String soundsClick = 'assets/sounds/click.mp3';
}
```

*(Output is sorted by asset path, which is why the order here differs
slightly from the input tree.)*

**Example 2** - special characters, numbers, and duplicate-prefix handling:

```text
assets/
├── images/
│   ├── home_screen.png
│   ├── old photo.jpg
│   └── home.png
├── icons/
│   ├── user-profile.svg
│   └── home.png
└── data/
    └── app_config.json
```

```dart
class Assets {
  Assets._();

  static const String dataAppConfig = 'assets/data/app_config.json';
  static const String iconsHome = 'assets/icons/home.png';
  static const String iconsUserProfile = 'assets/icons/user-profile.svg';
  static const String imagesHome = 'assets/images/home.png';
  static const String imagesHomeScreen = 'assets/images/home_screen.png';
  static const String imagesOldPhoto = 'assets/images/old photo.jpg';
}
```

Note `iconsHome` vs. `imagesHome`: both files are literally named
`home.png`, but their directory prefixes keep the generated identifiers
distinct, so neither asset is silently dropped or overwritten.

**Example 3** - a genuine identifier collision (two *different* relative
paths that would still produce the same identifier):

```text
assets/
├── images/logo.png
└── images_logo.png     (a stray file directly under assets/)
```

Both would naively convert to `imagesLogo`. The second one gets
disambiguated automatically:

```dart
class Assets {
  Assets._();

  static const String imagesLogo = 'assets/images/logo.png';
  static const String imagesLogo2 = 'assets/images_logo.png';
}
```

...and the notification balloon calls out that a collision was resolved,
so you know to double check it (and rename the asset if the auto-suffix
isn't what you want).

## Architecture

```text
actions/
  GenerateAssetsAction.kt      Orchestrates the Alt+G command; talks to the IDE
                                (progress, notifications, VFS) and delegates all
                                actual logic to core/*.
core/
  FlutterProjectDetector.kt    Finds the project root / assets dir / output file
                                using the IDE's own project info - no manual
                                path entry required.
  AssetScanner.kt              Recursively walks the assets directory. Extension-
                                agnostic: every regular, non-hidden file counts.
  AssetNameConverter.kt        Pure function: asset relative path -> flat, valid,
                                camelCase Dart identifier (the 2.4.2 "legacy"
                                naming rules).
  DartAssetsGenerator.kt       Builds the de-duplicated, sorted list of asset
                                entries and renders the final Assets.dart source.
settings/
  FlutterAssetsGeneratorSettings.kt      Persistent per-project settings
                                          (assets dir / output file / class name).
  FlutterAssetsGeneratorConfigurable.kt  Settings > Tools UI for the above.
notifications/
  Notifier.kt                  Thin helper around the balloon-notification API.
```

`core/*` has zero dependencies on IntelliJ Platform APIs - it's plain
Kotlin operating on `java.io.File` and strings, so it's straightforward to
unit test or reuse outside the plugin if you ever want to.

## Error handling

The plugin is designed to never crash the IDE:

- **No Flutter project** (`pubspec.yaml` missing) → warning notification, no-op.
- **No assets directory** → warning notification naming the configured path, no-op.
- **Empty assets directory** (e.g. every asset was just deleted) → still
  regenerates the file with an empty class body, so `Assets.dart` always
  reflects reality instead of going stale; the notification makes clear
  that zero assets were found so it's not mistaken for a bug.
- **Inaccessible / unreadable files** → skipped individually during the
  scan rather than aborting the whole run.
- **Invalid characters in filenames** → sanitized away by `AssetNameConverter`
  (see above); never produces invalid Dart syntax.
- **Duplicate generated identifiers** → auto-disambiguated with a numeric
  suffix; reported in the notification, never silently dropped.
- **Can't write the output file** (permissions, locked file, missing
  parent that can't be created, etc.) → caught and reported as an error
  notification with the underlying message.
- Any other unexpected exception is caught at the top level of the action
  and turned into an error notification rather than propagating.

## Android Studio compatibility

- Built against the **IntelliJ Platform Gradle Plugin 2.x**
  (`org.jetbrains.intellij.platform`, requires Gradle 9.0+ and JDK 17+),
  which is the current, actively maintained successor to the old (`0.x`/`1.x`)
  Gradle IntelliJ Plugin that the original 2.4.2 build used.
- The Gradle build targets Android Studio directly via the
  `androidStudio(...)` dependency helper (see `build.gradle.kts` /
  `gradle.properties` → `platformVersion`), following JetBrains' documented
  approach for Android Studio plugin development.
- The plugin only depends on `com.intellij.modules.platform` - it uses no
  Android-specific (`org.jetbrains.android`) or Dart-plugin-specific APIs,
  so the same build also works in plain IntelliJ IDEA (Community or
  Ultimate) with the Flutter/Dart plugins installed, if you ever want that.
- `pluginSinceBuild=242` in `gradle.properties` corresponds to the 2024.2
  (Ladybug) platform branch, with `pluginUntilBuild` left **open-ended** so
  the plugin keeps installing on newer Android Studio releases without
  needing a republish for every point release. If a future Android Studio
  version introduces a breaking platform API change, bump `platformVersion`
  (and re-test) rather than assuming the open-ended range always holds.
- No deprecated actions/notification APIs are used: actions declare
  `getActionUpdateThread()` explicitly (required on current versions),
  and notifications go through `NotificationGroupManager` +
  a `<notificationGroup>` extension point, not the old
  `Notifications.Bus`/balloon APIs.
- The settings UI is implemented with plain Swing (`FormBuilder` +
  `JBTextField`) rather than the Kotlin UI DSL, specifically because the UI
  DSL's API has changed across platform releases - for three text fields,
  the extra Swing boilerplate is worth the long-term source stability.

If you target an Android Studio version significantly older than 2024.2,
lower `pluginSinceBuild`/`platformVersion` accordingly and re-test; nothing
in this plugin's own code requires anything newer than a very old platform
baseline, but the Gradle plugin/toolchain requirements above are hard
floors.
