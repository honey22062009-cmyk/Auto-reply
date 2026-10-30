package dev.veil.app.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.veil.app.MainActivity
import dev.veil.app.VeilApplication
import dev.veil.app.domain.Appearance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

/** Uses a disposable emulator/test installation and only synthetic local contact data. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationPauseContactAndThemePersistThroughRealApplication() {
        val graph = (compose.activity.application as VeilApplication).graph
        runBlocking {
            graph.preferences.finishOnboarding(emptySet())
            graph.preferences.setPaused(false)
            graph.preferences.setAppearance(Appearance.LIGHT)
            graph.preferences.setMotion(true)
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
        compose.onNodeWithContentDescription("Home tab").performClick()
        compose.onNodeWithText("Observation paused").assertIsDisplayed()
        // Clear only this test window's protection to capture synthetic, empty-app QA artwork.
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        val output = File(compose.activity.getExternalFilesDir("validation"), "home-dark.png")
        output.outputStream().use { stream ->
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        runBlocking { graph.preferences.setPaused(false) }
    }
}
