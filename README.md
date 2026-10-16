# Flutter Assets Generator

A configurable asset code generator for **Android Studio** and **IntelliJ IDEA**.

Scans your Flutter project's `assets/` directory and generates `Assets.dart` using your chosen naming and generation settings.

## Features

- Legacy / Flat and Hierarchical generation
- `camelCase` and `snake_case` naming
- `String` and `Class` leaf types
- Optional parent-directory inclusion
- Configurable output directory, filename, and class name
- Safe Dart identifiers and collision handling
- Regenerates the complete asset index with **Alt+G**
- Add font declarations to `pubspec.yaml`

## Generation Styles

### Legacy / Flat

Combines directory names and filenames into flat identifiers.

```dart
Assets.imagesLogo
Assets.iconsUserProfile
```

Default leaf type: `String`

### Hierarchical

Represents folders as nested classes.

```dart
Assets.images.logo.path
Assets.icons.userProfile.path
```

Default leaf type: `Class`

## Generate Assets

- **Windows / Linux:** `Alt+G`
- **macOS:** `⌥G`

The shortcut uses the current settings and regenerates the complete asset index.

## Add Fonts to pubspec.yaml

In a Flutter project, select folders or font files, then choose **Flutter Configure Paths → Add Fonts to pubspec.yaml**.

The action recursively scans selected folders for `.ttf`, `.otf`, and `.ttc` files. Hidden folders are skipped.

For `Gilroy-Light.ttf`, it adds:

```yaml
flutter:
  fonts:
    - family: Gilroy-Light
      fonts:
        - asset: assets/fonts/Gilroy-Light.ttf
```

- Family names come from filenames without extensions.
- Existing YAML content, comments, sections, and line endings are preserved.
- Already-declared font paths are skipped.
- New files are added to an existing family when its name matches.
- A notification reports how many fonts were added and skipped.

## Configuration

Open **Settings / Preferences → Tools → Flutter Assets Generator**.

Available settings include generation style, naming style, leaf type, parent-directory inclusion, output directory, filename, and class name.

## Installation

### JetBrains Marketplace

Install **Flutter Assets Generator** from the JetBrains Marketplace.

### Install from ZIP

1. Open **Settings / Preferences → Plugins**.
2. Select **Install Plugin from Disk...**.
3. Choose the plugin ZIP and restart the IDE if requested.

## Requirements

- Android Studio or IntelliJ IDEA
- A Flutter project

## License

Apache License 2.0. See [LICENSE](LICENSE).
