package dev.veil.app.platform

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.domain.MessagingApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic framework fixtures validate our parser; these are not app-version compatibility tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationInspectorTest {
    @Test @Config(sdk = [28]) fun `Android 9 keeps manual previews without claiming a UID backed automation identity`() {
        val observation = inspect(styleMessage("Hello",1200))!!.observation
        assertEquals("Hello",observation.preview)
        assertNull(observation.identityKey)
    }
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val person get() = Person.Builder().setName("Aditi").setKey("friend-1").build()
    private val self get() = Person.Builder().setName("You").setKey("local-user").build()

    @Test fun `extracts sender and latest incoming message from MessagingStyle`() {
        val result = inspect(styleMessage("Kal milte hain?", 1200))!!
        assertEquals("Aditi", result.observation.sender)
        assertEquals("Kal milte hain?", result.observation.preview)
        assertEquals(1200L, result.observation.messageAt)
        assertTrue(result.observation.isMessagingStyle)
        assertFalse(result.observation.isGroup)
    }

    @Test fun `null sender representing own reply is ignored`() {
        val style = Notification.MessagingStyle(self)
            .addMessage("incoming", 1200, person)
            .addMessage(Notification.MessagingStyle.Message("my reply", 1300, null as Person?))
        assertNull(inspect(builder().setStyle(style).build()))
    }

    @Test fun `explicit self person key is ignored`() {
        val style = Notification.MessagingStyle(self).addMessage("my reply", 1200, self)
        assertNull(inspect(builder().setStyle(style).build()))
    }

    @Test fun `same display name does not imply same person`() {
        val friend = Person.Builder().setName("You").setKey("another-user").build()
        val style = Notification.MessagingStyle(self).addMessage("hello", 1200, friend)
        assertNotNull(inspect(builder().setStyle(style).build()))
    }

    @Test fun `same displayed sender with different person keys changes reviewed fingerprint`() {
        fun message(key: String) = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, Person.Builder().setName("Aditi").setKey(key).build())).build()
        assertNotEquals(inspect(message("person-a"))!!.observation.id,
            inspect(message("person-b"))!!.observation.id)
    }

    @Test fun `person identity binding survives sender label and notification key changes`() {
        fun message(label: String) = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, Person.Builder().setName(label).setKey("friend-1").build())).build()
        val first = inspect(message("Aditi"), notificationId = 7)!!.observation
        val renamed = inspect(message("Aditi new label"), notificationId = 8)!!.observation
        assertNotNull(first.identityKey)
        assertEquals(first.identityKey, renamed.identityKey)
        assertEquals(first.id, renamed.id) // A live repost/name change must not create another reply opportunity.
        assertFalse(first.identityKey!!.contains("friend-1"))
    }

    @Test fun `same displayed name is not a binding when person identifiers differ`() {
        fun message(key: String) = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, Person.Builder().setName("Aditi").setKey(key).build())).build()
        assertNotEquals(inspect(message("one"))!!.observation.identityKey,
            inspect(message("two"))!!.observation.identityKey)
    }

    @Test fun `display name and recyclable notification key never form an automation identity`() {
        val plain = builder().setContentTitle("Aditi").setContentText("hello").build()
        val nameOnlyStyle = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, Person.Builder().setName("Aditi").build())).build()
        assertNull(inspect(plain)!!.observation.identityKey)
        assertNull(inspect(nameOnlyStyle)!!.observation.identityKey)
    }

    @Test fun `app conversation shortcuts can anchor notifications without person keys`() {
        val first = builder().setShortcutId("private-conversation-one").setContentTitle("Aditi").setContentText("hello").build()
        val same = builder().setShortcutId("private-conversation-one").setContentTitle("Renamed").setContentText("hi").build()
        val other = builder().setShortcutId("private-conversation-two").setContentTitle("Aditi").setContentText("hello").build()
        val identity = inspect(first)!!.observation.identityKey
        assertNotNull(identity)
        assertEquals(identity, inspect(same, notificationId = 9)!!.observation.identityKey)
        assertNotEquals(identity, inspect(other)!!.observation.identityKey)
        assertFalse(identity!!.contains("private-conversation"))
    }

    @Test fun `person URI binding is opaque and platform scoped`() {
        val notification = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, Person.Builder().setName("Aditi").setUri("tel:+15550000000").build())).build()
        val whatsapp = inspect(notification)!!.observation.identityKey
        val instagram = inspect(notification, packageName = MessagingApp.INSTAGRAM.packageName)!!.observation.identityKey
        assertNotNull(whatsapp)
        assertFalse(whatsapp!!.contains("15550000000"))
        assertNotEquals(whatsapp, instagram)
    }

    @Test fun `person identity and exposed shortcut are combined conservatively`() {
        fun message(shortcut: String) = builder().setShortcutId(shortcut)
            .setStyle(Notification.MessagingStyle(self).addMessage("hello", 1200, person)).build()
        assertNotEquals(inspect(message("one"))!!.observation.identityKey,
            inspect(message("two"))!!.observation.identityKey)
    }

    @Test fun `person key and URI are distinct identifier namespaces`() {
        fun message(sender: Person) = builder().setStyle(Notification.MessagingStyle(self)
            .addMessage("hello", 1200, sender)).build()
        val keyed = message(Person.Builder().setName("Aditi").setKey("tel:+15550000000").build())
        val uri = message(Person.Builder().setName("Aditi").setUri("tel:+15550000000").build())
        assertNotEquals(inspect(keyed)!!.observation.identityKey, inspect(uri)!!.observation.identityKey)
    }

    @Test fun `stable recipient still gets a new message fingerprint for a new timestamp`() {
        val before = inspect(styleMessage("same text", 1200), notificationId = 7)!!.observation
        val after = inspect(styleMessage("same text", 1300), notificationId = 8)!!.observation
        assertEquals(before.identityKey, after.identityKey)
        assertNotEquals(before.id, after.id)
    }

    @Test fun `group conversations do not collapse into a single sender fingerprint`() {
        fun group(title: String) = builder().setStyle(Notification.MessagingStyle(self)
            .setConversationTitle(title).setGroupConversation(true).addMessage("same text", 1200, person)).build()
        assertNotEquals(inspect(group("First"), notificationId = 7)!!.observation.id,
            inspect(group("Second"), notificationId = 7)!!.observation.id)
    }

    @Test fun `explicit self URI is ignored even when display name changes`() {
        val local = Person.Builder().setName("You").setUri("tel:+15550000000").build()
        val outgoing = Person.Builder().setName("Me").setUri("tel:+15550000000").build()
        val style = Notification.MessagingStyle(local).addMessage("my reply", 1200, outgoing)
        assertNull(inspect(builder().setStyle(style).build()))
    }

    @Test fun `reposting identical message retains fingerprint`() {
        val notification = styleMessage("hello", 1200)
        assertEquals(inspect(notification, postTime = 1400)!!.observation.id,
            inspect(notification, postTime = 9000)!!.observation.id)
    }

    @Test fun `new identical text with a new message timestamp is distinct`() {
        assertNotEquals(inspect(styleMessage("hello", 1200))!!.observation.id,
            inspect(styleMessage("hello", 1300))!!.observation.id)
    }

    @Test fun `fallback no timestamp deduplicates notification updates`() {
        val notification = builder().setContentTitle("Aditi").setContentText("hello").build()
        assertEquals(inspect(notification, postTime = 100)!!.observation.id,
            inspect(notification, postTime = 200)!!.observation.id)
    }

    @Test fun `conversation notification keys isolate identical fallback messages`() {
        val notification = builder().setContentTitle("Aditi").setContentText("hello").build()
        assertNotEquals(inspect(notification, notificationId = 1)!!.observation.id,
            inspect(notification, notificationId = 2)!!.observation.id)
    }

    @Test fun `group summary never becomes message`() {
        assertNull(inspect(builder().setGroup("messages").setGroupSummary(true)
            .setContentText("2 messages").build()))
    }

    @Test fun `group conversation retains group flag and actual sender`() {
        val style = Notification.MessagingStyle(self).setConversationTitle("Friends")
            .setGroupConversation(true).addMessage("hello", 1200, person)
        val result = inspect(builder().setStyle(style).build())!!
        assertTrue(result.observation.isGroup)
        assertEquals("Aditi", result.observation.sender)
        assertEquals("Friends", result.observation.conversationHint)
    }

    @Test fun `unknown packages and unrelated category ignored`() {
        assertNull(inspect(styleMessage("hello", 1200), packageName = "com.example.other"))
        assertNull(inspect(builder().setCategory(Notification.CATEGORY_SERVICE).setContentText("syncing").build()))
    }

    @Test fun `ongoing status notifications are ignored`() {
        assertNull(inspect(builder().setOngoing(true).setContentText("Checking for new messages").build()))
    }

    @Test fun `generic hidden preview is not treated as message text or identity`() {
        val result = inspect(builder().setContentTitle("WhatsApp").setContentText("3 new messages").build())!!
        assertNull(result.observation.sender)
        assertNull(result.observation.preview)
    }

    @Test fun `clear title and plain text fallback are exposed without inventing style`() {
        val result = inspect(builder().setContentTitle("Aditi").setContentText("hey").build())!!
        assertEquals("Aditi", result.observation.sender)
        assertEquals("hey", result.observation.preview)
        assertFalse(result.observation.isMessagingStyle)
    }

    @Test fun `message text retained is bounded even for oversized previews`() {
        val notification = builder().setContentTitle("Aditi").setContentText("short").build()
        // Builder truncation varies by Android version. Inject oversized source extras to
        // validate VEIL's own boundary rather than expecting the framework to retain 8K.
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, "a".repeat(8000))
        assertEquals(4096, inspect(notification)!!.observation.preview!!.length)
        assertTrue(inspect(styleMessage("a".repeat(8000), 1200))!!.observation.preview!!.length <= 4096)
    }

    @Test fun `foreign content intent cannot become conversation open handle`() {
        val notification = builder().setContentTitle("Aditi").setContentText("hello")
            .setContentIntent(PendingIntent.getActivity(context, 60,
                Intent("test").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)).build()
        assertNull(inspect(notification)!!.contentIntent)
    }

    @Test fun `reply selection requires explicit semantic reply and freeform text`() {
        assertNotNull(NotificationInspector.selectReply(arrayOf(action()), context.packageName))
        assertNull(NotificationInspector.selectReply(arrayOf(action(semantic = Notification.Action.SEMANTIC_ACTION_NONE)), context.packageName))
        assertNull(NotificationInspector.selectReply(arrayOf(action(freeform = false)), context.packageName))
    }

    @Test fun `foreign immutable and ambiguous reply actions are rejected`() {
        assertNull(NotificationInspector.selectReply(arrayOf(action()), "com.untrusted"))
        assertNull(NotificationInspector.selectReply(arrayOf(action(immutable = true)), context.packageName))
        assertNull(NotificationInspector.selectReply(arrayOf(action(), action()), context.packageName))
    }

    @Test fun `an activity PendingIntent is not used as a background reply action`() {
        val intent = PendingIntent.getActivity(context, 50, Intent("test").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
        val action = Notification.Action.Builder(null, "Reply", intent)
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(RemoteInput.Builder("reply").build()).build()
        assertNull(NotificationInspector.selectReply(arrayOf(action), context.packageName))
    }

    @Test fun `multiple freeform result keys are ambiguous`() {
        val action = Notification.Action.Builder(null, "Reply", pending())
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(RemoteInput.Builder("reply-a").build())
            .addRemoteInput(RemoteInput.Builder("reply-b").build()).build()
        assertNull(NotificationInspector.selectReply(arrayOf(action), context.packageName))
    }

    @Test fun `hash uses unambiguous length framing and does not reveal input`() {
        val first = NotificationInspector.digest("a", "bc")
        assertEquals(64, first.length)
        assertNotEquals(first, NotificationInspector.digest("ab", "c"))
        assertFalse(first.contains("hello"))
    }

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_email).setCategory(Notification.CATEGORY_MESSAGE)

    private fun styleMessage(text: String, at: Long) = builder()
        .setStyle(Notification.MessagingStyle(self).setGroupConversation(false).addMessage(text, at, person)).build()

    private fun inspect(notification: Notification, packageName: String = MessagingApp.WHATSAPP.packageName,
        notificationId: Int = 7, postTime: Long = 2000) = NotificationInspector.inspect(
        StatusBarNotification(packageName, packageName, notificationId, null, 10001, 0, 0, notification, android.os.Process.myUserHandle(), postTime)
    )

    private fun pending(immutable: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context, if (immutable) 2 else 1, Intent("dev.veil.TEST").setPackage(context.packageName),
        if (immutable) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_MUTABLE,
    )

    private fun action(semantic: Int = Notification.Action.SEMANTIC_ACTION_REPLY, freeform: Boolean = true,
        immutable: Boolean = false) = Notification.Action.Builder(null, "Reply", pending(immutable))
        .setSemanticAction(semantic).addRemoteInput(RemoteInput.Builder("reply").setAllowFreeFormInput(freeform).build()).build()
}
