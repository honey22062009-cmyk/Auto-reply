package dev.veil.app.ui

import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.veil.app.MainActivity
import dev.veil.app.VeilApplication
import dev.veil.app.domain.Appearance
import dev.veil.app.domain.AccentPalette
import dev.veil.app.ai.AiConfiguration
import dev.veil.app.ai.WritingStyle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Uses a disposable emulator/test installation and only synthetic local contact data. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationPauseContactAndThemePersistThroughRealApplication() {
        assumeTrue("Run this data-reset smoke test only on the disposable Google emulator.",
            Build.MODEL.startsWith("sdk_gphone"))
        val graph = (compose.activity.application as VeilApplication).graph
        runBlocking {
            graph.preferences.finishOnboarding(emptySet())
            graph.preferences.setPaused(false)
            graph.preferences.setAppearance(Appearance.LIGHT)
            graph.preferences.setMotion(true)
            graph.preferences.setAccent(AccentPalette.ROSE)
            graph.preferences.setCompanion(true)
            graph.preferences.saveAi(AiConfiguration())
            graph.preferences.saveStyle(WritingStyle())
            graph.repository.deleteHistory()
        }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithContentDescription("Home tab").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
        compose.onNodeWithContentDescription("Pause everything").performClick()
        compose.waitUntil(30_000) { graph.readyPreferences.value?.capturePaused == true }
        compose.onNodeWithContentDescription("Pause everything").assertIsOn()
        compose.onNodeWithContentDescription("Inbox tab").performClick().assertIsSelected()
        compose.onNodeWithText("Your first notification starts here.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Priority tab").performClick()
        compose.onNodeWithContentDescription("Add WhatsApp person").performClick()
        compose.onNodeWithText("Name saved on device").performTextInput("Synthetic QA contact")
        compose.onNodeWithText("Notification sender (if different)").performTextInput("Synthetic QA sender")
        compose.onNodeWithText("Save person").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Synthetic QA contact").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Synthetic QA contact").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Synthetic QA contact").performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Synthetic QA contact").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Activity tab").performClick().assertIsSelected()
        compose.onNodeWithText("A clear record.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings tab").performClick().assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(30_000) { graph.readyPreferences.value?.appearance == Appearance.DARK }
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithContentDescription("Plum accent").performScrollTo().performClick()
        compose.waitUntil(30_000) { graph.readyPreferences.value?.accent == AccentPalette.PLUM }
        compose.onNodeWithContentDescription("Plum accent").assertIsSelected()

        // Test the direct, user-facing reply-style entry point. Mote has its own dedicated UI test.
        compose.onNodeWithContentDescription("Home tab").performClick()
        compose.onNodeWithText("Reply style").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithText("A voice that feels like you.").assertIsDisplayed()
        compose.onNodeWithText("A sample incoming message").performScrollTo().performTextInput("Hello")
        // Use the owning activity's token instead of Espresso root-window selection,
        // which failed to find a focused root during this modal/IME handoff in CI.
        compose.activityRule.scenario.onActivity { activity ->
            activity.currentFocus?.let { focused ->
                activity.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(focused.windowToken, 0)
                focused.clearFocus()
            }
        }
        compose.waitForIdle()
        // This smoke test checks navigation, persistence and form usability.
        // Async preview generation is exercised independently below, so a slow
        // background operation cannot conceal a broken tab or state restoration.
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsEnabled()
        // Verify that returning from a detail route really selects Home (not a restored AI Studio).
        compose.onNodeWithContentDescription("Home tab").performClick().assertIsSelected()
        assertTrue("Paused preference must survive detail-screen navigation",
            graph.readyPreferences.value?.capturePaused == true)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Observation paused").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Observation paused").performScrollTo().assertIsDisplayed()
        // Clear only this test window's protection to capture synthetic, empty-app QA artwork.
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        try {
            // A screenshot is optional visual evidence, not a product behavior assertion.
            // On some emulators UiAutomation's shell cannot persist files under /data/local/tmp.
            // The navigation, pause, theme and reply-preview checks above remain mandatory.
            val captured = runCatching {
                shell("screencap -p /data/local/tmp/veil-home-dark.png")
                shell("test -s /data/local/tmp/veil-home-dark.png && echo VEIL_SCREENSHOT_OK")
                    .contains("VEIL_SCREENSHOT_OK")
            }.getOrDefault(false)
            if (!captured) android.util.Log.w("VEIL_SMOKE",
                "Optional emulator screenshot unavailable; executed UI assertions passed.")
        } finally {
            compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        runBlocking { graph.preferences.setPaused(false) }
    }

    @Test fun offlinePreviewProducesRealViewModelState() {
        assumeTrue("Use a disposable emulator with no real messaging account.",
            Build.MODEL.startsWith("sdk_gphone"))
        val graph = (compose.activity.application as VeilApplication).graph
        runBlocking {
            graph.preferences.finishOnboarding(emptySet())
            graph.preferences.saveAi(AiConfiguration())
            graph.preferences.saveStyle(WritingStyle())
        }
        compose.waitUntil(30_000) {
            graph.readyPreferences.value?.ai == AiConfiguration() &&
                graph.readyPreferences.value?.style == WritingStyle()
        }
        compose.runOnIdle {
            graph.previewDraft.value = null
            graph.notice.value = null
            androidx.lifecycle.ViewModelProvider(compose.activity)
                .get(dev.veil.app.VeilViewModel::class.java).previewReply("Hello")
        }
        compose.waitUntil(20_000) {
            graph.previewDraft.value != null ||
                (!graph.previewBusy.value && graph.notice.value != null)
        }
        org.junit.Assert.assertEquals(
            "Real offline preview failed: ${graph.notice.value}", "Hey!", graph.previewDraft.value)
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
