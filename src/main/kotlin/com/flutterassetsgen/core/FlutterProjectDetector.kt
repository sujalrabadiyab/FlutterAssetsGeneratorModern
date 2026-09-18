package bys7.flutterassetsgen.core

import com.intellij.openapi.project.Project
import java.io.File

/**
 * Locates the Flutter project root and its assets directory using only the
 * information the IDE already has (the project base path) - no manual path
 * entry required, per the "don't require me to manually select an asset or
 * folder" requirement.
 */
object FlutterProjectDetector {

    /** True if [project]'s root looks like a Flutter project (has a pubspec.yaml). */
    fun isFlutterProject(project: Project): Boolean {
        val base = project.basePath ?: return false
        return File(base, "pubspec.yaml").isFile
    }

    /** Absolute path to the project root, or null if it can't be determined. */
    fun projectRoot(project: Project): File? = project.basePath?.let(::File)

    /**
     * Resolves the configured assets directory under the project root.
     * Returns null if the project root is unknown or the directory doesn't exist.
     */
    fun findAssetsDir(project: Project, assetsDirRelativePath: String): File? {
        val root = projectRoot(project) ?: return null
        val dir = File(root, assetsDirRelativePath)
        return if (dir.isDirectory) dir else null
    }

    /** Resolves the (possibly not-yet-existing) output file under the project root. */
    fun resolveOutputFile(project: Project, outputRelativePath: String): File? {
        val root = projectRoot(project) ?: return null
        return File(root, outputRelativePath)
    }
}
