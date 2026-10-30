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
import java.util.concurrent.atomic.AtomicInteger

/** Real local persistence and synthetic engines; no network request or real dispatch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantCoordinatorCancellationTest {
    private lateinit var db: VeilDatabase
    private lateinit var repository: VeilRepository
    private val clock = 1_800_000_000_000L
    private val configured = VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP),
        ai = AiConfiguration(provider = AiProvider.OPENAI, cloudConsent = true))

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VeilDatabase::class.java)
            .allowMainThreadQueries().build()
        val cipher = object : TextCipher {
            override fun encrypt(value: String?) = value?.let { "fixture:$it" }
            override fun decrypt(value: String?) = value?.removePrefix("fixture:")
        }
        repository = VeilRepository(db, cipher, { DispatchResult.Accepted }, {}, {}, { clock })
        repository.configure(configured)
    }
    @After fun close() { db.close() }

    @Test fun `revocation cancels active preview and a later request can run`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val coordinator = AssistantCoordinator(repository, { configured }, { "synthetic-key-not-real" }, { _, _ ->
            object : ReplyEngineV1 {
                override suspend fun generate(request: GenerationRequest): GenerationResult {
                    if (calls.incrementAndGet() == 1) { started.complete(Unit); awaitCancellation() }
                    return reply()
                }
            }
        }, { clock })
        val request = launch { coordinator.preview("hi") }
        withTimeout(5_000) { started.await() }
        coordinator.cancelPending()
        request.join()
        assertTrue(request.isCancelled)
        assertTrue(coordinator.preview("hi") is GenerationResult.Success)
        assertEquals(2, calls.get())
    }

    @Test fun `workers survive revocation and ignore queued work from the earlier epoch`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val coordinator = AssistantCoordinator(repository, { configured }, { "synthetic-key-not-real" }, { _, _ ->
            object : ReplyEngineV1 {
                override suspend fun generate(request: GenerationRequest): GenerationResult {
                    val count = calls.incrementAndGet()
                    if (count <= 2) { if (count == 2) started.complete(Unit); awaitCancellation() }
                    return reply()
                }
            }
        }, { clock })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            coordinator.start(scope)
            for (id in listOf("first", "second")) {
                val observation = message(id)
                repository.record(observation); coordinator.enqueue(observation)
            }
            withTimeout(10_000) { started.await() }
            val stale = message("queued-before-revocation")
            repository.record(stale); coordinator.enqueue(stale)
            coordinator.cancelPending()
            val fresh = message("fresh-after-revocation")
            repository.record(fresh); coordinator.enqueue(fresh)
            withTimeout(10_000) {
                while (repository.message(fresh.id)?.generationState != "SUGGESTED") delay(10)
            }
            assertEquals("NONE", repository.message(stale.id)!!.generationState)
            assertEquals(3, calls.get())
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }

    @Test fun `latest consent is checked after the initial preview snapshot`() = runBlocking {
        var preferenceReads = 0
        var calls = 0
        val coordinator = AssistantCoordinator(repository, {
            if (++preferenceReads == 1) configured else configured.copy(ai = configured.ai.copy(cloudConsent = false))
        }, { "synthetic-key-not-real" }, { _, _ ->
            object : ReplyEngineV1 {
                override suspend fun generate(request: GenerationRequest): GenerationResult { calls++; return reply() }
            }
        }, { clock })
        val result = coordinator.preview("hi") as GenerationResult.Failure
        assertEquals(AiFailureCode.CONSENT_REQUIRED, result.code)
        assertEquals(0, calls)
    }

    @Test fun `automatic replay stays local but explicit manual regeneration may use selected cloud`() = runBlocking {
        var calls = 0
        val coordinator = AssistantCoordinator(repository, { configured }, { "synthetic-key-not-real" }, { _, _ ->
            object : ReplyEngineV1 {
                override suspend fun generate(request: GenerationRequest): GenerationResult { calls++; return reply() }
            }
        }, { clock })
        val retained = message("retained").copy(fromActiveReplay = true)
        repository.record(retained)
        coordinator.process(retained)
        assertEquals(0, calls)
        assertEquals("TEMPLATE", repository.message(retained.id)!!.engine)
        coordinator.process(retained, force = true)
        assertEquals(1, calls)
        assertEquals("OPENAI", repository.message(retained.id)!!.engine)
        assertNull(repository.message(retained.id)!!.dispatchAttemptedAt)
    }

    private fun reply() = GenerationResult.Success("Hey!", AiProvider.OPENAI, "synthetic-model", requiresApproval = false)
    private fun message(id: String) = ObservedNotification(id, "key-$id", MessagingApp.WHATSAPP,
        "Synthetic contact", "hi", clock, clock, true, false, 1, true, identityKey = "synthetic-identity")
}
