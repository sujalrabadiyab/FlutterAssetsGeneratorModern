package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.AssetScanner
import bys7.flutterassetsgen.core.BuildResult
import bys7.flutterassetsgen.core.DartAssetsGenerator
import bys7.flutterassetsgen.core.FlutterProjectDetector
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
 */
class GenerateAssetsAction : AnAction() {

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

        val settings = FlutterAssetsGeneratorSettings.getInstance(project)

        val assetsDir = FlutterProjectDetector.findAssetsDir(project, settings.assetsDir)
        if (assetsDir == null) {
            Notifier.warning(
                project,
                title,
                "Assets directory '${settings.assetsDir}' was not found. " +
                        "Create it (or update the path in Settings | Tools | Flutter Assets Generator) and try again."
            )
            return
        }

        val outputFile = FlutterProjectDetector.resolveOutputFile(project, settings.outputFile)
        if (outputFile == null) {
            Notifier.error(project, title, "Could not resolve the project root to write '${settings.outputFile}'.")
            return
        }

        try {
            val scanned = AssetScanner.scan(assetsDir)

            val assetsDirPrefix = settings.assetsDir.trim('/')
            val buildResult: BuildResult = DartAssetsGenerator.buildEntries(scanned, assetsDirPrefix)
            val dartSource = DartAssetsGenerator.renderDartFile(settings.className, buildResult.entries)

            val wasWritten = writeAndRefresh(outputFile, dartSource)

            notifySuccess(project, title, settings.outputFile, buildResult, wasWritten)
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
        buildResult: BuildResult,
        wasWritten: Boolean
    ) {
        val message = buildString {
            if (!wasWritten) {
                append("$outputPath is already up to date - no changes")
            } else if (buildResult.entries.isEmpty()) {
                append("No assets found - wrote an empty $outputPath")
            } else {
                append("${buildResult.entries.size} asset")
                if (buildResult.entries.size != 1) append("s")
                append(" written to $outputPath")
            }
            if (buildResult.renamedDuplicates.isNotEmpty()) {
                append(". Resolved ${buildResult.renamedDuplicates.size} name collision")
                if (buildResult.renamedDuplicates.size != 1) append("s")
                append(" - see below.")
            }
        }
        if (buildResult.renamedDuplicates.isEmpty()) {
            Notifier.info(project, title, message)
        } else {
            val details = buildResult.renamedDuplicates.joinToString("<br/>")
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