package dev.veil.app.domain

import dev.veil.app.ai.AiConfiguration
import dev.veil.app.ai.WritingStyle

enum class MessagingApp(val packageName: String, val label: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"), INSTAGRAM("com.instagram.android", "Instagram");
    companion object { fun fromPackage(value: String) = entries.firstOrNull { it.packageName == value } }
}

const val TEST_REPLY = "VEIL compatibility test. Please confirm you received this message."

data class ObservedNotification(
    val id: String,
    val keyHash: String,
    val app: MessagingApp,
    val sender: String?,
    val preview: String?,
    val postedAt: Long,
    val messageAt: Long?,
    val isMessagingStyle: Boolean,
    val isGroup: Boolean,
    val actionCount: Int,
    val hasReplyAction: Boolean,
    val conversationHint: String? = null,
    val identityKey: String? = null,
    val fromActiveReplay: Boolean = false,
    val notificationContextNeedsReview: Boolean = false,
)

enum class ReplyStatus(val label: String) {
    READY("Test available"), UNAVAILABLE("Copy / open app"), DISPATCHING("Dispatch attempted"),
    ACCEPTED("Accepted by Android"), FAILED("Dispatch failed"), DISMISSED("Dismissed"), REMOVED("Notification removed")
}

data class MessageRecord(
    val observation: ObservedNotification,
    val status: ReplyStatus,
    val active: Boolean,
    val dispatchAttemptedAt: Long? = null,
    val dispatchAcceptedAt: Long? = null,
    val receiptReportedAt: Long? = null,
    val failureReason: String? = null,
    val suggestion: String? = null,
    val generationState: String = "NONE",
    val generationReason: String? = null,
    val engine: String? = null,
    val dispatchKind: String? = null,
)

data class ActivityEvent(val id: Long, val at: Long, val app: MessagingApp?, val title: String, val detail: String)
data class PriorityContact(val id: String, val label: String, val app: MessagingApp, val sender: String,
    val identityKey: String? = null, val autoEnabled: Boolean = false, val paused: Boolean = false,
    val cooldownSeconds: Int = 120, val maxPerHour: Int = 5, val maxPerDay: Int = 20,
    val maxPerConversation: Int = 3, val quietStartHour: Int = 22, val quietEndHour: Int = 8,
    val quietEnabled: Boolean = true, val instruction: String = "", val compatibilityVerified: Boolean = false)
enum class Appearance { SYSTEM, LIGHT, DARK }
enum class AccentPalette { ROSE, CORAL, PLUM, AMBER, SILVER }
data class VeilPreferences(
    val onboarded: Boolean = false,
    val enabledApps: Set<MessagingApp> = emptySet(),
    val capturePaused: Boolean = false,
    val appearance: Appearance = Appearance.SYSTEM,
    val reduceMotion: Boolean = false,
    val glassIntensity: Float = 0.65f,
    val retentionDays: Int = 1,
    val accent: AccentPalette = AccentPalette.ROSE,
    val companionEnabled: Boolean = true,
    val ai: AiConfiguration = AiConfiguration(),
    val style: WritingStyle = WritingStyle(),
    val automationEnabled: Boolean = false,
)

data class AppCapability(val app: MessagingApp, val installed: Boolean, val version: String?)
data class DeviceInfo(val model: String = "", val androidVersion: String = "", val sdk: Int = 0,
    val totalRamMb: Long = 0, val availableRamMb: Long = 0, val lowRam: Boolean = false)
data class SystemSnapshot(val notificationAccess: Boolean = false, val listenerConnected: Boolean = false,
    val apps: List<AppCapability> = emptyList(), val device: DeviceInfo = DeviceInfo())

sealed interface DispatchResult {
    data object Accepted : DispatchResult
    data class Rejected(val reason: String) : DispatchResult
}

/** Platform callbacks contain only supported notification fields, never credentials. */
interface NotificationSink {
    fun onObserved(notification: ObservedNotification)
    fun onRemoved(keyHash: String)
    fun onConnectionChanged(connected: Boolean)
}

/** Deterministic phase-one engine: intentionally no inferred answer or network calls. */
interface ReplyEngine { fun testReply(): String }
class ManualTemplateReplyEngine : ReplyEngine { override fun testReply() = TEST_REPLY }

data class VeilUiState(
    val loaded: Boolean = false,
    val preferences: VeilPreferences = VeilPreferences(),
    val messages: List<MessageRecord> = emptyList(),
    val activity: List<ActivityEvent> = emptyList(),
    val contacts: List<PriorityContact> = emptyList(),
    val system: SystemSnapshot = SystemSnapshot(),
    val notice: String? = null,
    val hasApiKey: Boolean = false,
    val previewDraft: String? = null,
    val previewBusy: Boolean = false,
)
