package com.example.shortcut

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Controller for building, posting, and synchronizing custom persistent shortcut notifications.
 */
object ShortcutNotificationManager {
    private const val TAG = "ShortcutNotifMgr"
    const val CHANNEL_ID = "orbit_shortcut_notification_channel"
    const val NOTIFICATION_ID = 2002

    const val ACTION_START_OR_UPDATE = "com.example.shortcut.ACTION_START_OR_UPDATE"
    const val ACTION_STOP = "com.example.shortcut.ACTION_STOP"
    const val EXTRA_OPEN_SHORTCUT_CONFIG = "extra_open_shortcut_config"
    const val EXTRA_SHORTCUT_APP_NOT_FOUND = "extra_shortcut_app_not_found"

    fun init(context: Context) {
        createNotificationChannel(context)
        if (ShortcutNotificationPreferences.isEnabled(context)) {
            syncServiceState(context)
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Shortcut Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notifications to directly launch your configured favorite applications."
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the shortcut notification for a specific ShortcutItem.
     */
    fun buildNotification(context: Context, item: ShortcutItem): Notification {
        createNotificationChannel(context)

        val targetPackage = item.packageName
        val title = item.displayTitle()
        val body = item.body
        val iconType = item.iconType
        val isOngoing = item.isOngoing

        // Launch Intent specific to this shortcut
        val pm = context.packageManager
        val launchIntent = if (targetPackage.isNotBlank()) {
            try {
                pm.getLaunchIntentForPackage(targetPackage)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting launch intent for $targetPackage", e)
                null
            }
        } else null

        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                item.notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // Fallback: Opens Orbit with a helpful warning if app is disabled or uninstalled
            val fallbackIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHORTCUT_APP_NOT_FOUND, targetPackage)
            }
            PendingIntent.getActivity(
                context,
                item.notificationId,
                fallbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val largeIconBitmap = resolveLargeIcon(context, iconType, targetPackage)
        val bannerBitmap = ShortcutNotificationPreferences.loadBannerBitmap(context, item)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_bubble_atom_core)
            .setContentIntent(pendingIntent)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (body.isNotBlank()) {
            builder.setContentText(body)
        }

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        if (bannerBitmap != null) {
            try {
                // Collapsed View - full card background cover matching Live Preview
                val collapsedViews = RemoteViews(context.packageName, R.layout.notification_shortcut_collapsed)
                collapsedViews.setImageViewBitmap(R.id.notif_bg_image, bannerBitmap)
                if (largeIconBitmap != null) {
                    collapsedViews.setImageViewBitmap(R.id.notif_app_icon, largeIconBitmap)
                }
                collapsedViews.setTextViewText(R.id.notif_title, title)
                if (body.isNotBlank()) {
                    collapsedViews.setViewVisibility(R.id.notif_body, View.VISIBLE)
                    collapsedViews.setTextViewText(R.id.notif_body, body)
                } else {
                    collapsedViews.setViewVisibility(R.id.notif_body, View.GONE)
                }

                // Expanded View - expanded media cover matching Live Preview
                val expandedViews = RemoteViews(context.packageName, R.layout.notification_shortcut_expanded)
                expandedViews.setImageViewBitmap(R.id.notif_bg_image, bannerBitmap)
                if (largeIconBitmap != null) {
                    expandedViews.setImageViewBitmap(R.id.notif_app_icon, largeIconBitmap)
                }
                expandedViews.setTextViewText(R.id.notif_title, title)
                if (body.isNotBlank()) {
                    expandedViews.setViewVisibility(R.id.notif_body, View.VISIBLE)
                    expandedViews.setTextViewText(R.id.notif_body, body)
                } else {
                    expandedViews.setViewVisibility(R.id.notif_body, View.GONE)
                }

                builder.setCustomContentView(collapsedViews)
                builder.setCustomBigContentView(expandedViews)
                // Note: Omit DecoratedCustomViewStyle to give RemoteViews full-bleed edge-to-edge card canvas
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply custom RemoteViews with background image", e)
                val bigPictureStyle = NotificationCompat.BigPictureStyle()
                    .bigPicture(bannerBitmap)
                if (body.isNotBlank()) {
                    bigPictureStyle.setSummaryText(body)
                }
                builder.setStyle(bigPictureStyle)
            }
        }

        val notification = builder.build()
        if (isOngoing) {
            notification.flags = notification.flags or
                NotificationCompat.FLAG_NO_CLEAR or
                NotificationCompat.FLAG_ONGOING_EVENT
        }

        return notification
    }

