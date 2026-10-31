package dev.veil.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.veil.app.domain.AccentPalette
import dev.veil.app.domain.VeilPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilPaletteTest {
    @Test fun newUsersStartWithAuroraButCanSelectDistinctPurpleAndBlue() {
        assertEquals(AccentPalette.SUNSET, VeilPreferences().accent)
        assertNotEquals(veilColorScheme(false, AccentPalette.SUNSET).primary,
            veilColorScheme(false, AccentPalette.BLOSSOM).primary)
        assertNotEquals(veilColorScheme(false, AccentPalette.BLOSSOM).primary,
            veilColorScheme(false, AccentPalette.CHAMPAGNE).primary)
    }

    @Test fun newPalettesKeepReadablePrimaryButtonsInBothModes() {
        listOf(AccentPalette.SUNSET, AccentPalette.BLOSSOM, AccentPalette.CHAMPAGNE).forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = veilColorScheme(dark, palette)
                assertTrue("${palette.name} dark=$dark primary contrast",
                    contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
                assertTrue("${palette.name} dark=$dark body contrast",
                    contrast(scheme.background, scheme.onBackground) >= 7f)
                assertTrue("${palette.name} dark=$dark accent distinction",
                    scheme.primary != scheme.secondary)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
}
