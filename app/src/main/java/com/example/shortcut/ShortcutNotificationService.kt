package com.example.shortcut

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ongoing Foreground Service hosting custom shortcut notifications.
 *
 * Keeps notifications persistent and active when configured,
 * ensuring high-priority OS lifecycle protection.
 */
class ShortcutNotificationService : Service() {

    companion object {
        private const val TAG = "ShortcutNotifService"
        val isServiceRunning = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.value = true
        ShortcutNotificationManager.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val allShortcuts = ShortcutNotificationPreferences.getAllShortcuts(this)
        val enabledShortcuts = allShortcuts.filter { it.isEnabled && it.packageName.isNotBlank() }

        if (enabledShortcuts.isEmpty() || intent?.action == ShortcutNotificationManager.ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // The first enabled shortcut acts as the primary foreground service notification
        val primaryShortcut = enabledShortcuts.first()
        val primaryNotification = ShortcutNotificationManager.buildNotification(this, primaryShortcut)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    primaryShortcut.notificationId,
                    primaryNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(primaryShortcut.notificationId, primaryNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground for ShortcutNotificationService", e)
        }

        // Post all remaining enabled shortcuts as concurrent persistent notifications
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            for (shortcut in enabledShortcuts.drop(1)) {
                try {
                    val notif = ShortcutNotificationManager.buildNotification(this, shortcut)
                    notificationManager.notify(shortcut.notificationId, notif)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify additional shortcut ${shortcut.id}", e)
                }
            }

            // Cancel any muted/disabled shortcuts
            for (shortcut in allShortcuts.filter { !it.isEnabled || it.packageName.isBlank() }) {
                try {
                    notificationManager.cancel(shortcut.notificationId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cancel muted shortcut ${shortcut.id}", e)
                }
            }
        }

        return START_STICKY
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