    /**
     * Builds the primary shortcut notification (compatibility wrapper).
     */
    fun buildNotification(context: Context): Notification {
        val primary = ShortcutNotificationPreferences.getAllShortcuts(context).firstOrNull()
            ?: ShortcutItem(id = ShortcutNotificationPreferences.DEFAULT_SHORTCUT_ID, notificationId = NOTIFICATION_ID)
        return buildNotification(context, primary)
    }

    /**
     * Resolves the large icon bitmap based on the user's icon selection.
     */
    private fun resolveLargeIcon(context: Context, iconType: String, targetPackage: String): Bitmap? {
        return try {
            when (iconType) {
                ShortcutNotificationPreferences.ICON_TYPE_APP -> {
                    if (targetPackage.isNotBlank()) {
                        val drawable = context.packageManager.getApplicationIcon(targetPackage)
                        drawableToBitmap(drawable, 128)
                    } else {
                        getOrbitIconBitmap(context)
                    }
                }
                ShortcutNotificationPreferences.ICON_TYPE_ORBIT -> {
                    getOrbitIconBitmap(context)
                }
                ShortcutNotificationPreferences.ICON_TYPE_MINIMAL -> {
                    getMinimalGlyphBitmap(context)
                }
                else -> getOrbitIconBitmap(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve large icon for $iconType", e)
            getOrbitIconBitmap(context)
        }
    }

    private fun getOrbitIconBitmap(context: Context): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
            drawable?.let { drawableToBitmap(it, 128) }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMinimalGlyphBitmap(context: Context): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_bubble_atom_core)
            drawable?.let { drawableToBitmap(it, 128, tintColor = 0xFFFF6B35.toInt()) }
        } catch (e: Exception) {
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable, size: Int = 128, tintColor: Int? = null): Bitmap {
        if (drawable is BitmapDrawable && tintColor == null) {
            val original = drawable.bitmap
            if (original != null && !original.isRecycled) {
                return Bitmap.createScaledBitmap(original, size, size, true)
            }
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else size
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else size

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        if (tintColor != null) {
            drawable.setTint(tintColor)
        }
        drawable.draw(canvas)

        return if (width != size || height != size) {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        } else {
            bitmap
        }
    }

    /**
     * Synchronizes all active shortcut notifications with the system notification shade.
     */
    fun syncServiceState(context: Context) {
        val allShortcuts = ShortcutNotificationPreferences.getAllShortcuts(context)
        val enabledShortcuts = allShortcuts.filter { it.isEnabled && it.packageName.isNotBlank() }
        val serviceIntent = Intent(context, ShortcutNotificationService::class.java)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (enabledShortcuts.isNotEmpty()) {
            serviceIntent.action = ACTION_START_OR_UPDATE
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ShortcutNotificationService", e)
            }

            // Immediately post or update each enabled notification
            for (shortcut in enabledShortcuts) {
                try {
                    val notif = buildNotification(context, shortcut)
                    notificationManager?.notify(shortcut.notificationId, notif)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct notify failed for ${shortcut.id}: ${e.message}")
                }
            }

            // Cancel any muted or incomplete shortcuts
            for (shortcut in allShortcuts.filter { !it.isEnabled || it.packageName.isBlank() }) {
                try {
                    notificationManager?.cancel(shortcut.notificationId)
                } catch (e: Exception) {
                    Log.w(TAG, "Cancel notification failed for ${shortcut.id}: ${e.message}")
                }
            }
        } else {
            serviceIntent.action = ACTION_STOP
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ShortcutNotificationService", e)
            }

            // Cancel all notifications
            for (shortcut in allShortcuts) {
                try {
                    notificationManager?.cancel(shortcut.notificationId)
                } catch (e: Exception) {
                    Log.w(TAG, "Cancel notification failed: ${e.message}")
                }
            }
            try {
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
