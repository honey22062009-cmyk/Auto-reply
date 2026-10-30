package dev.veil.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.domain.Appearance
import dev.veil.app.domain.MessagingApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreferencesRepositoryTest {
    @Test fun `capture is opt in and user settings survive a new repository instance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferencesRepository(context)
        val defaults = preferences.preferences.first()
        assertFalse(defaults.onboarded)
        assertTrue(defaults.enabledApps.isEmpty())
        preferences.finishOnboarding(setOf(MessagingApp.WHATSAPP))
        preferences.setApp(MessagingApp.INSTAGRAM, true)
        preferences.setApp(MessagingApp.WHATSAPP, false)
        preferences.setPaused(true)
        preferences.setAppearance(Appearance.DARK)
        preferences.setMotion(true)
        preferences.setGlass(2f)
        preferences.setRetention(20)
        val restored = PreferencesRepository(context).preferences.first()
        assertTrue(restored.onboarded)
        assertEquals(setOf(MessagingApp.INSTAGRAM), restored.enabledApps)
        assertTrue(restored.capturePaused)
        assertEquals(Appearance.DARK, restored.appearance)
        assertTrue(restored.reduceMotion)
        assertEquals(1f, restored.glassIntensity, 0f)
        assertEquals(7, restored.retentionDays)
    }
}
