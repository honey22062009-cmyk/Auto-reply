package dev.veil.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.ai.*
import dev.veil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

/** Exercises the actual Room/policy/reservation boundary with synthetic recipients and fake IPC. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantRepositoryTest {
    private lateinit var database: VeilDatabase
    private lateinit var repository: VeilRepository
    private lateinit var cipher: OpaqueCipher
    private lateinit var previousZone: TimeZone
    private var clock = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
    private var testDispatches = 0
    private val dispatches = mutableListOf<Triple<String,String,Boolean>>()
    private val identity = "opaque-stable-recipient"
    private val enabled = VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP), automationEnabled = true)

    @Before fun setup() = runBlocking {
        previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VeilDatabase::class.java)
            .allowMainThreadQueries().build()
        cipher = OpaqueCipher()
        repository = createRepository()
        repository.initialize(); repository.configure(enabled)
    }

    @After fun teardown() { database.close(); TimeZone.setDefault(previousZone) }

    @Test fun `manual reply commits exact edited text and one shot ledger before IPC`() = runBlocking {
        repository.record(incoming("manual"))
        val reviewed = "Hey!\nA reply I explicitly reviewed."
        repository.sendReply("manual", reviewed)
        repository.sendReply("manual", "a second send")
        repository.sendTest("manual")
        assertEquals(listOf(Triple("manual", reviewed, false)), dispatches)
        assertEquals(0, testDispatches)
        val row = database.dao().find("manual")!!
        assertEquals("MANUAL", row.dispatchKind)
        assertEquals(ReplyStatus.ACCEPTED.name, row.status)
        assertEquals(reviewed, repository.message("manual")!!.suggestion)
        assertNotEquals(reviewed, row.suggestion)
        assertNull(row.receiptReportedAt)
    }

    @Test fun `compatibility and normal replies share durable duplicate protection after history deletion`() = runBlocking {
        repository.record(incoming("test")); repository.sendTest("test")
        repository.deleteHistory()
        repository.record(incoming("test"))
        repository.sendReply("test", "Hey!")
        repository.sendTest("test")
        assertEquals(1, testDispatches)
        assertTrue(dispatches.isEmpty())
        assertEquals("TEST", database.dao().findAttempt("test")!!.kind)
    }

    @Test fun `simultaneous manual confirmations reserve exactly one attempt`() = runBlocking {
        repository.record(incoming("concurrent"))
        coroutineScope { List(8) { async { repository.sendReply("concurrent", "Hey!") } }.awaitAll() }
        assertEquals(1, dispatches.size)
        assertEquals(1, database.dao().attemptsSince(0).size)
    }

    @Test fun `failed manual dispatch preserves one shot ledger and redacts exception payload`() = runBlocking {
        repository=createRepository { _,_,_ -> throw IllegalStateException("private provider secret in exception") }
        repository.configure(enabled)
        repository.record(incoming("manual-failure"))
        repository.sendReply("manual-failure","Hey!")
        repository.sendReply("manual-failure","Hey!")
        val row=database.dao().find("manual-failure")!!
        assertEquals(ReplyStatus.FAILED.name,row.status)
        assertTrue(database.dao().findAttempt("manual-failure")!!.failed)
        assertFalse(row.failureReason.orEmpty().contains("private provider secret"))
        assertEquals(1,dispatches.size)
    }

    @Test fun `cancelled production dispatch stays uncertain and cannot retry after restoration`() = runBlocking {
        repository=createRepository { _,_,_ -> throw CancellationException("Synthetic interrupted IPC") }
        repository.configure(enabled)
        repository.record(incoming("interrupted-reply"))
        assertTrue(runCatching { repository.sendReply("interrupted-reply","Hey!") }.exceptionOrNull() is CancellationException)
        assertEquals(ReplyStatus.DISPATCHING.name,database.dao().find("interrupted-reply")!!.status)
        assertNull(database.dao().findAttempt("interrupted-reply")!!.acceptedAt)
        repository=createRepository()
        repository.initialize(); repository.configure(enabled)
        repository.record(incoming("interrupted-reply"))
        repository.sendReply("interrupted-reply","Hey!")
        assertEquals(1,dispatches.size)
    }

    @Test fun `generation claim is exclusive and interrupted state restores for manual retry`() = runBlocking {
        repository.record(incoming("draft"))
        val claims = coroutineScope { List(8) { async { repository.claimGeneration("draft") } }.awaitAll() }
        assertEquals(1, claims.count { it != null })
        repository.initialize()
        assertEquals("FAILED", repository.message("draft")!!.generationState)
        assertNull(repository.claimGeneration("draft"))
        assertNotNull(repository.claimGeneration("draft", force = true))
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `suggestion text is encrypted and review escalation is persisted independently of dispatch`() = runBlocking {
        repository.record(incoming("sensitive", text="Please transfer money"))
        repository.claimGeneration("sensitive")
        repository.storeGeneration("sensitive", GenerationResult.Success("Please let me review this.", AiProvider.TEMPLATE,
            "test-only", requiresApproval = true))
        val row = database.dao().find("sensitive")!!
        assertNotEquals("Please let me review this.", row.suggestion)
        assertEquals("Please let me review this.", repository.message("sensitive")!!.suggestion)
        assertEquals("APPROVAL", row.generationState)
        assertNull(row.attemptedAt)
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `credential notification preview is excluded from persistence and generation`() = runBlocking {
        repository.record(incoming("secret", text="Your verification code is 123456"))
        assertNull(database.dao().find("secret")!!.preview)
        assertNull(repository.claimGeneration("secret"))
        repository.sendReply("secret", "Hey!")
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `display name alone cannot enable automation or establish binding`() = runBlocking {
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi")
        val contact = repository.contactSnapshot().single()
        repository.record(incoming("name-only", stable = null))
        repository.bindContact(contact.id, "name-only")
        repository.saveContact(contact.copy(autoEnabled = true, compatibilityVerified = true, identityKey = "forged"))
        val stored = repository.contactSnapshot().single()
        assertNull(stored.identityKey)
        assertFalse(stored.autoEnabled)
        assertFalse(stored.compatibilityVerified)
    }

    @Test fun `account hints are encrypted and changing them revokes verified automatic permissions`() = runBlocking {
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi",
            whatsappPhone = "+919876543210")
        val initial = repository.contactSnapshot().single()
        assertEquals("+919876543210", initial.whatsappPhone)
        assertNotEquals("+919876543210", database.dao().findContact(initial.id)!!.whatsappPhone)
        assertNull(initial.identityKey)
        assertFalse(initial.autoEnabled)
        repository.record(incoming("verification-with-phone"))
        repository.sendTest("verification-with-phone")
        repository.confirmReceipt("verification-with-phone")
        repository.bindContact(initial.id, "verification-with-phone")
        assertTrue(repository.contactSnapshot().single().compatibilityVerified)
        repository.saveContact(repository.contactSnapshot().single().copy(autoEnabled = true))
        assertTrue(repository.contactSnapshot().single().autoEnabled)
        repository.saveContact(repository.contactSnapshot().single().copy(
            whatsappPhone = "+919876543211", autoEnabled = false))
        val edited = repository.contactSnapshot().single()
        assertEquals("+919876543211", edited.whatsappPhone)
        assertFalse(edited.autoEnabled)
        assertFalse(edited.compatibilityVerified)
        assertNull(edited.identityKey)
    }

    @Test fun `binding a different visible sender is rejected even when a stable key exists`() = runBlocking {
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi")
        val id = repository.contactSnapshot().single().id
        repository.record(incoming("wrong-sender").copy(sender = "Another account"))
        val result = repository.bindContact(id, "wrong-sender")
        assertTrue(result.contains("does not match"))
        assertNull(repository.contactSnapshot().single().identityKey)
    }

    @Test fun `instagram owner-provided username and id are encrypted but do not grant auto reply`() = runBlocking {
        repository.addContact("IG friend", MessagingApp.INSTAGRAM, "Priyanshi",
            instagramUsername = "@official.friend", instagramUserId = "9876543210")
        val contact = repository.contactSnapshot().single()
        assertEquals("official.friend", contact.instagramUsername)
        assertEquals("9876543210", contact.instagramUserId)
        assertNull(contact.identityKey)
        assertFalse(contact.autoEnabled)
        val stored = database.dao().findContact(contact.id)!!
        assertNotEquals("official.friend", stored.instagramUsername)
        assertNotEquals("9876543210", stored.instagramUserId)
    }

    @Test fun `binding requires actual test acceptance and separately reported receipt`() = runBlocking {
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi")
        val id = repository.contactSnapshot().single().id
        repository.record(incoming("binding"))
        repository.bindContact(id, "binding")
        assertFalse(repository.contactSnapshot().single().compatibilityVerified)
        repository.sendTest("binding")
        repository.bindContact(id, "binding")
        assertFalse(repository.contactSnapshot().single().compatibilityVerified)
        repository.confirmReceipt("binding")
        repository.bindContact(id, "binding")
        assertTrue(repository.contactSnapshot().single().compatibilityVerified)
        assertFalse(repository.contactSnapshot().single().autoEnabled)
    }

    @Test fun `manual dispatch receipt is not a compatibility test credential`() = runBlocking {
        repository.addContact("Friend", MessagingApp.WHATSAPP, "Aditi")
        repository.record(incoming("not-a-test")); repository.sendReply("not-a-test", "Hey!")
        repository.confirmReceipt("not-a-test")
        repository.bindContact(repository.contactSnapshot().single().id,"not-a-test")
        assertFalse(repository.contactSnapshot().single().compatibilityVerified)
    }

    @Test fun `verified explicit contact and global rule can dispatch a fresh greeting once`() = runBlocking {
        verifiedContact()
        repository.record(incoming("greeting"))
        repository.sendReply("greeting","Hey!",automatic = true,generatorRequiresApproval = false)
        repository.sendReply("greeting","Hey!",automatic = true,generatorRequiresApproval = false)
        assertEquals(listOf(Triple("greeting","Hey!",true)),dispatches)
        assertEquals("AUTO",database.dao().findAttempt("greeting")!!.kind)
    }

    @Test fun `sensitive unknown grouped and unanchored messages cannot auto dispatch`() = runBlocking {
        verifiedContact()
        val cases = listOf(
            incoming("money", text="Can you transfer money?"),
            incoming("unknown", text="Where did we meet last week?"),
            incoming("group").copy(isGroup=true),
            incoming("unanchored", stable=null),
            incoming("no-action").copy(hasReplyAction=false),
        )
        for (message in cases) {
            repository.record(message)
            repository.sendReply(message.id,"Hey!",automatic=true,generatorRequiresApproval=false)
            assertNull(database.dao().findAttempt(message.id))
        }
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `a fresh greeting cannot erase a recent financial conversation needing review`() = runBlocking {
        verifiedContact()
        repository.record(incoming("prior-financial",text="Please transfer money to my bank account"))
        clock+=1000
        repository.record(incoming("greeting-after-financial"))
        repository.sendReply("greeting-after-financial","Hey!",automatic=true,generatorRequiresApproval=false)
        assertTrue(dispatches.isEmpty())
        assertNull(database.dao().findAttempt("greeting-after-financial"))
        assertEquals("APPROVAL",repository.message("greeting-after-financial")!!.generationState)
    }

    @Test fun `global switch contact pause quiet hours and review warnings deny automation`() = runBlocking {
        var contact = verifiedContact()
        repository.configure(enabled.copy(automationEnabled=false))
        denyAutomatic("global-off")
        repository.configure(enabled)
        repository.saveContact(contact.copy(paused=true)); denyAutomatic("contact-paused")
        contact = repository.contactSnapshot().single()
        repository.saveContact(contact.copy(paused=false,quietEnabled=true,quietStartHour=0,quietEndHour=23))
        denyAutomatic("quiet")
        repository.saveContact(repository.contactSnapshot().single().copy(quietEnabled=false))
        repository.record(incoming("review"))
        repository.sendReply("review","Hey!",automatic=true,generatorRequiresApproval=true)
        assertNull(database.dao().findAttempt("review"))
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `cooldown and conversation cap include attempts across recreated local contact ids`() = runBlocking {
        val original = verifiedContact(maxConversation=2)
        repository.record(incoming("first-auto"))
        repository.sendReply("first-auto","Hey!",automatic=true,generatorRequiresApproval=false)
        assertEquals(1,dispatches.size)
        repository.removeContact(original.id)
        repository.addContact("Recreated",MessagingApp.WHATSAPP,"Aditi")
        val recreated = repository.contactSnapshot().single()
        repository.bindContact(recreated.id,"verification")
        repository.saveContact(repository.contactSnapshot().single().copy(autoEnabled=true,cooldownSeconds=0,
            quietEnabled=false,maxPerConversation=2,maxPerHour=10))
        denyAutomatic("second-auto")
        assertEquals(1,dispatches.size)
        assertNotEquals(original.id,recreated.id)
    }

    @Test fun `contact cooldown rejects rapid greetings even with available reply actions`() = runBlocking {
        verifiedContact()
        repository.saveContact(repository.contactSnapshot().single().copy(cooldownSeconds=120))
        // The successful compatibility attempt is deliberately counted conservatively.
        denyAutomatic("too-soon")
        clock += 121_000
        repository.record(incoming("after-cooldown"))
        repository.sendReply("after-cooldown","Hey!",automatic=true,generatorRequiresApproval=false)
        assertEquals(1,dispatches.size)
    }

    @Test fun `three failed automatic attempts suspend a further automatic dispatch`() = runBlocking {
        verifiedContact()
        repeat(3) { index -> database.dao().insertAttempt(ReplyAttemptEntity("old-failure-$index",null,
            "other-identity-$index",MessagingApp.WHATSAPP.name,"AUTO",clock-index-1,failed=true)) }
        denyAutomatic("after-failures")
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `unsafe manually edited text is rejected before recording or dispatching`() = runBlocking {
        for ((index,text) in listOf("", "a".repeat(1001), "safe\tunsafe", "safe\u202Eunsafe", "password:123456").withIndex()) {
            val id = "invalid-$index"; repository.record(incoming(id))
            repository.sendReply(id,text)
            assertNull(database.dao().findAttempt(id))
        }
        assertTrue(dispatches.isEmpty())
    }

    @Test fun `generation failure and cleared history never resurrect drafts or dispatch`() = runBlocking {
        repository.record(incoming("gone")); repository.claimGeneration("gone")
        repository.deleteHistory()
        repository.storeGeneration("gone", GenerationResult.Success("Hey!",AiProvider.TEMPLATE,"test"))
        assertNull(repository.message("gone"))
        assertTrue(dispatches.isEmpty())
    }

    private suspend fun denyAutomatic(id:String) {
        repository.record(incoming(id))
        repository.sendReply(id,"Hey!",automatic=true,generatorRequiresApproval=false)
        assertNull(database.dao().findAttempt(id))
    }

    private suspend fun verifiedContact(maxConversation:Int=10): PriorityContact {
        repository.addContact("Friend",MessagingApp.WHATSAPP,"Aditi")
        val id=repository.contactSnapshot().single().id
        repository.record(incoming("verification")); repository.sendTest("verification"); repository.confirmReceipt("verification")
        repository.bindContact(id,"verification")
        repository.saveContact(repository.contactSnapshot().single().copy(autoEnabled=true,cooldownSeconds=0,
            quietEnabled=false,maxPerConversation=maxConversation,maxPerHour=10,maxPerDay=30))
        clock += 1000
        return repository.contactSnapshot().single()
    }

    private fun incoming(id:String,text:String="Hi",stable:String?=identity) = ObservedNotification(id,"notification-key",
        MessagingApp.WHATSAPP,"Aditi",text,clock,clock,true,false,1,true,identityKey=stable)

    private fun createRepository(action:suspend (String,String,Boolean)->DispatchResult = { _,_,_ -> DispatchResult.Accepted }) = VeilRepository(database,cipher,{ testDispatches++; DispatchResult.Accepted },{},{},{ clock },
        replyDispatch={ id,text,automatic ->
            val durable=database.dao().findAttempt(id)!!
            assertEquals(if(automatic) "AUTO" else "MANUAL",durable.kind)
            assertNull(durable.acceptedAt)
            assertEquals(ReplyStatus.DISPATCHING.name,database.dao().find(id)!!.status)
            dispatches += Triple(id,text,automatic)
            action(id,text,automatic)
        })

    private class OpaqueCipher : TextCipher {
        private val values=mutableMapOf<String,String>()
        override fun encrypt(value:String?):String? = value?.let { "opaque-${values.size}".also { token -> values[token]=it } }
        override fun decrypt(value:String?):String? = values[value]
    }
}
