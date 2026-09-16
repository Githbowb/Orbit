package com.example

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.shortcut.ShortcutItem
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
        // Reset preferences to clean slate
        val prefs = context.getSharedPreferences("orbit_shortcut_notification_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `preferences default values are clean and empty`() {
        val shortcuts = ShortcutNotificationPreferences.getAllShortcuts(context)
        assertTrue("Initial shortcut list should be clean and empty", shortcuts.isEmpty())
        assertFalse(ShortcutNotificationPreferences.isEnabled(context))
        assertTrue(ShortcutNotificationPreferences.isOngoing(context))
    }

    @Test
    fun `preferences save and retrieve custom configuration`() {
        val item = ShortcutItem(
            id = "test_item_1",
            notificationId = 2005,
            isEnabled = true,
            isOngoing = false,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            title = "Open YouTube Now",
            body = "Resume watching video",
            iconType = ShortcutNotificationPreferences.ICON_TYPE_ORBIT
        )
        ShortcutNotificationPreferences.saveShortcut(context, item)

        val retrieved = ShortcutNotificationPreferences.getShortcutById(context, "test_item_1")
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isEnabled)
        assertFalse(retrieved.isOngoing)
        assertEquals("com.google.android.youtube", retrieved.packageName)
        assertEquals("YouTube", retrieved.appName)
        assertEquals("Open YouTube Now", retrieved.title)
        assertEquals("Resume watching video", retrieved.body)
        assertEquals(ShortcutNotificationPreferences.ICON_TYPE_ORBIT, retrieved.iconType)
    }

    @Test
    fun `buildNotification creates system-compliant notification with action buttons and grouping`() {
        val shortcut = ShortcutNotificationPreferences.createNewShortcut(context, "com.android.settings", "Settings")
        val customShortcut = shortcut.copy(
            title = "Open Settings",
            body = "Configure device options",
            isOngoing = true
        )
        ShortcutNotificationPreferences.saveShortcut(context, customShortcut)

        val notification = ShortcutNotificationManager.buildNotification(context, customShortcut)
        assertNotNull(notification)
        assertNotNull("Notification must have contentIntent attached", notification.contentIntent)
        assertNotNull("Notification must have deleteIntent attached", notification.deleteIntent)

        val extras = notification.extras
        assertNotNull(extras)
        assertEquals("Open Settings", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Configure device options", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        // Small Icon must be Orbit monochrome resource
        assertEquals(R.drawable.ic_orbit_small_monochrome, notification.smallIcon.resId)

        // Action Buttons: Up to 2 actions ("Open" and "Remove")
        assertNotNull("Notification must have action buttons", notification.actions)
        assertEquals("Notification should provide 2 action buttons", 2, notification.actions.size)
        assertEquals("Open", notification.actions[0].title.toString())
        assertEquals("Remove", notification.actions[1].title.toString())

        // Group Key
        assertEquals(ShortcutNotificationManager.GROUP_KEY_SHORTCUTS, notification.group)

        // Color & Colorized flag
        assertNotEquals(0, notification.color)
        assertTrue("Colorized flag must be active", extras.getBoolean(Notification.EXTRA_COLORIZED, false))

        // Ongoing check
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (notification.flags and Notification.FLAG_NO_CLEAR) != 0
        assertTrue("Notification should have ongoing flag set", isOngoing)
    }

    @Test
    fun `buildNotification defaults title to Open AppName when custom title is blank`() {
        val shortcut = ShortcutNotificationPreferences.createNewShortcut(context, "com.google.android.youtube", "YouTube")
        val blankTitleShortcut = shortcut.copy(title = "", body = "")

        val notification = ShortcutNotificationManager.buildNotification(context, blankTitleShortcut)
        assertNotNull(notification)

        // Title defaults to "Open YouTube"
        assertEquals("Open YouTube", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        // Body defaults to "Tap to launch"
        assertEquals("Tap to launch", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    @Test
    fun `palette color extraction extracts vibrant color or falls back to Orbit brand color`() {
        // Create solid color bitmap (Blue)
        val blueBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        blueBitmap.eraseColor(Color.BLUE)

        val extractedBlue = ShortcutNotificationManager.extractAccentColorSync(blueBitmap, cacheKey = "test_blue")
        assertNotEquals(ShortcutNotificationManager.DEFAULT_ORBIT_COLOR, extractedBlue)

        // Null bitmap falls back gracefully to Orbit brand color
        val fallbackColor = ShortcutNotificationManager.extractAccentColorSync(null)
        assertEquals(ShortcutNotificationManager.DEFAULT_ORBIT_COLOR, fallbackColor)
    }

    @Test
    fun `multiple shortcuts create group summary notification`() {
        val summaryNotification = ShortcutNotificationManager.buildSummaryNotification(context, activeCount = 3)
        assertNotNull(summaryNotification)
        assertEquals(ShortcutNotificationManager.GROUP_KEY_SHORTCUTS, summaryNotification.group)
        assertTrue(
            "Summary notification must have FLAG_GROUP_SUMMARY",
            (summaryNotification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        )
        assertEquals(R.drawable.ic_orbit_small_monochrome, summaryNotification.smallIcon.resId)
        assertEquals("Orbit Shortcuts", summaryNotification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
    }

    @Test
    fun `multiple shortcuts can be individually configured, enabled, and deleted`() {
        val shortcut1 = ShortcutNotificationPreferences.createNewShortcut(context, "com.spotify.music", "Spotify")
        val shortcut2 = ShortcutNotificationPreferences.createNewShortcut(context, "com.netflix.mediaclient", "Netflix")

        ShortcutNotificationPreferences.saveShortcut(context, shortcut1)
        ShortcutNotificationPreferences.saveShortcut(context, shortcut2)

        val all = ShortcutNotificationPreferences.getAllShortcuts(context)
        assertEquals(2, all.size)

        // Toggle enabled status
        ShortcutNotificationPreferences.setShortcutEnabled(context, shortcut1.id, false)
        val updated1 = ShortcutNotificationPreferences.getShortcutById(context, shortcut1.id)
        assertNotNull(updated1)
        assertFalse(updated1!!.isEnabled)

        val updated2 = ShortcutNotificationPreferences.getShortcutById(context, shortcut2.id)
        assertNotNull(updated2)
        assertTrue(updated2!!.isEnabled)

        // Delete shortcut
        val deleted = ShortcutNotificationPreferences.deleteShortcut(context, shortcut1.id)
        assertTrue(deleted)
        assertNull(ShortcutNotificationPreferences.getShortcutById(context, shortcut1.id))
        assertEquals(1, ShortcutNotificationPreferences.getAllShortcuts(context).size)
    }

    @Test
    fun `empty shortcuts without package are purged and rejected from persistence`() {
        val ghost = ShortcutNotificationPreferences.createNewShortcut(context, "", "")
        ShortcutNotificationPreferences.saveShortcut(context, ghost)
        assertNull("Ghost shortcut without package should not be saved", ShortcutNotificationPreferences.getShortcutById(context, ghost.id))

        val valid = ShortcutNotificationPreferences.createNewShortcut(context, "com.android.chrome", "Chrome")
        ShortcutNotificationPreferences.saveShortcut(context, valid)
        assertNotNull(ShortcutNotificationPreferences.getShortcutById(context, valid.id))
    }
}
