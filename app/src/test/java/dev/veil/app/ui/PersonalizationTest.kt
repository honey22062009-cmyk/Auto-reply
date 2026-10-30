package dev.veil.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.veil.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PersonalizationTest {
    @Test fun `every accent keeps primary and container text above AA contrast in both themes`() {
        AccentPalette.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val colors = veilColorScheme(dark, accent)
                assertTrue("$accent dark=$dark primary text", contrast(colors.primary, colors.onPrimary) >= 4.5)
                assertTrue("$accent dark=$dark container text", contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5)
            }
        }
    }

    @Test fun `guide prioritizes access pause and explicit app selection without reading message text`() {
        val state = VeilUiState()
        assertEquals("lab", companionTips(state).first().route)
        val connected = state.copy(system = SystemSnapshot(notificationAccess = true))
        assertEquals("settings", companionTips(connected).first().route)
        val paused = connected.copy(preferences = VeilPreferences(capturePaused = true))
        assertEquals("home", companionTips(paused).first().route)
        val enabled = connected.copy(preferences = VeilPreferences(enabledApps = setOf(MessagingApp.WHATSAPP)))
        assertEquals("lab", companionTips(enabled).first().route)
        assertTrue(companionTips(enabled).any { it.route == "ai" })
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble(); val b = second.luminance().toDouble()
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
