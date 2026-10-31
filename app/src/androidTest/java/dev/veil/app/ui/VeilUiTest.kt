package dev.veil.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.veil.app.domain.Appearance
import dev.veil.app.domain.MessageRecord
import dev.veil.app.domain.MessagingApp
import dev.veil.app.domain.ObservedNotification
import dev.veil.app.domain.ReplyStatus
import dev.veil.app.domain.SystemSnapshot
import dev.veil.app.domain.TEST_REPLY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests call local callbacks only. They never interact with messaging accounts or dispatch intents. */
@RunWith(AndroidJUnit4::class)
class VeilUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floatingDockExposesFiveAccessibleTabsAndChangesSelection() {
        val selected = mutableStateOf(Destination.HOME)
        compose.setContent {
            VeilTheme(Appearance.LIGHT) {
                Box(Modifier.width(560.dp)) {
                    FloatingDock(selected.value.route, reducedMotion = true, intensity = .65f,
                        onSelect = { selected.value = it })
                }
            }
        }

        Destination.entries.forEach { destination ->
            compose.onNodeWithContentDescription("${destination.label} tab")
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
        compose.onNodeWithContentDescription("Inbox tab").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Home tab").assertIsNotSelected()
        compose.runOnIdle { assertEquals(Destination.INBOX, selected.value) }
    }

    @Test fun darkDockWithLargeTextKeepsTabsOperable() {
        val selected = mutableStateOf(Destination.HOME)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                VeilTheme(Appearance.DARK) {
                    Box(Modifier.width(560.dp)) {
                        FloatingDock(selected.value.route, reducedMotion = true, intensity = 1f,
                            onSelect = { selected.value = it })
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Settings tab").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(Destination.SETTINGS, selected.value) }
    }

    @Test fun testReplyRequiresExplicitConsentAndShowsExactRecipientAndText() {
        var sends = 0
        compose.setContent {
            VeilTheme { TestReplyDialog(message("first"), onDismiss = {}, onSend = { sends++ }) }
        }

        compose.onNodeWithText("WhatsApp · Consenting tester").assertIsDisplayed()
        compose.onNodeWithText(TEST_REPLY).assertIsDisplayed()
        compose.onNodeWithText("Send test").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithContentDescription("This contact agreed to receive a test reply")
            .assertIsOff().performClick()
        compose.onNodeWithText("Send test").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun consentDoesNotCarryOverToDifferentNotification() {
        val notification = mutableStateOf(message("first"))
        var sends = 0
        compose.setContent {
            VeilTheme { TestReplyDialog(notification.value, onDismiss = {}, onSend = { sends++ }) }
        }
        compose.onNodeWithContentDescription("This contact agreed to receive a test reply").performClick()
        compose.onNodeWithText("Send test").assertIsEnabled()

        compose.runOnIdle { notification.value = message("second") }
        compose.onNodeWithContentDescription("This contact agreed to receive a test reply").assertIsOff()
        compose.onNodeWithText("Send test").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun instagramRequiresAUsernameAndPreservesOptionalNumericId() {
        var saved: List<String>? = null
        compose.setContent {
            VeilTheme {
                AddContactDialog(MessagingApp.INSTAGRAM, onDismiss = {}) { label, app, sender, handle, id, phone ->
                    saved = listOf(label, app.name, sender, handle, id, phone)
                }
            }
        }
        compose.onNodeWithText("Save person").assertIsNotEnabled()
        compose.onNodeWithText("Person's display name").performTextInput("Priyanshi")
        compose.onNodeWithText("Save person").assertIsNotEnabled()
        compose.onNodeWithText("Instagram username").performTextInput("@friend_01")
        compose.onNodeWithText("Numeric Instagram user ID (optional)").performTextInput("123456789")
        compose.onNodeWithText("Save person").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Priyanshi", "INSTAGRAM", "Priyanshi", "friend_01", "123456789", ""), saved)
        }
    }

    @Test fun whatsappStoresDeviceNameAndEnteredContactNumber() {
        var saved: List<String>? = null
        compose.setContent {
            VeilTheme {
                AddContactDialog(MessagingApp.WHATSAPP, onDismiss = {}) { label, app, sender, handle, id, number ->
                    saved = listOf(label, app.name, sender, handle, id, number)
                }
            }
        }
        compose.onNodeWithText("Save person").assertIsNotEnabled()
        compose.onNodeWithText("Name saved on device").performTextInput("Aditi")
        compose.onNodeWithText("WhatsApp phone number (optional)").performTextInput("+91 98765 43210")
        compose.onNodeWithText("Save person").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Aditi", "WHATSAPP", "Aditi", "", "", "+919876543210"), saved)
        }
    }


    @Test fun onboardingDoesNotOptInAnyPlatformByDefault() {
        var completed: Set<MessagingApp>? = null
        compose.setContent { VeilTheme { VeilOnboarding(SystemSnapshot()) { completed = it } } }

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Observe WhatsApp notifications").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("Observe Instagram notifications").performScrollTo().assertIsOff()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Open VEIL").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(completed)
            assertTrue(completed!!.isEmpty())
        }
    }

    private fun message(id: String) = MessageRecord(
        observation = ObservedNotification(id, "notification-key-hash", MessagingApp.WHATSAPP,
            "Consenting tester", "A harmless test message", 1_700_000_000_000L, 1_700_000_000_000L,
            isMessagingStyle = true, isGroup = false, actionCount = 1, hasReplyAction = true),
        status = ReplyStatus.READY,
        active = true,
    )
}
