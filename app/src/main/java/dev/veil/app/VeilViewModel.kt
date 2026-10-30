package dev.veil.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.veil.app.data.DiagnosticReport
import dev.veil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VeilViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as VeilApplication).graph
    private val core = combine(graph.readyPreferences, graph.repository.messages,
        graph.repository.activity, graph.repository.contacts, graph.system) { preferences, messages, events, contacts, system ->
        VeilUiState(preferences != null, preferences ?: VeilPreferences(), messages, events, contacts, system)
    }
    val state: StateFlow<VeilUiState> = combine(core, graph.notice) { value, notice -> value.copy(notice = notice) }
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
    fun dismissMessage(id: String) = work { graph.repository.dismiss(id) }
    fun sendTest(id: String) = work { graph.notice.value = graph.repository.sendTest(id) }
    fun confirmReceipt(id: String) = work { graph.notice.value = graph.repository.confirmReceipt(id) }
    fun addContact(label: String, app: MessagingApp, sender: String) = work {
        graph.notice.value = graph.repository.addContact(label, app, sender)
    }
    fun removeContact(id: String) = work { graph.repository.removeContact(id) }
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
}
