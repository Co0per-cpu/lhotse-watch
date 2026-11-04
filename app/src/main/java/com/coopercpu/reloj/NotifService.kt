package com.coopercpu.reloj

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lee las notificaciones del teléfono (con tu permiso) y manda al reloj
 * solo las de las apps que elegiste. No guarda ni envía nada a otro lado.
 */
class NotifService : NotificationListenerService() {

    private var callKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

            // Llamadas entrantes
            if (n.category == Notification.CATEGORY_CALL && sbn.packageName in CALL_PACKAGES) {
                if (!WatchManager.settings.calls) return
                if (callKey == sbn.key) return
                callKey = sbn.key
                WatchManager.sendCall(title.ifBlank { "Llamada" })
                return
            }

            if (sbn.isOngoing) return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val type = WatchManager.settings.typeForPackage(sbn.packageName) ?: return
            if (title.isBlank() && text.isBlank()) return
            WatchManager.sendNotice(type, title, text)
        } catch (_: Exception) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == callKey) {
            callKey = null
            WatchManager.stopCall()
        }
    }
}
