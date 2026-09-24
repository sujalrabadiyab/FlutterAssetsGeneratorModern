package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.FlutterProjectDetector
import bys7.flutterassetsgen.core.PubspecAssetsEditor
import bys7.flutterassetsgen.notifications.Notifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File

/**
 * "Flutter Configure Paths -> Add Folder and Subfolders": declares the selected folder *and*
 * every subfolder underneath it as individual directory entries in `pubspec.yaml` (folder paths
 * only - never individual files), skipping any that are already declared.
 *
 * Unlike [AddAsAssetPathAction] on a single folder, this intentionally still adds e.g.
 * `assets/home/` even when `assets/` is already declared - the point of this action is a
 * complete, explicit per-folder listing.
 */
class AddFolderAndSubfoldersAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selected = AddAsAssetPathAction.selectedFiles(e)
        e.presentation.isEnabledAndVisible =
            project != null &&
                FlutterProjectDetector.isFlutterProject(project) &&
                selected.isNotEmpty() &&
                selected.all { it.isDirectory }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val folders = AddAsAssetPathAction.selectedFiles(e).filter { it.isDirectory }
        if (folders.isEmpty()) return

        val pubspecFile = FlutterProjectDetector.pubspecFile(project) ?: return
        val projectRoot = FlutterProjectDetector.projectRoot(project) ?: return

        var added = 0
        for (vf in folders) {
            for (dir in collectDirectories(File(vf.path))) {
                val relative = AddAsAssetPathAction.relativeAssetPath(projectRoot, dir) ?: continue
                if (PubspecAssetsEditor.addPath(pubspecFile, relative)) added++
            }
        }

        AddAsAssetPathAction.refresh(pubspecFile)

        val title = "Flutter Assets Generator"
        if (added > 0) {
            Notifier.info(project, title, "Added $added folder path${if (added == 1) "" else "s"} to pubspec.yaml. Press Alt+G to regenerate Assets.dart.")
        } else {
            Notifier.info(project, title, "Nothing to add - every folder is already declared.")
        }
    }

    /** [root] itself plus every non-hidden subdirectory, recursively, in a stable (depth-first, name-sorted) order. */
    private fun collectDirectories(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        fun walk(dir: File) {
            result += dir
            dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?.forEach { walk(it) }
        }
        walk(root)
        return result
    }
}
