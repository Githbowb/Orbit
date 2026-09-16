package com.example.shortcut

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages user preferences for multiple custom persistent shortcut notifications.
 */
object ShortcutNotificationPreferences {
    private const val PREFS_NAME = "orbit_shortcut_notification_prefs"
    private const val TAG = "ShortcutNotificationPrefs"

    const val KEY_SHORTCUTS_LIST = "shortcut_notifications_list_json"
    const val KEY_ENABLED = "shortcut_notif_enabled"
    const val KEY_ONGOING = "shortcut_notif_ongoing"
    const val KEY_PACKAGE_NAME = "shortcut_notif_package"
    const val KEY_APP_NAME = "shortcut_notif_app_name"
    const val KEY_TITLE = "shortcut_notif_title"
    const val KEY_BODY = "shortcut_notif_body"
    const val KEY_ICON_TYPE = "shortcut_notif_icon_type"
    const val KEY_BANNER_VERSION = "shortcut_notif_banner_version"

    const val ICON_TYPE_APP = "app"
    const val ICON_TYPE_ORBIT = "orbit"
    const val ICON_TYPE_MINIMAL = "minimal"

    const val BANNER_FILE_NAME = "shortcut_notification_banner.png"
    const val DEFAULT_SHORTCUT_ID = "default_shortcut"
    const val BASE_NOTIFICATION_ID = 2002

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves all saved custom shortcut configurations.
     * Automatically migrates legacy single-shortcut preference if present.
     */
    fun getAllShortcuts(context: Context): List<ShortcutItem> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_SHORTCUTS_LIST, null)

        if (jsonString != null && jsonString.isNotBlank()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<ShortcutItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(ShortcutItem.fromJson(obj))
                }
                if (list.isNotEmpty()) {
                    return list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse shortcuts list json", e)
            }
        }

        // Migration from legacy single-shortcut prefs or initialize default
        val legacyPackage = prefs.getString(KEY_PACKAGE_NAME, "") ?: ""
        val legacyAppName = prefs.getString(KEY_APP_NAME, "") ?: ""
        val legacyTitle = prefs.getString(KEY_TITLE, "") ?: ""
        val legacyBody = prefs.getString(KEY_BODY, "") ?: ""
        val legacyIconType = prefs.getString(KEY_ICON_TYPE, ICON_TYPE_APP) ?: ICON_TYPE_APP
        val legacyEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val legacyOngoing = prefs.getBoolean(KEY_ONGOING, true)

        val legacyBannerFile = File(context.filesDir, BANNER_FILE_NAME)
        val hasLegacyBanner = legacyBannerFile.exists()

        val defaultItem = ShortcutItem(
            id = DEFAULT_SHORTCUT_ID,
            notificationId = BASE_NOTIFICATION_ID,
            isEnabled = legacyEnabled,
            isOngoing = legacyOngoing,
            packageName = legacyPackage,
            appName = legacyAppName,
            title = legacyTitle,
            body = legacyBody,
            iconType = legacyIconType,
            bannerFileName = if (hasLegacyBanner) BANNER_FILE_NAME else null,
            bannerVersion = prefs.getInt(KEY_BANNER_VERSION, 0)
        )

        val initialList = listOf(defaultItem)
        saveAllShortcutsList(context, initialList)
        return initialList
    }

    /**
     * Saves the entire list of shortcut items to SharedPreferences.
     */
    private fun saveAllShortcutsList(context: Context, items: List<ShortcutItem>) {
        val array = JSONArray()
        for (item in items) {
            array.put(item.toJson())
        }
        getPrefs(context).edit().putString(KEY_SHORTCUTS_LIST, array.toString()).apply()

        // Also sync primary/first shortcut to legacy keys for backward compatibility
        val primary = items.firstOrNull()
        if (primary != null) {
            getPrefs(context).edit()
                .putBoolean(KEY_ENABLED, primary.isEnabled)
                .putBoolean(KEY_ONGOING, primary.isOngoing)
                .putString(KEY_PACKAGE_NAME, primary.packageName)
                .putString(KEY_APP_NAME, primary.appName)
                .putString(KEY_TITLE, primary.title)
                .putString(KEY_BODY, primary.body)
                .putString(KEY_ICON_TYPE, primary.iconType)
                .putInt(KEY_BANNER_VERSION, primary.bannerVersion)
                .apply()
        }
    }

    fun getShortcutById(context: Context, id: String): ShortcutItem? {
        return getAllShortcuts(context).find { it.id == id }
    }

    fun saveShortcut(context: Context, item: ShortcutItem) {
        val current = getAllShortcuts(context).toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        saveAllShortcutsList(context, current)
    }

    fun setShortcutEnabled(context: Context, id: String, enabled: Boolean) {
        val current = getAllShortcuts(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(isEnabled = enabled)
            saveAllShortcutsList(context, current)
        }
    }

    fun deleteShortcut(context: Context, id: String): Boolean {
        val current = getAllShortcuts(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = current.removeAt(index)
            deleteBannerBitmapForShortcut(context, item)
            saveAllShortcutsList(context, current)
            return true
        }
        return false
    }

    fun getNextNotificationId(context: Context): Int {
        val shortcuts = getAllShortcuts(context)
        val maxId = shortcuts.maxOfOrNull { it.notificationId } ?: (BASE_NOTIFICATION_ID - 1)
        return maxOf(BASE_NOTIFICATION_ID, maxId + 1)
    }

    fun createNewShortcut(context: Context, pkg: String = "", appName: String = ""): ShortcutItem {
        val nextId = getNextNotificationId(context)
        return ShortcutItem(
            id = UUID.randomUUID().toString(),
            notificationId = nextId,
            isEnabled = true,
            isOngoing = true,
            packageName = pkg,
            appName = appName,
            title = appName,
            body = "",
            iconType = ICON_TYPE_APP,
            bannerFileName = null,
            bannerVersion = 0,
            createdAt = System.currentTimeMillis()
        )
    }

    fun loadBannerBitmap(context: Context, item: ShortcutItem): Bitmap? {
        val fileName = item.bannerFileName
            ?: (if (item.id == DEFAULT_SHORTCUT_ID) BANNER_FILE_NAME else null)
            ?: return null
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode banner bitmap for ${item.id}", e)
            null
        }
    }

    fun hasBannerImage(context: Context, item: ShortcutItem): Boolean {
        val fileName = item.bannerFileName
            ?: (if (item.id == DEFAULT_SHORTCUT_ID) BANNER_FILE_NAME else null)
            ?: return false
        return File(context.filesDir, fileName).exists()
    }

    fun saveBannerBitmapForShortcut(context: Context, shortcutId: String, bitmap: Bitmap): String? {
        return try {
            val fileName = "shortcut_banner_${shortcutId}.png"
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            fileName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save banner bitmap for shortcut $shortcutId", e)
            null
        }
    }

    fun deleteBannerBitmapForShortcut(context: Context, item: ShortcutItem) {
        val fileName = item.bannerFileName
            ?: (if (item.id == DEFAULT_SHORTCUT_ID) BANNER_FILE_NAME else null)
        fileName?.let { name ->
            val file = File(context.filesDir, name)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    // ==========================================
    // Legacy Bridge Methods (Maintains 100% compatibility)
    // ==========================================

    fun isEnabled(context: Context): Boolean {
        return getAllShortcuts(context).any { it.isEnabled }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            shortcuts[0] = shortcuts[0].copy(isEnabled = enabled)
            saveAllShortcutsList(context, shortcuts)
        }
    }

    fun isOngoing(context: Context): Boolean {
        return getAllShortcuts(context).firstOrNull()?.isOngoing ?: true
    }

    fun getTargetPackage(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.packageName ?: ""
    }

    fun getTargetAppName(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.appName ?: ""
    }

    fun setTargetApp(context: Context, packageName: String, appName: String) {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            shortcuts[0] = shortcuts[0].copy(packageName = packageName, appName = appName)
            saveAllShortcutsList(context, shortcuts)
        }
    }

    fun setTitle(context: Context, title: String) {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            shortcuts[0] = shortcuts[0].copy(title = title)
            saveAllShortcutsList(context, shortcuts)
        }
    }

    fun setBody(context: Context, body: String) {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            shortcuts[0] = shortcuts[0].copy(body = body)
            saveAllShortcutsList(context, shortcuts)
        }
    }

    fun getTitle(context: Context): String {
        val primary = getAllShortcuts(context).firstOrNull()
        if (primary != null) {
            if (primary.title.isNotBlank()) return primary.title
            if (primary.appName.isNotBlank()) return primary.appName
        }
        return "App Shortcut"
    }

    fun getRawTitle(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.title ?: ""
    }

    fun getBody(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.body ?: ""
    }

    fun getRawBody(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.body ?: ""
    }

    fun getIconType(context: Context): String {
        return getAllShortcuts(context).firstOrNull()?.iconType ?: ICON_TYPE_APP
    }

    fun getBannerVersion(context: Context): Int {
        return getAllShortcuts(context).firstOrNull()?.bannerVersion ?: 0
    }

    fun getBannerFile(context: Context): File {
        val primary = getAllShortcuts(context).firstOrNull()
        val name = primary?.bannerFileName ?: BANNER_FILE_NAME
        return File(context.filesDir, name)
    }

    fun hasBannerImage(context: Context): Boolean {
        val primary = getAllShortcuts(context).firstOrNull()
        return primary?.let { hasBannerImage(context, it) } ?: false
    }

    fun loadBannerBitmap(context: Context): Bitmap? {
        val primary = getAllShortcuts(context).firstOrNull()
        return primary?.let { loadBannerBitmap(context, it) }
    }

    fun saveBannerBitmap(context: Context, bitmap: Bitmap): Boolean {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            val primary = shortcuts[0]
            val fileName = saveBannerBitmapForShortcut(context, primary.id, bitmap)
            if (fileName != null) {
                shortcuts[0] = primary.copy(
                    bannerFileName = fileName,
                    bannerVersion = primary.bannerVersion + 1
                )
                saveAllShortcutsList(context, shortcuts)
                return true
            }
        }
        return false
    }

    fun deleteBannerImage(context: Context): Boolean {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            val primary = shortcuts[0]
            deleteBannerBitmapForShortcut(context, primary)
            shortcuts[0] = primary.copy(bannerFileName = null, bannerVersion = primary.bannerVersion + 1)
            saveAllShortcutsList(context, shortcuts)
            return true
        }
        return false
    }

    fun saveAll(
        context: Context,
        enabled: Boolean,
        ongoing: Boolean,
        pkg: String,
        appName: String,
        title: String,
        body: String,
        iconType: String
    ) {
        val shortcuts = getAllShortcuts(context).toMutableList()
        if (shortcuts.isNotEmpty()) {
            val primary = shortcuts[0]
            shortcuts[0] = primary.copy(
                isEnabled = enabled,
                isOngoing = ongoing,
                packageName = pkg,
                appName = appName,
                title = title,
                body = body,
                iconType = iconType
            )
            saveAllShortcutsList(context, shortcuts)
        } else {
            val newShortcut = ShortcutItem(
                id = DEFAULT_SHORTCUT_ID,
                notificationId = BASE_NOTIFICATION_ID,
                isEnabled = enabled,
                isOngoing = ongoing,
                packageName = pkg,
                appName = appName,
                title = title,
                body = body,
                iconType = iconType
            )
            saveAllShortcutsList(context, listOf(newShortcut))
        }
    }
}
