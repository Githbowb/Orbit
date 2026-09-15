package com.example

import android.app.Application
import com.example.ocr.OcrManager
import com.example.shortcut.ShortcutNotificationManager

/**
 * Application subclass for Orbit.
 *
 * Sole purpose right now: initialize the [OcrManager] singleton with the application
 * context as early as possible so that the model download status is checked early,
 * and restore active persistent services.
 *
 * Registered in AndroidManifest.xml via `android:name=".OrbitApp"` on `<application>`.
 */
class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hand the application context to OcrManager so it can initialize.
        OcrManager.setApplicationContext(this)

        // Monitor cache size asynchronously on app startup and auto-clear if >= 100MB
        CacheManager.checkAndAutoClearCache(this)

        // Initialize and sync custom persistent shortcut notification if enabled
        ShortcutNotificationManager.init(this)
    }
}
