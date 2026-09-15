package com.example

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.shortcut.ShortcutNotificationManager
import com.example.shortcut.ShortcutNotificationPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutNotificationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset preferences to clean state
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = false,
            ongoing = true,
            pkg = "",
            appName = "",
            title = "",
            body = "",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_APP
        )
    }

    @Test
    fun `preferences default values are correct`() {
        assertFalse(ShortcutNotificationPreferences.isEnabled(context))
        assertTrue(ShortcutNotificationPreferences.isOngoing(context))
        assertEquals("", ShortcutNotificationPreferences.getTargetPackage(context))
        assertEquals("", ShortcutNotificationPreferences.getTargetAppName(context))
        assertEquals("App Shortcut", ShortcutNotificationPreferences.getTitle(context))
        assertEquals("", ShortcutNotificationPreferences.getBody(context))
        assertEquals(ShortcutNotificationPreferences.ICON_TYPE_APP, ShortcutNotificationPreferences.getIconType(context))
    }

    @Test
    fun `preferences save and retrieve custom configuration`() {
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = true,
            ongoing = false,
            pkg = "com.google.android.youtube",
            appName = "YouTube",
            title = "Open YouTube Now",
            body = "Resume watching video",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_ORBIT
        )

        assertTrue(ShortcutNotificationPreferences.isEnabled(context))
        assertFalse(ShortcutNotificationPreferences.isOngoing(context))
        assertEquals("com.google.android.youtube", ShortcutNotificationPreferences.getTargetPackage(context))
        assertEquals("YouTube", ShortcutNotificationPreferences.getTargetAppName(context))
        assertEquals("Open YouTube Now", ShortcutNotificationPreferences.getTitle(context))
        assertEquals("Resume watching video", ShortcutNotificationPreferences.getBody(context))
        assertEquals(ShortcutNotificationPreferences.ICON_TYPE_ORBIT, ShortcutNotificationPreferences.getIconType(context))
    }

    @Test
    fun `buildNotification creates valid notification with correct metadata and NO configure actions`() {
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = true,
            ongoing = true,
            pkg = "com.android.settings",
            appName = "Settings",
            title = "Quick Settings",
            body = "Configure device",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_MINIMAL
        )

        val notification = ShortcutNotificationManager.buildNotification(context)
        assertNotNull(notification)
        assertNotNull(notification.contentIntent)

        val extras = notification.extras
        assertNotNull(extras)
        assertEquals("Quick Settings", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Configure device", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        // Ensure NO action buttons (e.g. no "Configure" or other buttons) are attached to the notification
        assertTrue(
            "Notification must not have configure or action buttons attached",
            notification.actions == null || notification.actions.isEmpty()
        )

        // Ongoing check
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (notification.flags and Notification.FLAG_NO_CLEAR) != 0
        assertTrue("Notification should have ongoing/no_clear flag set", isOngoing)
    }

    @Test
    fun `buildNotification handles clean notification with only app icon and title`() {
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = true,
            ongoing = false,
            pkg = "com.google.android.youtube",
            appName = "YouTube",
            title = "",
            body = "",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_APP
        )

        val notification = ShortcutNotificationManager.buildNotification(context)
        assertNotNull(notification)
        assertNotNull(notification.contentIntent)

        // Title defaults to target app name ("YouTube")
        assertEquals("YouTube", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        // Body is null or not set when blank
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertTrue(text == null || text.isBlank())

        // No action buttons
        assertTrue(notification.actions == null || notification.actions.isEmpty())
    }

    @Test
    fun `banner image lifecycle saves, loads, and deletes bitmap`() {
        val testBitmap = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
        val saved = ShortcutNotificationPreferences.saveBannerBitmap(context, testBitmap)
        assertTrue("Banner bitmap should save successfully", saved)
        assertTrue("hasBannerImage should return true", ShortcutNotificationPreferences.hasBannerImage(context))

        val loaded = ShortcutNotificationPreferences.loadBannerBitmap(context)
        assertNotNull("Loaded banner bitmap should not be null", loaded)

        val deleted = ShortcutNotificationPreferences.deleteBannerImage(context)
        assertTrue("Banner should be deleted", deleted)
        assertFalse("hasBannerImage should return false after delete", ShortcutNotificationPreferences.hasBannerImage(context))
        assertNull("Loaded bitmap should be null after delete", ShortcutNotificationPreferences.loadBannerBitmap(context))
    }

    @Test
    fun `buildNotification with banner image applies custom media cover RemoteViews`() {
        val testBitmap = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
        ShortcutNotificationPreferences.saveBannerBitmap(context, testBitmap)
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = true,
            ongoing = true,
            pkg = "com.google.android.youtube",
            appName = "YouTube",
            title = "YouTube",
            body = "Tap to open",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_APP
        )

        val notification = ShortcutNotificationManager.buildNotification(context)
        assertNotNull(notification)
        assertNotNull(
            "Custom contentView or bigContentView should be set when banner bitmap exists",
            notification.contentView ?: notification.bigContentView
        )

        // Verify RemoteViews can actually inflate without InflateException or ActionException
        val contentView = notification.contentView
        assertNotNull("contentView must not be null", contentView)
        val inflatedCollapsed = contentView!!.apply(context, null)
        assertNotNull("Collapsed RemoteViews should inflate successfully", inflatedCollapsed)

        val bigContentView = notification.bigContentView
        assertNotNull("bigContentView must not be null", bigContentView)
        val inflatedExpanded = bigContentView!!.apply(context, null)
        assertNotNull("Expanded RemoteViews should inflate successfully", inflatedExpanded)

        // Clean up
        ShortcutNotificationPreferences.deleteBannerImage(context)
    }
}
