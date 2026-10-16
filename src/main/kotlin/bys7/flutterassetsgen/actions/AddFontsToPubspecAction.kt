package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.FlutterProjectDetector
import bys7.flutterassetsgen.core.PubspecFontsEditor
import bys7.flutterassetsgen.notifications.Notifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File

/**
 * "Flutter Configure Paths -> Add Fonts": declares every font file
 * (.ttf / .otf / .ttc) found in the selected folder(s) - including subfolders - under
 * `pubspec.yaml`'s `flutter: fonts:` section, one family per file, named after the file.
 * Selected font files are added directly.
 */
class AddFontsToPubspecAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selected = AddAsAssetPathAction.selectedFiles(e)
        e.presentation.isEnabledAndVisible =
            project != null &&
                FlutterProjectDetector.isFlutterProject(project) &&
                selected.isNotEmpty() &&
                selected.all { it.isDirectory || PubspecFontsEditor.isFontFile(File(it.path)) }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selected = AddAsAssetPathAction.selectedFiles(e)
        if (selected.isEmpty()) return

        val pubspecFile = FlutterProjectDetector.pubspecFile(project) ?: return
        val projectRoot = FlutterProjectDetector.projectRoot(project) ?: return
        val title = "Flutter Assets Generator"

        val fontFiles = selected
            .flatMap { collectFontFiles(File(it.path)) }
            .distinctBy { it.path }
            .sortedBy { it.path }

        if (fontFiles.isEmpty()) {
            Notifier.info(project, title, "No font files (.ttf, .otf, .ttc) found in the selection.")
            return
        }

        val entries = fontFiles.mapNotNull { file ->
            AddAsAssetPathAction.relativeAssetPath(projectRoot, file)?.let(PubspecFontsEditor::entryForAsset)
        }

        val result = PubspecFontsEditor.addFonts(pubspecFile, entries)
        AddAsAssetPathAction.refresh(pubspecFile)

        val message = when {
            result.added > 0 && result.skipped == 0 ->
                "Added ${result.added} font${plural(result.added)} to pubspec.yaml."
            result.added > 0 ->
                "Added ${result.added} font${plural(result.added)}; ${result.skipped} already declared."
            else ->
                "Nothing to add - every font is already declared."
        }
        Notifier.info(project, title, message)
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** [root] itself if it's a font file, else every font file under it (recursive, skipping hidden folders). */
    private fun collectFontFiles(root: File): List<File> {
        if (root.isFile) return if (PubspecFontsEditor.isFontFile(root)) listOf(root) else emptyList()
        val result = mutableListOf<File>()
        fun walk(dir: File) {
            dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
                when {
                    child.name.startsWith(".") -> Unit
                    child.isDirectory -> walk(child)
                    PubspecFontsEditor.isFontFile(child) -> result += child
                }
            }
        }
        walk(root)
        return result
    }
}
