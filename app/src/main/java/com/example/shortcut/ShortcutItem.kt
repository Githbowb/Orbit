package com.example.shortcut

import org.json.JSONObject
import java.util.UUID

/**
 * Represents a single custom persistent shortcut notification.
 */
data class ShortcutItem(
    val id: String = UUID.randomUUID().toString(),
    val notificationId: Int = 2002,
    val isEnabled: Boolean = true,
    val isOngoing: Boolean = true,
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val body: String = "",
    val iconType: String = ShortcutNotificationPreferences.ICON_TYPE_APP,
    val bannerFileName: String? = null,
    val bannerVersion: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun displayTitle(): String {
        if (title.isNotBlank()) return title
        if (appName.isNotBlank()) return appName
        return "App Shortcut"
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("notificationId", notificationId)
            put("isEnabled", isEnabled)
            put("isOngoing", isOngoing)
            put("packageName", packageName)
            put("appName", appName)
            put("title", title)
            put("body", body)
            put("iconType", iconType)
            put("bannerFileName", bannerFileName ?: "")
            put("bannerVersion", bannerVersion)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ShortcutItem {
            val bannerFile = json.optString("bannerFileName", "").takeIf { it.isNotBlank() }
            return ShortcutItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                notificationId = json.optInt("notificationId", 2002),
                isEnabled = json.optBoolean("isEnabled", true),
                isOngoing = json.optBoolean("isOngoing", true),
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                title = json.optString("title", ""),
                body = json.optString("body", ""),
                iconType = json.optString("iconType", ShortcutNotificationPreferences.ICON_TYPE_APP),
                bannerFileName = bannerFile,
                bannerVersion = json.optInt("bannerVersion", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
