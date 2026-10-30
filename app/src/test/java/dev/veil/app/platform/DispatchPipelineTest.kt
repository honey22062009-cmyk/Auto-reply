package dev.veil.app.platform

import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.domain.DispatchResult
import dev.veil.app.domain.MessagingApp
import dev.veil.app.domain.TEST_REPLY
import dev.veil.app.domain.VeilPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Framework shadows only: no WhatsApp account, recipient, network, or real message is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DispatchPipelineTest {
    private lateinit var context: Context
    private lateinit var controller: PlatformController
    private lateinit var listener: VeilNotificationListener
    private lateinit var replyIntent: PendingIntent

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        controller = PlatformController(context)
        controller.configure(VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP)))
        listener = Robolectric.buildService(VeilNotificationListener::class.java).get()
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(context, VeilNotificationListener::class.java), true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceLocked(false)
        replyIntent = PendingIntent.getBroadcast(context, 99,
            Intent("dev.veil.SYNTHETIC_REPLY_TEST").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
        shadowOf(replyIntent).setCreatorPackage(MessagingApp.WHATSAPP.packageName)
    }

    @Test fun `explicit test dispatch puts exactly fixed text into action declared result key`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        assertEquals(0, broadcasts().size) // Detection alone never dispatches.
        assertEquals(DispatchResult.Accepted, controller.dispatchTest(id))
        assertEquals(1, broadcasts().size)
        val results = RemoteInput.getResultsFromIntent(broadcasts().single())
        assertEquals(TEST_REPLY, results!!.getCharSequence("owned_reply_key").toString())
        assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, RemoteInput.getResultsSource(broadcasts().single()))
    }

    @Test fun `second explicit test on same handle is rejected and sends nothing more`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        assertEquals(DispatchResult.Accepted, controller.dispatchTest(id))
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertEquals(1, broadcasts().size)
    }

    @Test fun `changed live message invalidates reviewed reply even before update callback`() = runBlocking {
        val old = message("hello", 1200)
        val id = connectWith(old)
        listener.cancelNotification(old.key)
        shadowOf(listener).addActiveNotification(message("a different message", 1300))
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `notification missing from live system list cannot use retained action`() = runBlocking {
        val old = message("hello", 1200)
        val id = connectWith(old)
        listener.cancelNotification(old.key)
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `cancelled PendingIntent fails without implicit retry`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        replyIntent.cancel()
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `group live message is inspectable but cannot be sent a test`() = runBlocking {
        val id = connectWith(message("hello", 1200, group = true))
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `device lock prevents action dispatch`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceLocked(true)
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `permission revocation after observation prevents dispatch`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(context, VeilNotificationListener::class.java), false)
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        assertTrue(broadcasts().isEmpty())
    }

    @Test fun `disconnect invalidates actions and a stale service cannot disconnect replacement`() = runBlocking {
        val id = connectWith(message("hello", 1200))
        controller.disconnected(listener)
        assertFalse(controller.connected.value)
        assertTrue(controller.dispatchTest(id) is DispatchResult.Rejected)
        val replacement = Robolectric.buildService(VeilNotificationListener::class.java).get()
        controller.connected(replacement)
        controller.disconnected(listener)
        assertTrue(controller.connected.value)
        assertTrue(broadcasts().isEmpty())
    }

    private fun broadcasts() = shadowOf(context as Application).broadcastIntents
        .filter { it.action == "dev.veil.SYNTHETIC_REPLY_TEST" }

    private fun connectWith(notification: StatusBarNotification): String {
        shadowOf(listener).addActiveNotification(notification)
        controller.connected(listener)
        return NotificationInspector.inspect(notification)!!.observation.id
    }

    private fun message(text: String, at: Long, group: Boolean = false): StatusBarNotification {
        val self = Person.Builder().setName("You").setKey("self").build()
        val sender = Person.Builder().setName("Consenting fixture").setKey("test-only").build()
        val style = Notification.MessagingStyle(self).setGroupConversation(group).addMessage(text, at, sender)
        val action = Notification.Action.Builder(null, "Reply", replyIntent)
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(RemoteInput.Builder("owned_reply_key").build()).build()
        val notification = Notification.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_email).setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(style).addAction(action).build()
        return StatusBarNotification(MessagingApp.WHATSAPP.packageName, MessagingApp.WHATSAPP.packageName,
            7, null, 10001, 0, 0, notification, android.os.Process.myUserHandle(), 2000)
    }
}
