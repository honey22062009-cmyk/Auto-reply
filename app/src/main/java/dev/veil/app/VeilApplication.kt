package dev.veil.app

import android.app.Application
import androidx.room.Room
import dev.veil.app.data.*
import dev.veil.app.domain.*
import dev.veil.app.ai.AiConfiguration
import dev.veil.app.ai.WritingStyle
import dev.veil.app.platform.PlatformController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class VeilApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
    override fun onCreate() { super.onCreate(); graph.start() }
}

class AppGraph(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val platform = PlatformController(application)
    val preferences = PreferencesRepository(application)
    val secrets = ApiKeyStore(application)
    val database = Room.databaseBuilder(application, VeilDatabase::class.java, "veil.db")
        .addMigrations(VeilDatabase.MIGRATION_1_2, VeilDatabase.MIGRATION_2_3).build()
    val repository = VeilRepository(database, SecretBox(), platform::dispatchTest, platform::forget, platform::clear,
        replyDispatch = { id,text,automatic -> platform.dispatchReply(id,text,automatic) })
    val readyPreferences = MutableStateFlow<VeilPreferences?>(null)
    val hasApiKey = MutableStateFlow(false)
    val previewDraft = MutableStateFlow<String?>(null)
    val previewBusy = MutableStateFlow(false)
    val assistant = AssistantCoordinator(repository,{ readyPreferences.value ?: VeilPreferences() },secrets::read)
    val system = MutableStateFlow(SystemSnapshot())
    val notice = MutableStateFlow<String?>(null)
    private sealed interface PlatformEvent {
        data class Observed(val value: ObservedNotification) : PlatformEvent
        data class Removed(val hash: String) : PlatformEvent
        data class Connected(val value: Boolean) : PlatformEvent
    }
    private val events = Channel<PlatformEvent>(128)
    @Volatile private var pendingAi: AiConfiguration? = null
    @Volatile private var pendingStyle: WritingStyle? = null

    fun applyAiImmediately(value: AiConfiguration) {
        assistant.cancelPending()
        previewDraft.value=null
        pendingAi=value
        readyPreferences.value=readyPreferences.value?.copy(ai=value)
    }
    fun applyStyleImmediately(value: WritingStyle) {
        assistant.cancelPending()
        previewDraft.value=null
        pendingStyle=value
        readyPreferences.value=readyPreferences.value?.copy(style=value)
    }

    fun start() {
        platform.sink = object : NotificationSink {
            override fun onObserved(notification: ObservedNotification) { enqueue(PlatformEvent.Observed(notification)) }
            override fun onRemoved(keyHash: String) { enqueue(PlatformEvent.Removed(keyHash)) }
            override fun onConnectionChanged(connected: Boolean) { enqueue(PlatformEvent.Connected(connected)) }
        }
        scope.launch {
            try {
                repository.initialize()
                hasApiKey.value = secrets.hasKey()
                assistant.start(scope)
                launch {
                    preferences.preferences.collect { value ->
                        val withAi=pendingAi?.let { override ->
                            if (override==value.ai) { pendingAi=null; value } else value.copy(ai=override)
                        } ?: value
                        val effective=pendingStyle?.let { override ->
                            if (override==value.style) { pendingStyle=null; withAi } else withAi.copy(style=override)
                        } ?: withAi
                        if (readyPreferences.value?.ai != effective.ai) assistant.cancelPending()
                        repository.configure(effective)
                        readyPreferences.value = effective
                        platform.configure(effective)
                        refreshSystem()
                    }
                }
                for (event in events) {
                    try {
                        when (event) {
                            is PlatformEvent.Observed -> { repository.record(event.value); assistant.enqueue(event.value) }
                            is PlatformEvent.Removed -> repository.removed(event.hash)
                            is PlatformEvent.Connected -> { repository.connectionChanged(event.value); refreshSystem() }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        platform.clear()
                        notice.value = "A local storage operation failed. Live test actions were cleared. Restart VEIL and check available storage."
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                platform.clear()
                notice.value = "VEIL could not initialize local storage. Close and reopen the app."
                readyPreferences.value = VeilPreferences()
            }
        }
    }

    private fun enqueue(event: PlatformEvent) {
        if (events.trySend(event).isFailure) {
            platform.clear() // Fail closed during notification floods; never dispatch from stale cached state.
            notice.value = "Too many notification updates arrived at once. Test actions were cleared; request a fresh test message."
        }
    }

    fun refreshSystem() {
        system.value = runCatching { platform.snapshot() }.getOrElse {
            notice.value = "Device capability information could not be refreshed."
            SystemSnapshot()
        }
    }
}
