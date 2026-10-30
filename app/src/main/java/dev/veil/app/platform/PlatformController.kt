package dev.veil.app.platform

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.veil.app.domain.AppCapability
import dev.veil.app.domain.DeviceInfo
import dev.veil.app.domain.DispatchResult
import dev.veil.app.domain.MessagingApp
import dev.veil.app.domain.NotificationSink
import dev.veil.app.domain.SystemSnapshot
import dev.veil.app.domain.TEST_REPLY
import dev.veil.app.domain.VeilPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Live reply handles exist only in memory and are never reconstructed from persisted content. */
class PlatformController(context: Context) {
    private val context = context.applicationContext
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var preferences = VeilPreferences()
    private var listener: VeilNotificationListener? = null
    private val handles = LinkedHashMap<String, InspectedNotification>()
    private val attempted = LinkedHashSet<String>()
    private val blockedAutoIdentities = LinkedHashSet<String>()
    private var captureStop = false
    private var automaticStop = false
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    @Volatile var sink: NotificationSink? = null

    fun configure(preferences: VeilPreferences) {
        val previous = synchronized(lock) {
            val old = this.preferences
            this.preferences = preferences
            handles.entries.removeAll { !enabled(it.value.observation.app) }
            old
        }
        if (preferences.onboarded && !preferences.capturePaused &&
            (previous.enabledApps != preferences.enabledApps || !previous.onboarded || previous.capturePaused)) {
            mainHandler.post { refreshActive() }
        }
    }

    internal fun connected(service: VeilNotificationListener) {
        synchronized(lock) { listener = service; handles.clear() }
        mutableConnected.value = true
        sink?.onConnectionChanged(true)
        refreshActive()
    }

    internal fun disconnected(service: VeilNotificationListener) {
        val didDisconnect = synchronized(lock) {
            if (listener !== service) false else { listener = null; handles.clear(); true }
        }
        if (didDisconnect) {
            mutableConnected.value = false
            sink?.onConnectionChanged(false)
        }
    }

    internal fun observed(sbn: StatusBarNotification) = observed(sbn, fromActiveReplay = false)

    private fun observed(sbn: StatusBarNotification, fromActiveReplay: Boolean) {
        val app = MessagingApp.fromPackage(sbn.packageName) ?: return
        // Android grants broad listener access; do not inspect extras until the owner opts this app in.
        if (!synchronized(lock) { enabled(app) }) return
        val keyHash = NotificationInspector.digest(sbn.key)
        val inspected = runCatching { NotificationInspector.inspect(sbn) }.getOrNull()?.let {
            if (fromActiveReplay) it.copy(observation = it.observation.copy(fromActiveReplay = true)) else it
        }
        val notify = synchronized(lock) {
            handles.entries.removeAll { it.value.rawKey == sbn.key }
            if (!enabled(app)) return@synchronized false
            if (inspected != null) {
                handles[inspected.observation.id] = inspected
                while (handles.size > 250) handles.remove(handles.keys.first())
            }
            true
        }
        if (!notify) return
        if (inspected == null) sink?.onRemoved(keyHash) else sink?.onObserved(inspected.observation)
    }

    internal fun removed(sbn: StatusBarNotification) {
        if (MessagingApp.fromPackage(sbn.packageName) == null) return
        synchronized(lock) { handles.entries.removeAll { it.value.rawKey == sbn.key } }
        sink?.onRemoved(NotificationInspector.digest(sbn.key))
    }

    private fun refreshActive() {
        val service = synchronized(lock) { listener } ?: return
        try { service.activeNotifications.orEmpty().forEach { observed(it, fromActiveReplay = true) } }
        catch (_: SecurityException) { disconnected(service) }
        catch (_: IllegalStateException) { disconnected(service) }
    }

    /** Compatibility tests always retain the recognizable, fixed text. */
    suspend fun dispatchTest(id: String): DispatchResult = dispatchReply(id, TEST_REPLY)

