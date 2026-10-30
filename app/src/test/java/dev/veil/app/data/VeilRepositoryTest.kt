package dev.veil.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Real Room SQL/transactions plus a fake platform dispatcher; no messages leave these tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VeilRepositoryTest {
    private lateinit var db: VeilDatabase
    private lateinit var repository: VeilRepository
    private lateinit var cipher: OpaqueTestCipher
    private var clock = 1_800_000_000_000L
    private var dispatchCount = 0
    private var cleared = 0
    private val forgotten = mutableListOf<String>()
    private val enabled = VeilPreferences(onboarded = true, enabledApps = MessagingApp.entries.toSet())

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VeilDatabase::class.java).allowMainThreadQueries().build()
        cipher = OpaqueTestCipher()
        repository = createRepository()
        repository.configure(enabled)
        repository.initialize()
    }

    @After fun tearDown() { db.close() }

    @Test fun `duplicate callbacks update capability without duplicating message or events`() = runBlocking {
        val incoming = observation()
        repository.record(incoming)
        repository.record(incoming.copy(hasReplyAction = false, actionCount = 0, postedAt = clock + 100))
        assertEquals(1, db.dao().messageCount())
        assertEquals(2, db.dao().eventCount())
        val updated = repository.messages.first().single()
        assertEquals(ReplyStatus.UNAVAILABLE, updated.status)
        assertFalse(updated.observation.hasReplyAction)
        assertEquals(incoming.postedAt, updated.observation.postedAt)
        assertEquals(0, dispatchCount)
    }

    @Test fun `attempt is committed before dispatcher runs and accepted is not receipt`() = runBlocking {
        repository = createRepository { id ->
            val durable = db.dao().find(id)!!
            assertEquals(ReplyStatus.DISPATCHING.name, durable.status)
            assertEquals(clock, durable.attemptedAt)
            assertNull(durable.acceptedAt)
            DispatchResult.Accepted
        }
        repository.configure(enabled)
        repository.record(observation())
        repository.sendTest("message-1")
        val row = repository.messages.first().single()
        assertEquals(ReplyStatus.ACCEPTED, row.status)
        assertNotNull(row.dispatchAttemptedAt)
        assertNotNull(row.dispatchAcceptedAt)
        assertNull(row.receiptReportedAt)
        assertEquals(1, dispatchCount)
    }

    @Test fun `simultaneous taps send exactly once`() = runBlocking {
        repository.record(observation())
        coroutineScope { List(8) { async { repository.sendTest("message-1") } }.awaitAll() }
        assertEquals(1, dispatchCount)
        assertEquals(ReplyStatus.ACCEPTED.name, db.dao().find("message-1")!!.status)
    }

    @Test fun `rejected and exceptional attempts never retry from an update or restart`() = runBlocking {
        for ((index, fail) in listOf<suspend (String) -> DispatchResult>(
            { DispatchResult.Rejected("Action expired.") },
            { throw IllegalStateException("private exception detail") },
        ).withIndex()) {
            val incoming = observation(id = "failure-$index", key = "key-$index")
            repository = createRepository(fail)
            repository.configure(enabled)
            repository.record(incoming)
            repository.sendTest(incoming.id)
            repository.record(incoming)
            repository.sendTest(incoming.id)
            repository = createRepository()
            repository.configure(enabled)
            repository.initialize()
            repository.record(incoming)
            repository.sendTest(incoming.id)
            val row = db.dao().find(incoming.id)!!
            assertEquals(ReplyStatus.FAILED.name, row.status)
            assertNotNull(row.attemptedAt)
            assertNull(row.acceptedAt)
            assertFalse(row.failureReason.orEmpty().contains("private exception detail"))
        }
        assertEquals(2, dispatchCount)
    }

    @Test fun `persisted uncertain dispatch stays blocked after restoration`() = runBlocking {
        repository.record(observation())
        val original = db.dao().find("message-1")!!
        db.dao().updateMessage(original.copy(status = ReplyStatus.DISPATCHING.name, attemptedAt = clock))
        repository = createRepository()
        repository.configure(enabled)
        repository.initialize()
        assertFalse(db.dao().find("message-1")!!.active)
        repository.record(observation())
        repository.sendTest("message-1")
        assertEquals(0, dispatchCount)
        assertEquals(ReplyStatus.DISPATCHING.name, db.dao().find("message-1")!!.status)
    }

    @Test fun `cancellation preserves unknown outcome and durable attempt instead of claiming failure`() = runBlocking {
        repository = createRepository { throw CancellationException("Activity closed during dispatch") }
        repository.configure(enabled)
        repository.record(observation())
        var cancelled = false
        try { repository.sendTest("message-1") } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        val row = db.dao().find("message-1")!!
        assertEquals(ReplyStatus.DISPATCHING.name, row.status)
        assertNotNull(row.attemptedAt)
        assertNull(row.acceptedAt)
        assertNull(row.failureReason)
        repository = createRepository()
        repository.configure(enabled)
        repository.record(observation())
        repository.sendTest("message-1")
        assertEquals(1, dispatchCount)
    }

    @Test fun `Room file persists deduplication and attempt across database reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "veil-state-${UUID.randomUUID()}.db"
        db.close()
        try {
            db = Room.databaseBuilder(context, VeilDatabase::class.java, name).allowMainThreadQueries().build()
            repository = createRepository()
            repository.configure(enabled)
            repository.record(observation())
            repository.sendTest("message-1")
            db.close()
            db = Room.databaseBuilder(context, VeilDatabase::class.java, name).allowMainThreadQueries().build()
            repository = createRepository()
            repository.initialize()
            repository.configure(enabled)
            assertFalse(db.dao().find("message-1")!!.active)
            repository.record(observation())
            repository.sendTest("message-1")
            assertEquals(1, db.dao().messageCount())
            assertEquals(1, dispatchCount)
            assertNotNull(db.dao().find("message-1")!!.acceptedAt)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun `replacement invalidates old message and removal blocks current message`() = runBlocking {
        repository.record(observation())
        repository.record(observation(id = "new-message", preview = "new question"))
        assertFalse(db.dao().find("message-1")!!.active)
        assertTrue(db.dao().find("new-message")!!.active)
        repository.sendTest("message-1")
        assertEquals(0, dispatchCount)
        repository.removed("key-hash")
        repository.sendTest("new-message")
        assertFalse(db.dao().find("new-message")!!.active)
        assertEquals(ReplyStatus.REMOVED.name, db.dao().find("new-message")!!.status)
        assertEquals(0, dispatchCount)
    }

    @Test fun `separate notification keys retain independent current conversations`() = runBlocking {
        repository.record(observation())
        repository.record(observation(id = "message-2", key = "other-key"))
        repository.removed("key-hash")
        assertFalse(db.dao().find("message-1")!!.active)
        assertTrue(db.dao().find("message-2")!!.active)
        repository.sendTest("message-2")
        assertEquals(1, dispatchCount)
    }

    @Test fun `same message moving to a new notification key follows the new removal`() = runBlocking {
        repository.record(observation())
        repository.record(observation(key = "replacement-key"))
        assertEquals(1, db.dao().messageCount())
        repository.removed("replacement-key")
        assertFalse(db.dao().find("message-1")!!.active)
        repository.sendTest("message-1")
        assertEquals(0, dispatchCount)
    }

    @Test fun `disabled packages onboarding and pause reject capture and sends`() = runBlocking {
        repository.configure(VeilPreferences())
        repository.record(observation())
        assertEquals(0, db.dao().messageCount())
        repository.configure(enabled.copy(enabledApps = setOf(MessagingApp.INSTAGRAM)))
        repository.record(observation())
        assertEquals(0, db.dao().messageCount())
        repository.configure(enabled)
        repository.record(observation())
        repository.configure(enabled.copy(capturePaused = true))
        repository.record(observation(id = "during-pause"))
        repository.sendTest("message-1")
        assertEquals(1, db.dao().messageCount())
        assertFalse(db.dao().find("message-1")!!.active)
        assertEquals(0, dispatchCount)
    }

    @Test fun `disconnect removes live authority without erasing evidence of accepted dispatch`() = runBlocking {
        repository.record(observation())
        repository.sendTest("message-1")
        repository.connectionChanged(false)
        val row = db.dao().find("message-1")!!
        assertFalse(row.active)
        assertEquals(ReplyStatus.ACCEPTED.name, row.status)
        assertNotNull(row.acceptedAt)
        repository.connectionChanged(true)
        assertFalse(db.dao().find("message-1")!!.active)
    }

    @Test fun `dismissed message remains blocked after duplicate callback`() = runBlocking {
        repository.record(observation())
        repository.dismiss("message-1")
        assertEquals(listOf("message-1"), forgotten)
        repository.record(observation())
        repository.sendTest("message-1")
        assertEquals(ReplyStatus.DISMISSED.name, db.dao().find("message-1")!!.status)
        assertEquals(0, dispatchCount)
    }

    @Test fun `receipt is only user reported after an accepted test and is idempotent`() = runBlocking {
        repository.record(observation())
        repository.confirmReceipt("message-1")
        assertNull(db.dao().find("message-1")!!.receiptReportedAt)
        repository.sendTest("message-1")
        repository.confirmReceipt("message-1")
        val eventCount = db.dao().eventCount()
        clock += 5_000
        repository.confirmReceipt("message-1")
        assertEquals(eventCount, db.dao().eventCount())
        val event = repository.activity.first().first()
        assertEquals("Receipt reported by user", event.title)
        assertTrue(event.detail.contains("Not independently verified"))
    }

    @Test fun `retention removes old content and rejects stale notification replay`() = runBlocking {
        repository.configure(enabled.copy(retentionDays = 7))
        repository.record(observation(postedAt = clock - 3 * 86_400_000L))
        assertEquals(1, db.dao().messageCount())
        repository.configure(enabled.copy(retentionDays = 1))
        assertEquals(0, db.dao().messageCount())
        repository.record(observation(postedAt = clock - 2 * 86_400_000L))
        assertEquals(0, db.dao().messageCount())
        repository.record(observation())
        clock += 2 * 86_400_000L
        repository.configure(enabled)
        assertEquals(0, db.dao().messageCount())
        assertEquals(0, db.dao().eventCount())
    }

    @Test fun `plaintext content never enters Room fields and local contacts require manual entry`() = runBlocking {
        val incoming = observation().copy(conversationHint = "private conversation identifier")
        repository.record(incoming)
        val row = db.dao().find(incoming.id)!!
        assertNotEquals(incoming.sender, row.sender)
        assertNotEquals(incoming.preview, row.preview)
        assertNotEquals(incoming.conversationHint, row.conversation)
        val decoded = repository.messages.first().single().observation
        assertEquals(incoming.sender, decoded.sender)
        assertEquals(incoming.preview, decoded.preview)
        assertEquals(0, db.dao().contacts().size)
        repository.addContact("  Best friend  ", MessagingApp.WHATSAPP, "  Aditi  ")
        val contact = db.dao().contacts().single()
        assertNotEquals("Best friend", contact.label)
        assertNotEquals("Aditi", contact.sender)
        assertEquals("Best friend", repository.contacts.first().single().label)
        repository.addContact("Duplicate", MessagingApp.WHATSAPP, "aditi")
        assertEquals(1, db.dao().contacts().size)
        repository.addContact("Other platform", MessagingApp.INSTAGRAM, "Aditi")
        assertEquals(2, db.dao().contacts().size)
        repository.removeContact(contact.id)
        assertEquals(1, db.dao().contacts().size)
    }

    @Test fun `delete history clears message events and live actions but keeps contact choices`() = runBlocking {
        repository.record(observation())
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi")
        repository.deleteHistory()
        assertEquals(0, db.dao().messageCount())
        assertEquals(0, db.dao().eventCount())
        assertEquals(1, cleared)
        assertEquals(1, db.dao().contacts().size)
    }

    @Test fun `encryption failure rolls back notification and event persistence`() = runBlocking {
        val broken = object : TextCipher {
            override fun encrypt(value: String?): String? = error("Keystore unavailable")
            override fun decrypt(value: String?): String? = null
        }
        val guarded = VeilRepository(db, broken, { DispatchResult.Accepted }, {}, {}, { clock })
        guarded.configure(enabled)
        assertTrue(runCatching { guarded.record(observation()) }.isFailure)
        assertEquals(0, db.dao().messageCount())
        assertEquals(0, db.dao().eventCount())
    }

    private fun createRepository(action: suspend (String) -> DispatchResult = { DispatchResult.Accepted }) =
        VeilRepository(db, cipher, { id -> dispatchCount++; action(id) }, forgotten::add, { cleared++ }, { clock })

    private fun observation(id: String = "message-1", key: String = "key-hash", preview: String = "Private test message",
        postedAt: Long = clock) = ObservedNotification(id, key, MessagingApp.WHATSAPP, "Aditi", preview,
        postedAt, postedAt, isMessagingStyle = true, isGroup = false, actionCount = 1, hasReplyAction = true)

    private class OpaqueTestCipher : TextCipher {
        private val values = mutableMapOf<String, String>()
        override fun encrypt(value: String?): String? = value?.let {
            val token = "opaque-${values.size}"; values[token] = it; token
        }
        override fun decrypt(value: String?) = values[value]
    }
}
