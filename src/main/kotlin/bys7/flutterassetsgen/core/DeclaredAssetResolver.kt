package bys7.flutterassetsgen.core

import java.io.File

/**
 * Resolves the raw path strings declared under `flutter: assets:` in `pubspec.yaml` into a flat,
 * deduplicated list of actual asset files. Directory entries are expanded recursively; exactly
 * one [ScannedAsset] is produced even if a file happens to be reachable through more than one
 * declared entry (e.g. an exact file entry that's also covered by a broader directory entry).
 *
 * Every [ScannedAsset.relativePath] here is relative to the *project root*, not to any single
 * "assets" folder, since Flutter allows asset declarations to live anywhere in the project. This
 * is what makes `assets/privacy.html` resolve to the identifier `assetsPrivacy` without needing a
 * separately-configured "assets directory": the declared path *is* the source of truth.
 *
 * Deliberately does not fall back to scanning any physical directory - a path that isn't declared
 * in `pubspec.yaml` is, by design, not an asset as far as generation is concerned.
 */
object DeclaredAssetResolver {

    /** Files/directories we never want to treat as assets even though they can live under a declared directory. */
    private val IGNORED_NAMES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

    /**
     * @param projectRoot the Flutter project root (where `pubspec.yaml` lives)
     * @param declaredPaths raw entries as read from `flutter: assets:` (e.g. "assets/", "assets/privacy.html")
     */
    fun resolve(projectRoot: File, declaredPaths: List<String>): List<ScannedAsset> {
        val byAbsolutePath = LinkedHashMap<String, ScannedAsset>()

        for (declaredRaw in declaredPaths) {
            val declared = declaredRaw.trim()
            if (declared.isEmpty()) continue

            val isDirectoryDeclaration = declared.endsWith("/") || declared.endsWith("\\")
            val cleanPath = declared.trim('/', '\\')
            if (cleanPath.isEmpty()) continue

            val target = File(projectRoot, cleanPath)
            if (isDirectoryDeclaration || target.isDirectory) {
                for (asset in collectFiles(target, projectRoot)) {
                    byAbsolutePath.putIfAbsent(asset.file.absolutePath, asset)
                }
            } else if (target.isFile) {
                val relative = relativeTo(projectRoot, target)
                byAbsolutePath.putIfAbsent(target.absolutePath, ScannedAsset(target, relative))
            }
            // Entries that don't exist on disk (stale declarations) are silently skipped -
            // there's nothing to generate for a path that isn't there.
        }

        return byAbsolutePath.values.sortedBy { it.relativePath }
    }

    private fun collectFiles(dir: File, projectRoot: File): List<ScannedAsset> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .onEnter { d -> !d.name.startsWith(".") }
            .filter { it.isFile }
            .filterNot { it.name.startsWith(".") }
            .filterNot { it.name in IGNORED_NAMES }
            .map { file -> ScannedAsset(file, relativeTo(projectRoot, file)) }
            .toList()
    }

    private fun relativeTo(root: File, file: File): String =
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
}
