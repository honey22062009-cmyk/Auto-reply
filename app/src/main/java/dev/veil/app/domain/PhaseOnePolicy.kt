package dev.veil.app.domain

/** Sending remains an explicit, one-shot compatibility test in this milestone. */
object PhaseOnePolicy {
    fun rejection(record: MessageRecord, preferences: VeilPreferences): String? = when {
        !preferences.onboarded -> "Complete setup before testing."
        preferences.capturePaused -> "VEIL is paused. Resume before testing."
        record.observation.app !in preferences.enabledApps -> "This platform is disabled."
        record.dispatchAttemptedAt != null -> "This notification already had a test attempt. Ask for a new test message."
        record.status == ReplyStatus.DISMISSED -> "This notification was dismissed."
        !record.active -> "The original notification is no longer active."
        record.observation.isGroup -> "Group replies are disabled in this compatibility build."
        record.observation.sender.isNullOrBlank() -> "The sender cannot be identified. Open the messaging app."
        record.observation.preview.isNullOrBlank() -> "The message preview is unavailable. Open the messaging app."
        !record.observation.hasReplyAction -> "No supported reply action is available. Copy the test text and open the app."
        else -> null
    }
}
