package dev.veil.app

import dev.veil.app.ai.*
import dev.veil.app.data.VeilRepository
import dev.veil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.ArrayDeque

/** Bounded workers keep notification callbacks responsive; all sends remain in the repository. */
class AssistantCoordinator(
    private val repository: VeilRepository,
    private val preferences: () -> VeilPreferences,
    private val apiKey: () -> String?,
    private val engineFactory: ((VeilPreferences, String?) -> ReplyEngineV1)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Pending(val observation: ObservedNotification, val epoch: Long, val automationAtCapture: Boolean)
    private val incoming = Channel<Pending>(64)
    private val cloudAttempts = ArrayDeque<Long>()
    private val sessionStartedAt = now()
    private val policyLock = Any()
    private var policyEpoch = 0L
    private val activeGenerations = mutableSetOf<Job>()

    /** Invalidate queued work and disconnect active transport through ordinary coroutine cancellation. */
    fun cancelPending() {
        val jobs = synchronized(policyLock) {
            policyEpoch++
            activeGenerations.toList()
        }
        jobs.forEach { it.cancel(CancellationException("VEIL generation settings changed")) }
    }

    fun start(scope: CoroutineScope) {
        repeat(2) {
            scope.launch {
                for (task in incoming) {
                    // Cancelling a request must not cancel the long-lived notification worker.
                    supervisorScope { launch { process(task, force = false) }.join() }
                }
            }
        }
    }

    fun enqueue(observed: ObservedNotification) {
        val prefs = preferences()
        incoming.trySend(Pending(observed, epoch(), prefs.automationEnabled))
    }

    suspend fun process(observed: ObservedNotification, force: Boolean = false) {
        process(Pending(observed, epoch(), preferences().automationEnabled), force)
    }

    private suspend fun process(task: Pending, force: Boolean) {
        if (!current(task.epoch)) return
        val observed = task.observation
        val record = repository.claimGeneration(observed.id, force) ?: return
        try {
            ensureCurrent(task.epoch)
            val prefs = preferences()
            val contacts = repository.contactSnapshot().filter {
                it.app == observed.app && it.identityKey != null && it.identityKey == observed.identityKey
            }
            val contact = contacts.singleOrNull()
            val context = if (prefs.ai.includeContext && !observed.fromActiveReplay)
                repository.context(observed.identityKey, observed.id) else emptyList()
            val request = GenerationRequest(record.observation.preview.orEmpty(), context, prefs.style, contact?.instruction.orEmpty())
            val replay = observed.fromActiveReplay || (observed.messageAt ?: 0L) < sessionStartedAt - 5_000L
            // A manual Generate action is fresh authorization even for retained history. It never auto-sends.
            var result = generate(request, prefs, preferLocal = replay && !force, expectedEpoch = task.epoch)
            if (result is GenerationResult.Success && observed.notificationContextNeedsReview) {
                result=result.copy(requiresApproval=true,warnings=result.warnings+"Earlier messages exposed in this notification need your personal review.")
            }
            ensureCurrent(task.epoch)
            if (result is GenerationResult.Failure && result.code !in setOf(AiFailureCode.SENSITIVE_INPUT, AiFailureCode.EMPTY_INPUT)) {
                val fallback = ManualTemplateEngine().generate(request)
                if (fallback is GenerationResult.Success) result = fallback.copy(requiresApproval = true,
                    warnings = fallback.warnings + "The selected engine was unavailable; this is an offline template requiring review.")
            }
            ensureCurrent(task.epoch)
            repository.storeGeneration(observed.id, result)
            ensureCurrent(task.epoch)
            if (!force && !replay && task.automationAtCapture && result is GenerationResult.Success && contacts.size == 1 &&
                preferences().automationEnabled && contact?.autoEnabled == true && !contact.paused) {
                repository.sendReply(observed.id, result.draft, automatic = true, generatorRequiresApproval = result.requiresApproval)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { repository.storeGeneration(observed.id,
                GenerationResult.Failure(AiFailureCode.INCOMPLETE, "Generation was interrupted. Generate again manually.")) }
            throw cancelled
        } catch (_: Exception) {
            repository.storeGeneration(observed.id, GenerationResult.Failure(AiFailureCode.NETWORK,
                "Generation could not complete. Review engine settings or write a reply manually."))
        }
    }

    suspend fun preview(text: String): GenerationResult {
        val captured = epoch()
        val prefs = preferences()
        return generate(GenerationRequest(text, style = prefs.style), prefs, expectedEpoch = captured)
    }

    private suspend fun generate(request: GenerationRequest, prefs: VeilPreferences,
        preferLocal: Boolean = false, expectedEpoch: Long): GenerationResult {
        val job = currentCoroutineContext().job
        synchronized(policyLock) {
            if (policyEpoch != expectedEpoch) throw CancellationException("VEIL generation settings changed")
            activeGenerations.add(job)
        }
        try {
            ensureCurrent(expectedEpoch)
            if (DraftSafety.containsCredentials(request.incoming)) return GenerationResult.Failure(AiFailureCode.SENSITIVE_INPUT,
                "Security-code and credential messages are not retained or sent to an AI provider. Reply directly in the messaging app.")
            if (preferLocal || prefs.ai.provider == AiProvider.TEMPLATE) return ManualTemplateEngine().generate(request)
            // Recheck after repository suspensions and before constructing the network engine.
            val latest = preferences()
            if (latest.ai != prefs.ai || latest.style != prefs.style || !latest.ai.cloudConsent || latest.ai.provider != AiProvider.OPENAI)
                return GenerationResult.Failure(AiFailureCode.CONSENT_REQUIRED, "AI settings changed. Review the current provider and sharing settings before generating again.")
            val key = apiKey()
            if (key.isNullOrBlank()) return GenerationResult.Failure(AiFailureCode.KEY_REQUIRED,
                "Save your own API key in AI Studio. No cloud request was made.")
            ensureCurrent(expectedEpoch)
            val permitted = synchronized(cloudAttempts) {
                while (cloudAttempts.isNotEmpty() && cloudAttempts.first() <= now() - 3_600_000L) cloudAttempts.removeFirst()
                if (cloudAttempts.size >= 20) false else { cloudAttempts.addLast(now()); true }
            }
            if (!permitted) return GenerationResult.Failure(AiFailureCode.RATE_LIMITED,
                "VEIL's limit of 20 cloud-generation attempts per hour for this running session has been reached. No automatic retry.")
            ensureCurrent(expectedEpoch)
            return (engineFactory?.invoke(latest, key) ?: OpenAIReplyEngine(latest.ai, key)).generate(request).also {
                ensureCurrent(expectedEpoch)
            }
        } finally {
            synchronized(policyLock) { activeGenerations.remove(job) }
        }
    }

    private fun epoch() = synchronized(policyLock) { policyEpoch }
    private fun current(epoch: Long) = synchronized(policyLock) { policyEpoch == epoch }
    private suspend fun ensureCurrent(epoch: Long) {
        currentCoroutineContext().ensureActive()
        if (!current(epoch)) throw CancellationException("VEIL generation settings changed")
    }
}
