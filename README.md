# Flutter Assets Generator

A configurable Flutter asset code generator for **Android Studio** and **IntelliJ IDEA**.

Flutter Assets Generator scans your Flutter project's `assets/` directory and generates a Dart `Assets.dart` file from your asset files. Choose between **Legacy / Flat** and **Hierarchical** generation styles, customize naming and leaf types, and regenerate the complete asset index with a single keyboard shortcut.

## Features

* Recursive Flutter asset scanning
* **Legacy / Flat** generation
* **Hierarchical** generation
* `camelCase` and `snake_case` naming
* `String` and `Class` leaf types
* Optional parent-directory inclusion
* Configurable output directory
* Configurable output filename
* Configurable generated class name
* Automatic Dart identifier sanitization
* Deterministic collision handling
* Complete regeneration on every run
* Background generation
* Success and error notifications
* Single **Alt+G** generation shortcut
* Modern IntelliJ Platform APIs

---

## Generation Styles

Choose the generation style that fits your project.

### Legacy / Flat

Generates a flat API where directory names are combined with the asset name.

```dart
Assets.imagesLogo
Assets.imagesHomeScreen
Assets.iconsUserProfile
```

**Default leaf type:** `String`

Example:

```dart
class Assets {
  Assets._();

  static const String imagesLogo = 'assets/images/logo.png';
  static const String imagesHomeScreen = 'assets/images/home_screen.png';
  static const String iconsUserProfile = 'assets/icons/user-profile.svg';
}
```

---

### Hierarchical

Generates a directory-based API.

```dart
Assets.images.logo
Assets.images.homeScreen
Assets.icons.userProfile
```

With the default **Class** leaf type, an asset can be accessed as:

```dart
Assets.images.logo.path
```

**Default leaf type:** `Class`

Example:

```dart
Assets.images.logo.path
Assets.images.homeScreen.path
Assets.icons.userProfile.path
```

---

## Default Configuration

A new installation uses:

| Setting                  | Default          |
| ------------------------ | ---------------- |
| Generation Style         | **Hierarchical** |
| Naming Style             | **camelCase**    |
| Leaf Type                | **Class**        |
| Include Parent Directory | **Enabled**      |
| Output Directory         | `lib/generated/` |
| Output Filename          | `Assets.dart`    |
| Class Name               | `Assets`         |

The default configuration therefore produces the hierarchical class-based API:

```dart
Assets.images.logo.path
```

You can change these options from the plugin settings.

---

## Generate Assets

The plugin uses a single generation shortcut:

### Windows / Linux

```text
Alt+G
```

### macOS

```text
⌥G
```

The shortcut always uses the **currently configured settings**.

For example:

```text
Alt+G
   ↓
Read current settings
   ↓
Scan Flutter assets
   ↓
Generate using selected style
   ↓
Write Assets.dart
   ↓
Show notification
```

The shortcut is not tied to a particular generation style. Changing the generation style in Settings changes what `Alt+G` generates.

The action is also available from the IDE's action/menu system.

---

## Asset Structure

For example:

```text
assets/
├── images/
│   ├── logo.png
│   └── home_screen.png
├── icons/
│   └── user-profile.svg
└── data/
    └── app_config.json
```

### Legacy / Flat

```dart
Assets.imagesLogo
Assets.imagesHomeScreen
Assets.iconsUserProfile
Assets.dataAppConfig
```

### Hierarchical

```dart
Assets.images.logo
Assets.images.homeScreen
Assets.icons.userProfile
Assets.data.appConfig
```

With the default Class leaf type:

```dart
Assets.images.logo.path
Assets.images.homeScreen.path
Assets.icons.userProfile.path
Assets.data.appConfig.path
```

---

## Naming Styles

The plugin supports two naming styles.

### camelCase

```text
home_screen.png
```

becomes:

```dart
homeScreen
```

For example:

```dart
Assets.images.homeScreen
```

### snake_case

```text
home_screen.png
```

becomes:

```dart
home_screen
```

Naming is applied consistently to generated identifiers.

---

## Leaf Types

The **Leaf Type** setting controls how individual assets are represented.

### String

The asset resolves directly to its path:

```dart
static const String logo = 'assets/images/logo.png';
```

Use this when you want a simple asset path.

### Class

The asset is represented by a generated class.

For example:

```dart
Assets.images.logo.path
```

The class representation allows the generated asset to expose its path through the generated API.

### Default by Generation Style

| Generation Style | Default Leaf Type |
| ---------------- | ----------------- |
| Legacy / Flat    | **String**        |
| Hierarchical     | **Class**         |

The leaf type can be changed in Settings.

---

## Include Parent Directory

The **Include Parent Directory** option controls whether parent folders are included when creating asset identifiers.

When enabled:

```text
assets/images/home.png
```

becomes:

```text
imagesHome
```

instead of:

```text
home
```

This helps avoid collisions between assets with the same filename.

For example:

