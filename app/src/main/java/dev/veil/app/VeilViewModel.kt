package dev.veil.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.veil.app.data.DiagnosticReport
import dev.veil.app.domain.*
import dev.veil.app.ai.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VeilViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as VeilApplication).graph
    private val core = combine(graph.readyPreferences, graph.repository.messages,
        graph.repository.activity, graph.repository.contacts, graph.system) { preferences, messages, events, contacts, system ->
        VeilUiState(preferences != null, preferences ?: VeilPreferences(), messages, events, contacts, system)
    }
    private val enriched = combine(core,graph.hasApiKey,graph.previewDraft,graph.previewBusy) { value,key,preview,busy ->
        value.copy(hasApiKey=key,previewDraft=preview,previewBusy=busy)
    }
    val state: StateFlow<VeilUiState> = combine(enriched, graph.notice) { value, notice -> value.copy(notice = notice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VeilUiState())

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { graph.notice.value = "The operation could not be completed. Check storage and try again." }
        }
    }
    fun finishOnboarding(apps: Set<MessagingApp>) = work { graph.preferences.finishOnboarding(apps) }
    fun setCapturePaused(value: Boolean) {
        graph.platform.setCaptureStop(value)
        if (value) {
            // Immediate in-memory stop, before waiting for asynchronous settings/database persistence.
            graph.platform.configure((graph.readyPreferences.value ?: VeilPreferences()).copy(capturePaused = true))
        }
        work { graph.preferences.setPaused(value) }
    }
    fun setPlatformEnabled(app: MessagingApp, value: Boolean) {
        if (!value) graph.platform.configure((graph.readyPreferences.value ?: VeilPreferences()).let {
            it.copy(enabledApps = it.enabledApps - app)
        })
        work { graph.preferences.setApp(app, value) }
    }
    fun setAppearance(value: Appearance) = work { graph.preferences.setAppearance(value) }
    fun setReduceMotion(value: Boolean) = work { graph.preferences.setMotion(value) }
    fun setGlassIntensity(value: Float) = work { graph.preferences.setGlass(value) }
    fun setRetentionDays(value: Int) = work { graph.preferences.setRetention(value) }
    fun setAccent(value: AccentPalette) = work { graph.preferences.setAccent(value) }
    fun setCompanionEnabled(value: Boolean) = work { graph.preferences.setCompanion(value) }
    fun setAutomationEnabled(value: Boolean) {
        graph.platform.setAutomaticStop(!value)
        if (!value) graph.platform.configure((graph.readyPreferences.value ?: VeilPreferences()).copy(automationEnabled=false))
        work { graph.preferences.setAutomation(value) }
    }
    fun saveAiConfiguration(value: AiConfiguration) {
        graph.applyAiImmediately(value)
        work {
            graph.preferences.saveAi(value)
            graph.notice.value="AI provider settings saved. Cloud use still requires a stored API key and your explicit content-sharing consent."
        }
    }
    fun saveWritingStyle(value: WritingStyle) {
        graph.applyStyleImmediately(value)
        work { graph.preferences.saveStyle(value); graph.notice.value="Writing preferences saved. Preview before enabling automation." }
    }
    fun saveApiKey(value: String) {
        graph.assistant.cancelPending()
        work {
            graph.secrets.save(value); graph.hasApiKey.value=graph.secrets.hasKey()
            graph.notice.value="API key encrypted with Android Keystore. The saved key is never displayed or exported."
        }
    }
    fun deleteApiKey() {
        val offline=(graph.readyPreferences.value?.ai ?: AiConfiguration()).copy(provider=AiProvider.TEMPLATE,cloudConsent=false)
        graph.applyAiImmediately(offline)
        graph.hasApiKey.value=false
        work {
            graph.secrets.delete()
            graph.preferences.saveAi(offline)
            graph.notice.value="API key deleted. Offline templates selected and cloud sharing disabled."
        }
    }
    fun previewReply(sample: String) {
        if (graph.previewBusy.value) return
        graph.previewDraft.value = null // Never display an older reply while generating a new one.
        graph.previewBusy.value = true
        work {
            try {
                when (val result = graph.assistant.preview(sample)) {
                    is GenerationResult.Success -> {
                        graph.previewDraft.value = result.draft
                        graph.notice.value = "Preview generated by ${result.provider}. ${result.inputTokens} input / ${result.outputTokens} output tokens. Cost is not estimated."
                    }
                    is GenerationResult.Failure -> {
                        graph.previewDraft.value = null
                        graph.notice.value = result.message
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!viewModelScope.isActive) throw cancelled
                // Provider or style changes can revoke a pending draft. Tell the user instead
                // of leaving the preview area empty with no explanation.
                graph.previewDraft.value = null
                graph.notice.value = "Preview was interrupted. Review your AI settings and try again."
            } finally {
                graph.previewBusy.value = false
            }
        }
    }
    fun generateSuggestion(messageId: String) = work {
        graph.repository.message(messageId)?.let { graph.assistant.process(it.observation,force=true) }
    }
    fun sendReply(messageId: String, text: String) = work { graph.notice.value=graph.repository.sendReply(messageId,text) }
    fun bindContact(contactId: String, messageId: String) = work { graph.notice.value=graph.repository.bindContact(contactId,messageId) }
    fun saveContact(contact: PriorityContact) {
        if (contact.paused || !contact.autoEnabled) graph.platform.suspendAutomaticIdentity(contact.identityKey)
        work {
            graph.notice.value=graph.repository.saveContact(contact)
            val saved=graph.repository.contactSnapshot().firstOrNull { it.id==contact.id }
            if (saved?.autoEnabled==true && !saved.paused) graph.platform.resumeAutomaticIdentity(saved.identityKey)
        }
    }
    fun dismissMessage(id: String) = work { graph.repository.dismiss(id) }
    fun sendTest(id: String) = work { graph.notice.value = graph.repository.sendTest(id) }
    fun confirmReceipt(id: String) = work { graph.notice.value = graph.repository.confirmReceipt(id) }
    fun addContact(label: String, app: MessagingApp, sender: String, username: String = "",
        userId: String = "", phone: String = "") = work {
        graph.notice.value = graph.repository.addContact(label, app, sender, username, userId, phone)
    }
    fun removeContact(id: String) {
        graph.platform.suspendAutomaticIdentity(state.value.contacts.firstOrNull { it.id==id }?.identityKey)
        work { graph.repository.removeContact(id) }
    }
    fun deleteHistory() = work {
        graph.repository.deleteHistory()
        graph.notice.value = "Local notification history and activity deleted. Saved contacts and settings remain."
    }
    fun openConversation(id: String) {
        if (graph.platform.openConversation(id)) return
        val app = state.value.messages.firstOrNull { it.observation.id == id }?.observation?.app
        val context = getApplication<Application>()
        val launch = app?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }
        if (launch == null || runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
            graph.notice.value = "The app could not be opened. Open it from your tablet’s launcher."
        }
    }
    fun refreshSystemState() = work {
        graph.readyPreferences.value?.let { graph.repository.configure(it) }
        graph.refreshSystem()
    }
    fun clearNotice() { graph.notice.value = null }
    fun exportDiagnostics(uri: Uri) {
        val snapshot = state.value
        work {
            require(uri.scheme == "content")
            val content = DiagnosticReport.create(snapshot)
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: error("No output stream")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            graph.notice.value = "Sanitized diagnostics exported. No message text, sender names, contact records, or secrets were included."
        }
    }
    fun exportSettings(uri: Uri) {
        val snapshot=state.value
        work {
            require(uri.scheme=="content")
            val p=snapshot.preferences
            val json=JSONObject().put("schema",1).put("application","VEIL").put("version",BuildConfig.VERSION_NAME)
                .put("appearance",p.appearance.name).put("accent",p.accent.name).put("glassIntensity",p.glassIntensity)
                .put("reduceMotion",p.reduceMotion).put("companionEnabled",p.companionEnabled).put("retentionDays",p.retentionDays)
                .put("enabledApps",JSONArray(p.enabledApps.map { it.name }))
                .put("style",JSONObject().put("language",p.style.language.name).put("warmth",p.style.warmth)
                    .put("humor",p.style.humor).put("emoji",p.style.emoji).put("maxCharacters",p.style.maxCharacters)
                    .put("preferredPhrases",p.style.preferredPhrases).put("avoidPhrases",p.style.avoidPhrases)
                    .put("approvedExamples",JSONArray(p.style.approvedExamples)))
                .put("ai",JSONObject().put("provider",p.ai.provider.name).put("model",p.ai.model)
                    .put("maxOutputTokens",p.ai.maxOutputTokens).put("cloudConsent",false))
                .put("automaticSending",false)
                .put("exclusions","API keys, contacts, message history, dispatch ledger, cloud consent, and automation authorization are not exported.")
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"wt") ?: error("No stream")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(json.toString(2)) }
            graph.notice.value="Settings exported, including your supplied writing examples. Keys and conversation history were excluded."
        }
    }
}
