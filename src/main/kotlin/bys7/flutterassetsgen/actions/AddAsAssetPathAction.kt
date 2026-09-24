package bys7.flutterassetsgen.actions

import bys7.flutterassetsgen.core.FlutterProjectDetector
import bys7.flutterassetsgen.core.PubspecAssetsEditor
import bys7.flutterassetsgen.notifications.Notifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * "Flutter Configure Paths -> Add as Asset Path": declares the selected file(s)/folder(s) in
 * `pubspec.yaml`'s `flutter: assets:` section. A folder is added as a directory entry (covers
 * everything under it recursively); a file already covered by a declared parent directory is
 * skipped, since it would be redundant.
 */
class AddAsAssetPathAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isApplicable(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = selectedFiles(e)
        if (files.isEmpty()) return

        val pubspecFile = FlutterProjectDetector.pubspecFile(project) ?: return
        val projectRoot = FlutterProjectDetector.projectRoot(project) ?: return

        var added = 0
        var skipped = 0
        for (vf in files) {
            val relative = relativeAssetPath(projectRoot, File(vf.path)) ?: continue
            if (PubspecAssetsEditor.addPath(pubspecFile, relative)) added++ else skipped++
        }

        refresh(pubspecFile)
        notifyResult(project, added, skipped)
    }

    private fun notifyResult(project: Project, added: Int, skipped: Int) {
        val title = "Flutter Assets Generator"
        when {
            added > 0 && skipped == 0 ->
                Notifier.info(project, title, "Added $added path${plural(added)} to pubspec.yaml. Press Alt+G to regenerate Assets.dart.")
            added > 0 ->
                Notifier.info(project, title, "Added $added path${plural(added)}; $skipped already declared or covered. Press Alt+G to regenerate.")
            else ->
                Notifier.info(project, title, "Nothing to add - already declared, or already covered by a declared parent directory.")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        fun isApplicable(e: AnActionEvent): Boolean {
            val project = e.project ?: return false
            if (!FlutterProjectDetector.isFlutterProject(project)) return false
            return selectedFiles(e).isNotEmpty()
        }

        fun selectedFiles(e: AnActionEvent): List<VirtualFile> =
            e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
                ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { listOf(it) }
                ?: emptyList()

        /**
         * Path of [file] relative to [projectRoot], using forward slashes, with a trailing `/`
         * for directories. Null if [file] isn't actually inside the project.
         */
        fun relativeAssetPath(projectRoot: File, file: File): String? {
            val rootPath = projectRoot.toPath().normalize()
            val filePath = file.toPath().normalize()
            if (filePath == rootPath || !filePath.startsWith(rootPath)) return null
            val relative = rootPath.relativize(filePath).toString().replace(File.separatorChar, '/')
            return if (file.isDirectory) "$relative/" else relative
        }

        fun refresh(file: File) {
            ApplicationManager.getApplication().invokeLater {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            }
        }
    }
}