```text
assets/images/home.png
assets/icons/home.png
```

can become:

```dart
Assets.imagesHome
Assets.iconsHome
```

In hierarchical mode, directories naturally become hierarchy levels:

```dart
Assets.images.home
Assets.icons.home
```

---

## Dart Identifier Safety

Asset filenames and directories may contain characters that are not valid in Dart identifiers.

The generator sanitizes names containing:

* Spaces
* Hyphens
* Dots
* Special characters
* Multiple separators
* Leading numbers

Examples:

```text
old photo.jpg
```

becomes:

```text
oldPhoto
```

```text
user-profile.svg
```

becomes:

```text
userProfile
```

```text
123-logo.png
```

becomes a valid Dart identifier.

Generated identifiers are sanitized consistently and collisions are handled deterministically.

---

## Collision Handling

The generator prevents duplicate identifiers.

For example, if multiple assets resolve to the same identifier, the generator creates unique names rather than producing invalid Dart code.

Parent-directory inclusion also helps distinguish common filenames such as:

```text
assets/images/home.png
assets/icons/home.png
```

---

## Complete Regeneration

Every time `Alt+G` is executed, the complete asset index is regenerated.

The generator does not append new entries to the previous file.

This means:

* Added assets appear automatically.
* Deleted assets disappear.
* Renamed assets are updated.
* Moved assets are updated.
* Naming changes are reflected.
* Generation-style changes are reflected.
* Leaf-type changes are reflected.

The generated `Assets.dart` always represents the current asset tree.

---

## Configuration

Open:

```text
Settings / Preferences
→ Tools
→ Flutter Assets Generator
```

Available options include:

### Output

* Output Directory
* Output Filename
* Class Name

### Generation

* Generation Style

  * Legacy / Flat
  * Hierarchical
* Naming Style

  * camelCase
  * snake_case
* Leaf Type

  * String
  * Class
* Include Parent Directory

The settings are used automatically whenever `Alt+G` is executed.

---

## Installation

### JetBrains Marketplace

Install **Flutter Assets Generator** directly from the JetBrains Marketplace.

After installation, restart the IDE if requested.

### Install from ZIP

If you have a plugin distribution ZIP:

1. Open Android Studio or IntelliJ IDEA.
2. Open **Settings / Preferences**.
3. Go to **Plugins**.
4. Click the gear icon.
5. Select **Install Plugin from Disk...**
6. Select the plugin ZIP.
7. Apply the changes.
8. Restart the IDE if requested.

---

## Requirements

The plugin is designed for modern versions of:

* Android Studio
* IntelliJ IDEA

The project uses the IntelliJ Platform plugin APIs and does not require the original FlutterAssetsGenerator plugin.

---

## Usage

1. Open a Flutter project.
2. Make sure the project contains an `assets/` directory.
3. Configure the generator if required.
4. Press **Alt+G** (`⌥G` on macOS).
5. The plugin scans the asset tree.
6. `Assets.dart` is regenerated using the current settings.

Example:

```text
my_flutter_app/
├── assets/
│   ├── images/
│   │   └── logo.png
│   └── icons/
│       └── settings.svg
├── lib/
│   └── generated/
│       └── Assets.dart
└── pubspec.yaml
```

---

## Generated Output

The generated file is intended to be used directly from your Flutter code.

For hierarchical class-based generation:

```dart
import 'generated/Assets.dart';

Image.asset(
  Assets.images.logo.path,
);
```

For a String-based configuration:

```dart
Image.asset(
  Assets.images.logo,
);
```

The exact API depends on the selected generation style and leaf type.

---

## Development

Clone the repository and open the project in IntelliJ IDEA or Android Studio.

Build the plugin with:

```powershell
.\gradlew.bat clean buildPlugin
```

The generated plugin ZIP is placed under:

```text
build\distributions\
```

---

## Design Goals

Flutter Assets Generator is designed around a few simple principles:

* **Configurable** — choose the generated API that fits your project.
* **Predictable** — deterministic asset scanning and naming.
* **Safe** — generated identifiers are valid Dart identifiers.
* **Complete** — every generation replaces the previous generated output.
* **Simple** — one shortcut generates using the current configuration.
* **Modern** — built using current IntelliJ Platform APIs.

---

## Relationship to Other Asset Generators

Flutter Assets Generator is an independent implementation.

It provides configurable flat and hierarchical asset-generation workflows without depending on another asset-generator plugin.

The generated APIs may be familiar to users of other Flutter asset generators, but the plugin has its own implementation and configuration system.

---

## License

This project is licensed under the **Apache License 2.0**.

See [LICENSE](LICENSE) for details.

---

## Support

If you find a bug or have a feature request, please open an issue in the GitHub repository.

When reporting a generation issue, include:

* Generation Style
* Naming Style
* Leaf Type
* Include Parent Directory setting
* Example asset structure
* Generated output
* Expected output

This makes generation issues easier to reproduce and resolve.
