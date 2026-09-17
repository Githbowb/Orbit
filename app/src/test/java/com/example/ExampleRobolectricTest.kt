package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Orbit", appName)
  }

  @Test
  fun `ocr and app launcher cannot be disabled`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Attempt to disable OCR and Launcher
    ToolsPreferences.setToolEnabled(context, ToolsPreferences.KEY_OCR, false)
    ToolsPreferences.setToolEnabled(context, ToolsPreferences.KEY_LAUNCHPAD, false)

    // Verify they remain enabled
    org.junit.Assert.assertTrue(ToolsPreferences.isToolEnabled(context, ToolsPreferences.KEY_OCR))
    org.junit.Assert.assertTrue(ToolsPreferences.isToolEnabled(context, ToolsPreferences.KEY_LAUNCHPAD))
  }

  @Test
  fun `shortcut tutorial strings are available`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tutorialTitle = context.getString(R.string.shortcut_tutorial_title)
    val alwaysActive = context.getString(R.string.tool_always_active)
    org.junit.Assert.assertTrue(tutorialTitle.isNotBlank())
    org.junit.Assert.assertTrue(alwaysActive.isNotBlank())
  }
}
