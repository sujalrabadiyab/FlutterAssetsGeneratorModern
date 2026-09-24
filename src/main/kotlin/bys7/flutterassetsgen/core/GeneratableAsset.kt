package bys7.flutterassetsgen.core

/**
 * One resolved asset, ready to hand to [DartAssetsGenerator].
 *
 * [assetPath] and [namingPath] are deliberately kept separate:
 *
 * @param assetPath the real path embedded in the generated Dart string (what
 *   `Image.asset(...)` / `AssetImage(...)` actually needs) - always the full path relative to
 *   the project root, e.g. `"assets/images/logo.png"`.
 * @param namingPath the path used to derive Dart identifiers and (for Hierarchical) nesting -
 *   normally the same as [assetPath] but with the project's conventional assets-root folder
 *   (e.g. `"assets/"`) stripped off the front, via [toGeneratableAssets], so that folder's own
 *   name doesn't leak into the generated API: `Assets.images.logo`, not `Assets.assets.images.logo`.
 */
data class GeneratableAsset(val assetPath: String, val namingPath: String) {
    /** The file's own name (with extension), used to decide String vs AssetGenImage ([resolveFieldType]). */
    val fileName: String get() = assetPath.substringAfterLast('/')
}

/**
 * Builds the [GeneratableAsset] list the generators consume from [scannedAssets] (always
 * project-root-relative, per [DeclaredAssetResolver]): [ScannedAsset.relativePath] becomes
 * [GeneratableAsset.assetPath] verbatim (untouched - it must stay a real, loadable path), while
 * [GeneratableAsset.namingPath] has [assetsRootPrefix] (typically `settings.assetsDir`, e.g.
 * `"assets"`) stripped off the front when a given asset's path actually starts with it.
 *
 * An asset declared outside [assetsRootPrefix] (e.g. a separately-declared `fonts/CustomFont.ttf`
 * that isn't under `assets/` at all) is left with its own [namingPath] unchanged, so its own
 * top-level folder becomes its own group rather than being force-nested under anything.
 */
fun toGeneratableAssets(scannedAssets: List<ScannedAsset>, assetsRootPrefix: String): List<GeneratableAsset> {
    val prefix = assetsRootPrefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
    return scannedAssets.map { asset ->
        val namingPath = if (prefix.isNotEmpty() && asset.relativePath.startsWith(prefix)) {
            asset.relativePath.removePrefix(prefix)
        } else {
            asset.relativePath
        }
        GeneratableAsset(assetPath = asset.relativePath, namingPath = namingPath)
    }
}
