package bys7.flutterassetsgen.core

import java.io.File

/**
 * Recursively scans an assets directory. Deliberately extension-agnostic:
 * every regular, non-hidden file under the directory is a candidate asset,
 * since Flutter can bundle arbitrary file types (images, audio, video,
 * fonts, JSON, text, ...). No hardcoded extension whitelist.
 */
object AssetScanner {

    /** Files/directories we never want to treat as assets even though they can live under assets/. */
    private val IGNORED_NAMES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

    /**
     * Scans [assetsRoot] recursively and returns every asset file found, each
     * paired with its path relative to [assetsRoot] using forward slashes
     * (e.g. "images/profile/user.png"), sorted for deterministic output.
     */
    fun scan(assetsRoot: File): List<ScannedAsset> {
        if (!assetsRoot.isDirectory) return emptyList()

        val rootPath = assetsRoot.toPath()
        return assetsRoot.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") }
            .filter { it.isFile }
            .filterNot { it.name.startsWith(".") }
            .filterNot { it.name in IGNORED_NAMES }
            .mapNotNull { file ->
                runCatching {
                    val relative = rootPath.relativize(file.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                    ScannedAsset(file = file, relativePath = relative)
                }.getOrNull()
            }
            .sortedBy { it.relativePath }
            .toList()
    }
}

/**
 * @param file the asset's absolute location on disk
 * @param relativePath the asset's path relative to the assets root, e.g. "images/logo.png"
 */
data class ScannedAsset(val file: File, val relativePath: String)
