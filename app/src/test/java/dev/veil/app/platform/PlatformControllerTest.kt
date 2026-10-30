package dev.veil.app.platform

import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.domain.MessagingApp
import dev.veil.app.domain.DispatchResult
import dev.veil.app.domain.NotificationSink
import dev.veil.app.domain.ObservedNotification
import dev.veil.app.domain.VeilPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformControllerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val sink = RecordingSink()
    private val controller get() = PlatformController(context).also { it.sink = sink }

    @Test fun `no content captured before explicit onboarding selection`() {
        val platform = controller
        platform.observed(notification())
        assertTrue(sink.observations.isEmpty())
        platform.configure(VeilPreferences(onboarded = true))
        platform.observed(notification())
        assertTrue(sink.observations.isEmpty())
    }

    @Test fun `only explicitly selected platform captured`() {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        platform.observed(notification())
        platform.observed(notification(MessagingApp.INSTAGRAM))
        assertEquals(1, sink.observations.size)
        assertEquals(MessagingApp.WHATSAPP, sink.observations.single().app)
    }

    @Test fun `global pause stops content observation`() {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = MessagingApp.entries.toSet(), capturePaused = true))
        platform.observed(notification())
        assertTrue(sink.observations.isEmpty())
    }

    @Test fun `capture stop latch ignores older unpaused preferences until explicit resume`() {
        val platform = controller
        val enabled = VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP))
        platform.configure(enabled)
        platform.setCaptureStop(true)
        platform.configure(enabled)
        platform.observed(notification())
        assertTrue(sink.observations.isEmpty())
        platform.setCaptureStop(false)
        platform.observed(notification())
        assertEquals(1, sink.observations.size)
    }

    @Test fun `notification removal emits only a hash`() {
        val platform = controller
        platform.removed(notification())
        assertEquals(64, sink.removals.single().length)
        assertFalse(sink.removals.single().contains("com.whatsapp"))
    }

    @Test fun `invalidated non-message update removes old key`() {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        platform.observed(notification())
        val invalid = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setCategory(Notification.CATEGORY_SERVICE).build()
        platform.observed(notification(value = invalid))
        assertEquals(1, sink.observations.size)
        assertEquals(sink.observations.single().keyHash, sink.removals.single())
    }

    @Test fun `opening forgotten handle fails safely`() {
        val platform = controller
        assertFalse(platform.openConversation("unknown"))
        platform.clear()
        assertFalse(platform.openConversation("unknown"))
    }

    @Test fun `manual dispatch without a live handle rejects rather than guessing a recipient`() = runBlocking {
        assertTrue(controller.dispatchTest("unknown") is DispatchResult.Rejected)
    }

    @Test fun `pausing removes captured live handles before a queued test`() = runBlocking {
        val platform = controller
        val preferences = VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP))
        platform.configure(preferences)
        platform.observed(notification())
        val id = sink.observations.single().id
        platform.configure(preferences.copy(capturePaused = true))
        assertTrue(platform.dispatchTest(id) is DispatchResult.Rejected)
    }

    @Test fun `disabling a platform prevents reuse of its previously captured action`() = runBlocking {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        platform.observed(notification())
        val id = sink.observations.single().id
        platform.configure(VeilPreferences(onboarded = true))
        assertTrue(platform.dispatchTest(id) is DispatchResult.Rejected)
    }

    @Test fun `removed notifications cannot be dispatched`() = runBlocking {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        val notification = notification()
        platform.observed(notification)
        val id = sink.observations.single().id
        platform.removed(notification)
        assertTrue(platform.dispatchTest(id) is DispatchResult.Rejected)
    }

    @Test fun `capture never dispatches and no notification access means test fails closed`() = runBlocking {
        val platform = controller
        platform.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        platform.observed(notification())
        val result = platform.dispatchTest(sink.observations.single().id)
        assertTrue(result is DispatchResult.Rejected)
        assertTrue((result as DispatchResult.Rejected).reason.contains("Notification access"))
    }

    private fun notification(app: MessagingApp = MessagingApp.WHATSAPP, value: Notification = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_email).setCategory(Notification.CATEGORY_MESSAGE)
        .setContentTitle("Test contact").setContentText("Hello").build()) =
        StatusBarNotification(app.packageName, app.packageName, 7, null, 10001, 0, 0, value, android.os.Process.myUserHandle(), 2000)

    private class RecordingSink : NotificationSink {
        val observations = mutableListOf<ObservedNotification>()
        val removals = mutableListOf<String>()
        override fun onObserved(notification: ObservedNotification) { observations += notification }
        override fun onRemoved(keyHash: String) { removals += keyHash }
        override fun onConnectionChanged(connected: Boolean) = Unit
    }
}
