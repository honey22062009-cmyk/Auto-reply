package dev.veil.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.veil.app.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Local UI callbacks only; no notification dispatch and no AI/provider calls. */
@RunWith(AndroidJUnit4::class)
class CompanionPersonalizationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun companionOpensLocalGuidanceAndNavigatesToAnActualDestination() {
        var route: String? = null
        compose.setContent { VeilTheme { CompanionDock(VeilUiState(), { route = it }, {}) } }
        compose.onNodeWithContentDescription("Mote, your VEIL guide").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("A little guidance from Mote").assertIsDisplayed()
        compose.onNodeWithText("Open Compatibility Lab").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("lab", route) }
    }

    @Test fun hidingCompanionRemovesItsTouchTarget() {
        val enabled = mutableStateOf(true)
        compose.setContent { VeilTheme {
            CompanionDock(VeilUiState(preferences = VeilPreferences(companionEnabled = enabled.value)), {}, { enabled.value = false })
        } }
        compose.onNodeWithContentDescription("Mote, your VEIL guide").performClick()
        compose.onNodeWithText("Hide Mote · bring back in Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mote, your VEIL guide").assertDoesNotExist()
    }

    @Test fun eachColorChoiceIsExplicitAndSelectedStateUpdates() {
        val selected = mutableStateOf(AccentPalette.ROSE)
        compose.setContent { VeilTheme(palette = selected.value) { AccentSelector(selected.value, { selected.value = it }) } }
        compose.onNodeWithContentDescription("Rose accent").assertIsSelected()
        AccentPalette.entries.forEach { palette ->
            compose.onNodeWithContentDescription("${accentLabel(palette)} accent").assertHeightIsAtLeast(48.dp)
                .performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(palette, selected.value) }
        }
    }

    @Test fun narrowLargeFontDockRetainsAccessibleTouchTargets() {
        val selected = mutableStateOf(Destination.HOME)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                VeilTheme { Box(Modifier.width(320.dp)) {
                    FloatingDock(selected.value.route, true, .65f, { selected.value = it })
                } }
            }
        }
        Destination.entries.forEach { destination ->
            compose.onNodeWithContentDescription("${destination.label} tab").assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        }
    }

    @Test fun aManualReplyNeedsRecipientAndTextConfirmation() {
        var sends = 0
        val message = MessageRecord(ObservedNotification("synthetic", "synthetic-key", MessagingApp.WHATSAPP,
            "Synthetic test contact", "Hello", 1000, 1000, true, false, 1, true), ReplyStatus.READY, true)
        compose.setContent { VeilTheme { ReplyReviewDialog(message, "Hi dost", {}, { sends++ }) } }
        compose.onNodeWithText("Hi dost").assertIsDisplayed()
        compose.onNodeWithText("Send reply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("I reviewed the recipient and reply text").performClick()
        compose.onNodeWithText("Send reply").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }
}
