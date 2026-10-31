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
        assertEquals(AccentPalette.AURORA, VeilPreferences().accent)
        assertNotEquals(veilColorScheme(false, AccentPalette.AURORA).primary,
            veilColorScheme(false, AccentPalette.VIOLET).primary)
        assertNotEquals(veilColorScheme(false, AccentPalette.VIOLET).primary,
            veilColorScheme(false, AccentPalette.AZURE).primary)
    }

    @Test fun newPalettesKeepReadablePrimaryButtonsInBothModes() {
        listOf(AccentPalette.AURORA, AccentPalette.VIOLET, AccentPalette.AZURE).forEach { palette ->
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
