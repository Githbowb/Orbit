package com.example.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Automatically restores the persistent shortcut notification after a device reboot
 * if the feature is enabled in user settings.
 */
class ShortcutBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (ShortcutNotificationPreferences.isEnabled(context)) {
                ShortcutNotificationManager.syncServiceState(context)
            }
        }
    }
}
