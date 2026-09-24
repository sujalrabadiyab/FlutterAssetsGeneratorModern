package bys7.flutterassetsgen.core

/** Overall shape of the generated `Assets.dart` file. */
enum class GenerationStyle {
    /** One flat constant per asset, e.g. `Assets.imagesLogo` (FlutterAssetsGenerator 2.4.2-style). */
    LEGACY_FLAT,

    /** Nested, typed groups per directory, e.g. `Assets.images.logo` (FlutterAssetsGenerator 3.x-style). */
    HIERARCHICAL
}

/** How each generated asset reference is typed. */
enum class LeafType {
    /** `static const String logo = 'assets/images/logo.png';` */
    STRING,

    /** `static const AssetGenImage logo = AssetGenImage('assets/images/logo.png');` */
    CLASS
}

/**
 * Bundles the user-configurable knobs that control how [DartAssetsGenerator] turns scanned
 * asset files into Dart source, independently of *where* those assets/output live (that's
 * still [bys7.flutterassetsgen.settings.FlutterAssetsGeneratorSettings]'s job).
 */
data class GenerationConfig(
    val generationStyle: GenerationStyle = GenerationStyle.LEGACY_FLAT,
    val namingStyle: NamingStyle = NamingStyle.CAMEL_CASE,
    val leafType: LeafType = LeafType.STRING,
    /**
     * Legacy Flat only: whether the immediate parent folder name is folded into the flat
     * identifier (`imagesHome`) or ignored (`home`). Hierarchical mode always uses folders as
     * nesting levels regardless of this flag.
     */
    val includeParentDirectory: Boolean = true
)
