package dev.veil.app.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.veil.app.VeilApplication

class VeilNotificationListener : NotificationListenerService() {
    private val controller get() = (application as VeilApplication).graph.platform
    override fun onListenerConnected() { super.onListenerConnected(); controller.connected(this) }
    override fun onListenerDisconnected() {
        controller.disconnected(this)
        super.onListenerDisconnected()
        // One supported rebind request, not a polling loop or a promise of uninterrupted capture.
        controller.requestReconnect()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sbn?.let(controller::observed) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { sbn?.let(controller::removed) }
    override fun onDestroy() { controller.disconnected(this); super.onDestroy() }
}
