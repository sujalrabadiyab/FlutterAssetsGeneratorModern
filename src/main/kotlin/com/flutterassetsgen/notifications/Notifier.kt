package com.flutterassetsgen.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Small helper around the balloon-notification API so the rest of the
 * plugin never has to touch [NotificationGroupManager] directly.
 *
 * The notification group id here MUST match the `<notificationGroup id=.../>`
 * declared in plugin.xml.
 */
object Notifier {

    private const val GROUP_ID = "FlutterAssetsGenerator.Notifications"

    fun info(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    fun warning(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    fun error(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    private fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
