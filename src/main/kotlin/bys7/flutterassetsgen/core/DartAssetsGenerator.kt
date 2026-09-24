package bys7.flutterassetsgen.core

/**
 * Result of generating a complete `Assets.dart`, regardless of which [GenerationStyle] produced it.
 *
 * @param assetCount number of individual asset files represented in [dartSource]
 * @param renamedDuplicates human-readable descriptions of any naming collisions that had to be
 *   auto-resolved (e.g. "images/home.png -> imagesHome2 (name collision with another asset)")
 * @param dartSource the complete, ready-to-write `Assets.dart` source text
 */
data class GenerationOutput(
    val assetCount: Int,
    val renamedDuplicates: List<String>,
    val dartSource: String
)

/**
 * Entry point for turning resolved asset files into a complete `Assets.dart` source file.
 *
 * This object owns nothing but the dispatch: the actual naming/formatting work for each
 * [GenerationStyle] lives in its own generator ([LegacyAssetGenerator] /
 * [HierarchicalAssetGenerator]) so the two output styles can be read, tested, and evolved
 * independently, while [AssetNameConverter] (identifier rules) stays shared between them.
 */
object DartAssetsGenerator {

    fun generate(
        assets: List<GeneratableAsset>,
        className: String,
        config: GenerationConfig
    ): GenerationOutput = when (config.generationStyle) {
        GenerationStyle.LEGACY_FLAT ->
            LegacyAssetGenerator.generate(assets, className, config)

        GenerationStyle.HIERARCHICAL ->
            HierarchicalAssetGenerator.generate(assets, className, config)
    }
}