    /**
     * IPC adapter only. The repository must commit its one-shot ledger and check manual approval
     * or explicitly enabled contact rules before calling this method. No detection callback sends.
     */
    suspend fun dispatchReply(id: String, text: String, automatic: Boolean = false): DispatchResult = withContext(Dispatchers.Main.immediate) {
        if (text.isBlank() || text.length > 1000 || text.any {
                (it.isISOControl() && it != '\n') || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069'
            }) {
            return@withContext DispatchResult.Rejected("Use a reply of 1–1,000 characters without unsafe control characters.")
        }
        val initial = synchronized(lock) {
            if (id in attempted) return@withContext DispatchResult.Rejected("A reply was already attempted for this message. Ask for a fresh message.")
            handles[id]
        } ?: return@withContext DispatchResult.Rejected("The live notification is no longer available. Ask for a fresh message.")
        if (!synchronized(lock) { enabled(initial.observation.app) }) {
            return@withContext DispatchResult.Rejected("Observation is paused or this platform is disabled.")
        }
        val component = ComponentName(context, VeilNotificationListener::class.java)
        if (!context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)) {
            return@withContext DispatchResult.Rejected("Notification access was removed. Review access in Android settings.")
        }
        if (context.getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            return@withContext DispatchResult.Rejected("Unlock the tablet before sending a reply.")
        }
        val service = synchronized(lock) { listener }
            ?: return@withContext DispatchResult.Rejected("Notification listener is disconnected.")
        val active = try { service.getActiveNotifications(arrayOf(initial.rawKey)).singleOrNull() }
        catch (_: SecurityException) { null }
        catch (_: IllegalStateException) { null }
        val current = active?.let { runCatching { NotificationInspector.inspect(it) }.getOrNull() }
        if (current == null || current.observation.id != id || current.observation.app != initial.observation.app) {
            forget(id)
            return@withContext DispatchResult.Rejected("This notification changed or was removed. Review the newest notification first.")
        }
        if (current.observation.isGroup) return@withContext DispatchResult.Rejected("Group replies are disabled. Review the conversation in the messaging app.")
        if (current.observation.sender.isNullOrBlank() || current.observation.preview.isNullOrBlank()) {
            return@withContext DispatchResult.Rejected("Sender or message preview is unavailable. Open the messaging app to verify the conversation.")
        }
        val reply = current.reply ?: return@withContext DispatchResult.Rejected("No supported, unambiguous free-form reply action is available.")
        synchronized(lock) {
            if (!enabled(current.observation.app) || id in attempted || listener !== service ||
                handles[id]?.rawKey != current.rawKey) {
                return@synchronized DispatchResult.Rejected("Sending was paused, disconnected, or this message was already attempted.")
            }
            if (automatic && (automaticStop || !preferences.automationEnabled || current.observation.identityKey == null ||
                    current.observation.identityKey in blockedAutoIdentities)) {
                return@synchronized DispatchResult.Rejected("Automatic replies were stopped for this contact or globally.")
            }
            attempted.add(id)
            while (attempted.size > 1000) attempted.remove(attempted.first())
            try {
                val results = Bundle().apply { putCharSequence(reply.input.resultKey, text) }
                val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                RemoteInput.addResultsToIntent(arrayOf(reply.input), intent, results)
                RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                reply.intent.send(context, 0, intent)
                DispatchResult.Accepted
            } catch (_: android.app.PendingIntent.CanceledException) {
                handles.remove(id)
                DispatchResult.Rejected("The messaging app cancelled this reply action. Ask for a fresh notification.")
            } catch (_: SecurityException) {
                handles.remove(id)
                DispatchResult.Rejected("Android did not authorize the reply action.")
            } catch (_: RuntimeException) {
                handles.remove(id)
                DispatchResult.Rejected("Android could not dispatch this reply action. No automatic retry was made.")
            }
        }
    }

    fun openConversation(id: String): Boolean {
        val entry = synchronized(lock) { handles[id] } ?: return false
        return try {
            val contentIntent = entry.contentIntent
            if (contentIntent != null) contentIntent.send()
            else {
                val launch = context.packageManager.getLaunchIntentForPackage(entry.observation.app.packageName) ?: return false
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: Exception) { false }
    }

    fun forget(id: String) { synchronized(lock) { handles.remove(id) } }
    fun clear() { synchronized(lock) { handles.clear() } }

    /** User stop latches survive older asynchronous preference emissions. */
    fun setCaptureStop(stopped: Boolean) {
        synchronized(lock) {
            captureStop = stopped
            if (stopped) handles.clear()
        }
    }

    fun setAutomaticStop(stopped: Boolean) {
        synchronized(lock) { automaticStop = stopped }
    }

    /** Immediate stop takes effect before any asynchronous contact-preference/database update. */
    fun suspendAutomaticIdentity(identityKey: String?) {
        if (identityKey != null) synchronized(lock) { blockedAutoIdentities.add(identityKey) }
    }

    /** Call only after the matching explicit enable operation was durably saved. */
    fun resumeAutomaticIdentity(identityKey: String?) {
        if (identityKey != null) synchronized(lock) { blockedAutoIdentities.remove(identityKey) }
    }

    fun requestReconnect() {
        runCatching { NotificationListenerService.requestRebind(ComponentName(context, VeilNotificationListener::class.java)) }
    }

    fun snapshot(): SystemSnapshot {
        val component = ComponentName(context, VeilNotificationListener::class.java)
        val access = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val apps = MessagingApp.entries.map { app ->
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(app.packageName, 0)
                AppCapability(app, true, info.versionName ?: info.longVersionCode.toString())
            } catch (_: PackageManager.NameNotFoundException) { AppCapability(app, false, null) }
        }
        return SystemSnapshot(access, connected.value, apps, DeviceInfo(
            "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            memory.totalMem / (1024 * 1024), memory.availMem / (1024 * 1024), activityManager.isLowRamDevice,
        ))
    }

    private fun enabled(app: MessagingApp) = !captureStop && preferences.onboarded && !preferences.capturePaused && app in preferences.enabledApps
}
