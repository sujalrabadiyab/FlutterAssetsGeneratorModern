package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.FlutterProjectDetector
import bys7.flutterassetsgen.core.PubspecAssetsEditor
import bys7.flutterassetsgen.notifications.Notifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File

/**
 * "Flutter Configure Paths -> Remove from Asset Paths": removes the selected file's/folder's
 * *exact* declaration from `pubspec.yaml`'s `flutter: assets:` section, if present. Does not
 * attempt to guess intent when the selection is only indirectly covered by a broader declared
 * directory - only a verbatim match is removed.
 */
class RemoveFromAssetPathsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = AddAsAssetPathAction.isApplicable(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = AddAsAssetPathAction.selectedFiles(e)
        if (files.isEmpty()) return

        val pubspecFile = FlutterProjectDetector.pubspecFile(project) ?: return
        val projectRoot = FlutterProjectDetector.projectRoot(project) ?: return

        var removed = 0
        for (vf in files) {
            val relative = AddAsAssetPathAction.relativeAssetPath(projectRoot, File(vf.path)) ?: continue
            if (PubspecAssetsEditor.removePath(pubspecFile, relative)) removed++
        }

        AddAsAssetPathAction.refresh(pubspecFile)

        val title = "Flutter Assets Generator"
        if (removed > 0) {
            Notifier.info(project, title, "Removed $removed path${if (removed == 1) "" else "s"} from pubspec.yaml. Press Alt+G to regenerate Assets.dart.")
        } else {
            Notifier.info(project, title, "Nothing to remove - not directly declared (it may only be covered by a parent directory entry).")
        }
    }
}
