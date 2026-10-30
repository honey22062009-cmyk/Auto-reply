package dev.veil.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.ai.*
import dev.veil.app.data.*
import dev.veil.app.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

/** Real persistence/coordinator pipeline; AI and IPC are synthetic and never use a network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AssistantCoordinatorTest {
    private lateinit var database:VeilDatabase
    private lateinit var repository:VeilRepository
    private lateinit var coordinator:AssistantCoordinator
    private lateinit var previousZone:TimeZone
    private var clock=Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
    private var preferences=VeilPreferences(onboarded=true,enabledApps=setOf(MessagingApp.WHATSAPP))
    private var key:String?="synthetic-not-real-provider-key"
    private var cloudCalls=0
    private val requests=mutableListOf<GenerationRequest>()
    private val dispatched=mutableListOf<Pair<String,Boolean>>()
    private var responder:suspend (GenerationRequest)->GenerationResult = { success() }
    private val identity="opaque-recipient"

    @Before fun setup() = runBlocking {
        previousZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),VeilDatabase::class.java)
            .allowMainThreadQueries().build()
        repository=VeilRepository(database,OpaqueCipher(),{DispatchResult.Accepted},{},{},{clock},
            replyDispatch={ id,_,automatic -> dispatched+=id to automatic; DispatchResult.Accepted })
        repository.initialize();repository.configure(preferences)
        coordinator=AssistantCoordinator(repository,{preferences},{key},engineFactory={_,_ -> object:ReplyEngineV1 {
            override suspend fun generate(request:GenerationRequest):GenerationResult {
                cloudCalls++;requests+=request;return responder(request)
            }
        }},now={clock})
    }
    @After fun teardown() {database.close();TimeZone.setDefault(previousZone)}

    @Test fun `default offline suggestions do not connect to a provider or send to unknown contacts`() = runBlocking {
        process(incoming("local"))
        assertEquals("TEMPLATE",repository.message("local")!!.engine)
        assertEquals("Hey!",repository.message("local")!!.suggestion)
        assertEquals(0,cloudCalls)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun `fresh live greeting completes the explicitly authorized automatic pipeline once`() = runBlocking {
        verifiedContact();update(preferences.copy(automationEnabled=true))
        val message=incoming("live")
        process(message);coordinator.process(message)
        assertEquals(listOf("live" to true),dispatched)
        assertEquals("AUTO",repository.message("live")!!.dispatchKind)
        assertEquals(0,cloudCalls)
    }

    @Test fun `active notification replay uses local generation and never invokes cloud or automation`() = runBlocking {
        verifiedContact();enableCloud(automation=true)
        process(incoming("replay").copy(fromActiveReplay=true))
        assertEquals(0,cloudCalls)
        assertTrue(dispatched.isEmpty())
        assertEquals("TEMPLATE",repository.message("replay")!!.engine)
    }

    @Test fun `messages older than coordinator session cannot trigger cloud or unattended replies`() = runBlocking {
        verifiedContact();enableCloud(automation=true)
        process(incoming("historical").copy(messageAt=clock-60_000))
        assertEquals(0,cloudCalls)
        assertTrue(dispatched.isEmpty())
        assertEquals("TEMPLATE",repository.message("historical")!!.engine)
    }

    @Test fun `explicit forced generation uses consented cloud but never sends automatically`() = runBlocking {
        verifiedContact();enableCloud(automation=true)
        val message=incoming("manual-generate")
        repository.record(message);coordinator.process(message,force=true)
        assertEquals(1,cloudCalls)
        assertTrue(dispatched.isEmpty())
        assertEquals("OPENAI",repository.message(message.id)!!.engine)
    }

    @Test fun `manual request can generate an old selected notification without authorizing a send`() = runBlocking {
        enableCloud()
        val message=incoming("old-selection").copy(messageAt=clock-60_000,fromActiveReplay=true)
        repository.record(message);coordinator.process(message,force=true)
        assertEquals(1,cloudCalls)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun `selecting cloud without consent or key falls back to a review-only local draft`() = runBlocking {
        verifiedContact()
        update(preferences.copy(automationEnabled=true,ai=AiConfiguration(provider=AiProvider.OPENAI,cloudConsent=false)))
        process(incoming("without-consent"))
        assertEquals("APPROVAL",repository.message("without-consent")!!.generationState)
        assertEquals("TEMPLATE",repository.message("without-consent")!!.engine)
        enableCloud(automation=true);key=null
        process(incoming("without-key"))
        assertEquals("APPROVAL",repository.message("without-key")!!.generationState)
        assertEquals(0,cloudCalls)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun `provider failure fallback cannot inherit unattended sending eligibility`() = runBlocking {
        verifiedContact();enableCloud(automation=true)
        responder={GenerationResult.Failure(AiFailureCode.NETWORK,"Synthetic provider unavailable")}
        process(incoming("failed-cloud"))
        assertEquals(1,cloudCalls)
        assertEquals("TEMPLATE",repository.message("failed-cloud")!!.engine)
        assertEquals("APPROVAL",repository.message("failed-cloud")!!.generationState)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun `credential related incoming content never reaches a provider including preview`() = runBlocking {
        enableCloud()
        process(incoming("credential","Your verification code is 123456"))
        assertNull(repository.message("credential")!!.observation.preview)
        assertNull(repository.message("credential")!!.suggestion)
        val preview=coordinator.preview("My password is secret")
        assertTrue(preview is GenerationResult.Failure)
        assertEquals(AiFailureCode.SENSITIVE_INPUT,(preview as GenerationResult.Failure).code)
        assertEquals(0,cloudCalls)
    }

    @Test fun `session cloud generation cap stops new requests without retrying or sending`() = runBlocking {
        enableCloud()
        repeat(21) { process(incoming("volume-$it")) }
        assertEquals(20,cloudCalls)
        assertEquals("TEMPLATE",repository.message("volume-20")!!.engine)
        assertEquals("APPROVAL",repository.message("volume-20")!!.generationState)
        assertTrue(dispatched.isEmpty())
        clock+=3_600_001
        process(incoming("after-window"))
        assertEquals(21,cloudCalls)
    }

    @Test fun `duplicate concurrent callbacks claim generation only once`() = runBlocking {
        enableCloud()
        responder={delay(20);success()}
        val message=incoming("duplicate")
        repository.record(message)
        coroutineScope {List(8){async{coordinator.process(message)}}.awaitAll()}
        assertEquals(1,cloudCalls)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun `selected context stays within the bound recipient and explicit opt in`() = runBlocking {
        enableCloud()
        repeat(8) { index -> repository.record(incoming("context-$index","Context message $index").copy(postedAt=clock-index-1)) }
        repository.record(incoming("another-person","Other private conversation").copy(identityKey="another-recipient"))
        process(incoming("context-off"))
        assertTrue(requests.last().context.isEmpty())
        update(preferences.copy(ai=preferences.ai.copy(includeContext=true)))
        process(incoming("context-on"))
        val context=requests.last().context
        assertTrue(context.isNotEmpty())
        assertTrue(context.size<=6)
        assertFalse(context.any { it.text.contains("Other private conversation") })
    }

    @Test fun `pausing during generation prevents its later automatic dispatch`() = runBlocking {
        verifiedContact();enableCloud(automation=true)
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        responder={started.complete(Unit);release.await();success()}
        val message=incoming("pause-inflight");repository.record(message)
        val job=async {coordinator.process(message)}
        started.await()
        update(preferences.copy(capturePaused=true))
        release.complete(Unit);job.await()
        assertTrue(dispatched.isEmpty())
        assertNull(database.dao().findAttempt(message.id))
    }

    @Test fun `cancelled generation records interruption without a dispatch or automatic retry`() = runBlocking {
        enableCloud()
        val started=CompletableDeferred<Unit>()
        responder={started.complete(Unit);awaitCancellation()}
        val message=incoming("cancelled");repository.record(message)
        val job=launch {coordinator.process(message)}
        started.await();job.cancelAndJoin()
        assertEquals("FAILED",repository.message(message.id)!!.generationState)
        assertNull(database.dao().findAttempt(message.id))
        coordinator.process(message)
        assertEquals(1,cloudCalls)
        assertTrue(dispatched.isEmpty())
    }

    private suspend fun update(value:VeilPreferences) {preferences=value;repository.configure(value)}
    private suspend fun enableCloud(automation:Boolean=false) = update(preferences.copy(automationEnabled=automation,
        ai=AiConfiguration(provider=AiProvider.OPENAI,cloudConsent=true)))
    private suspend fun process(message:ObservedNotification) {repository.record(message);coordinator.process(message)}
    private fun incoming(id:String,text:String="Hi")=ObservedNotification(id,"notification-key",MessagingApp.WHATSAPP,
        "Aditi",text,clock,clock,true,false,1,true,identityKey=identity)
    private suspend fun verifiedContact() {
        repository.addContact("Friend",MessagingApp.WHATSAPP,"Aditi")
        val id=repository.contactSnapshot().single().id
        repository.record(incoming("verification"));repository.sendTest("verification");repository.confirmReceipt("verification")
        repository.bindContact(id,"verification")
        repository.saveContact(repository.contactSnapshot().single().copy(autoEnabled=true,cooldownSeconds=0,
            quietEnabled=false,maxPerHour=10,maxPerDay=30,maxPerConversation=10))
        clock+=1000
    }
    private fun success()=GenerationResult.Success("Hey!",AiProvider.OPENAI,"synthetic-engine",requiresApproval=false)
    private class OpaqueCipher:TextCipher {
        private val values=mutableMapOf<String,String>()
        override fun encrypt(value:String?):String?=value?.let{"opaque-${values.size}".also{token->values[token]=it}}
        override fun decrypt(value:String?):String?=values[value]
    }
}
