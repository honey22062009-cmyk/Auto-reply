package dev.veil.app.data

import dev.veil.app.BuildConfig
import dev.veil.app.ai.AiProvider
import dev.veil.app.ai.DraftSafety
import dev.veil.app.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Allowlisted export: no messages, names, IDs/hashes, contacts, intents, or keys. */
object DiagnosticReport {
    fun create(state: VeilUiState): String = JSONObject().apply {
        put("schema", 2)
        put("application", "VEIL")
        put("version", BuildConfig.VERSION_NAME)
        put("generatedAt", System.currentTimeMillis())
        put("milestone", "Version 1.0")
        put("automaticSending", state.preferences.automationEnabled)
        put("cloudProcessing", state.preferences.ai.provider == AiProvider.OPENAI &&
            state.preferences.ai.cloudConsent && state.hasApiKey)
        put("ai", JSONObject().apply {
            put("provider", state.preferences.ai.provider.name)
            put("model", state.preferences.ai.model.takeUnless(DraftSafety::containsCredentials) ?: "redacted")
            put("maxOutputTokens", state.preferences.ai.maxOutputTokens)
            put("cloudConsent", state.preferences.ai.cloudConsent)
            put("includeContext", state.preferences.ai.includeContext)
            put("apiKeyAvailable", state.hasApiKey)
        })
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
                val last = records.maxByOrNull { it.observation.postedAt }
                fun MessageRecord.isKind(kind: String) = dispatchKind == kind ||
                    (kind == "TEST" && dispatchKind == null && dispatchAttemptedAt != null)
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
                    put("dispatchAttemptCount", records.count { it.dispatchAttemptedAt != null })
                    put("testAttemptCount", records.count { it.dispatchAttemptedAt != null && it.isKind("TEST") })
                    put("manualAttemptCount", records.count { it.dispatchAttemptedAt != null && it.isKind("MANUAL") })
                    put("automaticAttemptCount", records.count { it.dispatchAttemptedAt != null && it.isKind("AUTO") })
                    put("androidAcceptedCount", records.count { it.dispatchAcceptedAt != null })
                    put("testAndroidAcceptedCount", records.count { it.dispatchAcceptedAt != null && it.isKind("TEST") })
                    put("manualAndroidAcceptedCount", records.count { it.dispatchAcceptedAt != null && it.isKind("MANUAL") })
                    put("automaticAndroidAcceptedCount", records.count { it.dispatchAcceptedAt != null && it.isKind("AUTO") })
                    put("userReportedReceiptCount", records.count { it.receiptReportedAt != null })
                    put("independentlyVerifiedReceipt", false)
                    put("lastAcceptedTestAt", records.filter { it.isKind("TEST") }.mapNotNull { it.dispatchAcceptedAt }.maxOrNull() ?: JSONObject.NULL)
                })
            }
        })
        put("limitations", JSONArray(listOf("Notification-derived view, not full conversation history.",
            "Current actions may expire; observed action availability is not a delivery guarantee.",
            "No real-device compatibility claim follows from this report.",
            "Counts are limited to the most recent 100 retained observations.",
            "Automatic-sending and cloud flags describe configured eligibility, not proof of a send or provider request.",
            "Receipt reports are user supplied, not independently verified.")))
    }.toString(2)
}
