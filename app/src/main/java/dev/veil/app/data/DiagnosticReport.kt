package dev.veil.app.data

import dev.veil.app.BuildConfig
import dev.veil.app.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Allowlisted export: no messages, names, IDs/hashes, contacts, intents, or keys. */
object DiagnosticReport {
    fun create(state: VeilUiState): String = JSONObject().apply {
        put("schema", 1)
        put("application", "VEIL")
        put("version", BuildConfig.VERSION_NAME)
        put("generatedAt", System.currentTimeMillis())
        put("milestone", "Phase 1: manual compatibility tests only")
        put("automaticSending", false)
        put("cloudProcessing", false)
        put("notificationAccess", state.system.notificationAccess)
        put("listenerConnected", state.system.listenerConnected)
        put("paused", state.preferences.capturePaused)
        put("retentionDays", state.preferences.retentionDays)
        put("device", JSONObject().apply {
            put("model", state.system.device.model)
            put("androidVersion", state.system.device.androidVersion)
            put("sdk", state.system.device.sdk)
            put("totalRamMb", state.system.device.totalRamMb)
            put("availableRamMb", state.system.device.availableRamMb)
            put("lowRam", state.system.device.lowRam)
        })
        put("platforms", JSONArray().apply {
            MessagingApp.entries.forEach { app ->
                val installed = state.system.apps.firstOrNull { it.app == app }
                val records = state.messages.filter { it.observation.app == app }
                val last = records.firstOrNull()
                put(JSONObject().apply {
                    put("package", app.packageName)
                    put("installed", installed?.installed ?: false)
                    put("version", installed?.version ?: JSONObject.NULL)
                    put("captureEnabled", app in state.preferences.enabledApps)
                    put("recentObservationCount", records.size)
                    put("lastNotificationAt", last?.observation?.postedAt ?: JSONObject.NULL)
                    put("lastPreviewAccessible", !last?.observation?.preview.isNullOrBlank())
                    put("lastSenderAvailable", !last?.observation?.sender.isNullOrBlank())
                    put("lastReplyActionObserved", last?.observation?.hasReplyAction ?: false)
                    put("testAttemptCount", records.count { it.dispatchAttemptedAt != null })
                    put("androidAcceptedCount", records.count { it.dispatchAcceptedAt != null })
                    put("userReportedReceiptCount", records.count { it.receiptReportedAt != null })
                    put("independentlyVerifiedReceipt", false)
                    put("lastAcceptedTestAt", records.mapNotNull { it.dispatchAcceptedAt }.maxOrNull() ?: JSONObject.NULL)
                })
            }
        })
        put("limitations", JSONArray(listOf("Notification-derived view, not full conversation history.",
            "Current actions may expire; observed action availability is not a delivery guarantee.",
            "No real-device compatibility claim follows from this report.",
            "Counts are limited to the most recent 100 retained observations.",
            "Receipt reports are user supplied, not independently verified.")))
    }.toString(2)
}
