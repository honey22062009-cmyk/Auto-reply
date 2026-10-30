package dev.veil.app.ui

import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.veil.app.MainActivity
import dev.veil.app.VeilApplication
import dev.veil.app.domain.Appearance
import dev.veil.app.domain.AccentPalette
import dev.veil.app.ai.AiConfiguration
import dev.veil.app.ai.WritingStyle
import androidx.test.espresso.Espresso
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
        compose.onNodeWithText("Add priority contact").performClick()
        compose.onNodeWithText("Local contact label").performTextInput("Synthetic QA contact")
        compose.onNodeWithText("Exact notification sender").performTextInput("Synthetic QA sender")
        compose.onNodeWithText("Save contact").performClick()
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

        // Mote's route opens the actual studio; the preview uses saved offline templates only.
        compose.onNodeWithContentDescription("Mote, your VEIL guide").performClick()
        compose.onNodeWithText("Open reply settings").performScrollTo().performClick()
        compose.onNodeWithText("A voice that feels like you.").assertIsDisplayed()
        compose.onNodeWithText("A sample incoming message").performScrollTo().performTextInput("Hello")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Draft preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Draft preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hey!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home tab").performClick()
        compose.onNodeWithText("Your conversations.\nA little more breathing room.").performScrollTo()
        compose.onNodeWithText("Observation paused").assertIsDisplayed()
        // Clear only this test window's protection to capture synthetic, empty-app QA artwork.
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        try {
            // Shell-owned output survives connectedDebugAndroidTest's app uninstall/cleanup.
            shell("screencap -p /data/local/tmp/veil-home-dark.png")
            assertTrue(shell("test -s /data/local/tmp/veil-home-dark.png && echo VEIL_SCREENSHOT_OK").contains("VEIL_SCREENSHOT_OK"))
        } finally {
            compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        runBlocking { graph.preferences.setPaused(false) }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
