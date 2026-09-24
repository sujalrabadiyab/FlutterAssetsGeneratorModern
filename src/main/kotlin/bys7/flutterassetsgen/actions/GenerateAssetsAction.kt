package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.DartAssetsGenerator
import bys7.flutterassetsgen.core.DeclaredAssetResolver
import bys7.flutterassetsgen.core.FlutterProjectDetector
import bys7.flutterassetsgen.core.GenerationOutput
import bys7.flutterassetsgen.core.GenerationStyle
import bys7.flutterassetsgen.core.PubspecAssetsEditor
import bys7.flutterassetsgen.core.toGeneratableAssets
import bys7.flutterassetsgen.notifications.Notifier
import bys7.flutterassetsgen.settings.FlutterAssetsGeneratorSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.io.IOException

/**
 * "Generate Flutter Assets" action, bound to Alt+G by default.
 *
 * Assets are resolved from `pubspec.yaml`'s `flutter: assets:` declarations (via
 * [DeclaredAssetResolver]), not by blindly scanning a physical directory - use
 * "Flutter Configure Paths" (right-click a file/folder in the Project view) to declare what
 * should be included first.
 *
 * By default this uses whichever [GenerationStyle] is currently selected in Settings.
 *
 * Deliberately kept as a plain no-arg constructor with an overridable property, rather than a
 * constructor parameter: IntelliJ Platform instantiates action classes declared in `plugin.xml`
 * via reflection expecting a genuine zero-arg constructor, which a Kotlin constructor parameter
 * with a default value does not reliably provide (that needs `@JvmOverloads`, and even then it's
 * a fragile pattern for reflection-instantiated classes). A property override, resolved through
 * ordinary virtual dispatch, doesn't have that failure mode.
 */
open class GenerateAssetsAction : AnAction() {

    protected open val forcedStyle: GenerationStyle? = null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Flutter Assets", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                generate(project)
            }
        })
    }

    private fun generate(project: Project) {
        val title = "Flutter Assets Generator"

        if (!FlutterProjectDetector.isFlutterProject(project)) {
            Notifier.warning(
                project,
                title,
                "No pubspec.yaml found at the project root - this doesn't look like a Flutter project."
            )
            return
        }

        val projectRoot = FlutterProjectDetector.projectRoot(project)
        val pubspecFile = FlutterProjectDetector.pubspecFile(project)
        if (projectRoot == null || pubspecFile == null || !pubspecFile.isFile) {
            Notifier.error(project, title, "Could not resolve pubspec.yaml.")
            return
        }

        val settings = FlutterAssetsGeneratorSettings.getInstance(project)

        val declaredPaths = PubspecAssetsEditor.readDeclaredPaths(pubspecFile)
        if (declaredPaths.isEmpty()) {
            Notifier.warning(
                project,
                title,
                "No assets declared in pubspec.yaml's flutter: assets: section. Right-click a " +
                        "file or folder in the Project view and choose Flutter Configure Paths -> " +
                        "Add as Asset Path, then try again."
            )
            return
        }

        val outputFile = FlutterProjectDetector.resolveOutputFile(project, settings.outputFile)
        if (outputFile == null) {
            Notifier.error(project, title, "Could not resolve the project root to write '${settings.outputFile}'.")
            return
        }

        try {
            val resolvedAssets = DeclaredAssetResolver.resolve(projectRoot, declaredPaths)
                // Never let the file we're about to write become one of its own inputs.
                .filterNot { it.file.absoluteFile == outputFile.absoluteFile }

            // assetPath (the printed Dart string) always keeps the real, full project-relative
            // path; namingPath has settings.assetsDir (e.g. "assets") stripped off the front, so
            // that conventional folder name doesn't leak into every generated identifier.
            val generatableAssets = toGeneratableAssets(resolvedAssets, settings.assetsDir)

            val config = settings.generationConfig().let { base ->
                if (forcedStyle != null) base.copy(generationStyle = forcedStyle!!) else base
            }
            val result: GenerationOutput = DartAssetsGenerator.generate(generatableAssets, settings.className, config)

            val wasWritten = writeAndRefresh(outputFile, result.dartSource)

            notifySuccess(project, title, settings.outputFile, result, wasWritten)
        } catch (ex: IOException) {
            Notifier.error(project, title, "Failed to generate '${settings.outputFile}': ${ex.message}")
        } catch (ex: SecurityException) {
            Notifier.error(project, title, "Permission denied while writing '${settings.outputFile}': ${ex.message}")
        } catch (ex: Exception) {
            Notifier.error(project, title, "Unexpected error while generating assets: ${ex.message}")
        }
    }

    private fun notifySuccess(
        project: Project,
        title: String,
        outputPath: String,
        result: GenerationOutput,
        wasWritten: Boolean
    ) {
        val message = buildString {
            if (!wasWritten) {
                append("$outputPath is already up to date - no changes")
            } else if (result.assetCount == 0) {
                append("No assets found - wrote an empty $outputPath")
            } else {
                append("${result.assetCount} asset")
                if (result.assetCount != 1) append("s")
                append(" written to $outputPath")
            }
            if (result.renamedDuplicates.isNotEmpty()) {
                append(". Resolved ${result.renamedDuplicates.size} name collision")
                if (result.renamedDuplicates.size != 1) append("s")
                append(" - see below.")
            }
        }
        if (result.renamedDuplicates.isEmpty()) {
            Notifier.info(project, title, message)
        } else {
            val details = result.renamedDuplicates.joinToString("<br/>")
            Notifier.warning(project, title, "$message<br/>$details")
        }
    }

    /**
     * Writes [content] to target [file] on disk and refreshes the VFS view -
     * but only if [content] actually differs from what's already there.
     *
     * Skipping no-op writes avoids needless mtime/VFS churn (which in turn
     * avoids triggering file watchers, analyzers, and hot-reloads for a file
     * that didn't really change).
     *
     * @return true if the file was written, false if it was already up to date.
     */
    private fun writeAndRefresh(file: File?, content: String): Boolean {
        if (file == null) return false

        if (file.exists() && file.isFile && readTextOrNull(file) == content) {
            return false
        }

        file.parentFile?.mkdirs()
        file.writeText(content)

        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        }
        return true
    }

    /** Reads [file] as text, returning null instead of throwing if it can't be read. */
    private fun readTextOrNull(file: File): String? =
        try {
            file.readText()
        } catch (ex: IOException) {
            null
        } catch (ex: SecurityException) {
            null
        }
}