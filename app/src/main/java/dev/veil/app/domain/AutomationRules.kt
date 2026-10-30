package dev.veil.app.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class AutomationDisposition { ALLOW, APPROVAL, IGNORE }

enum class AutomationReason {
    PAUSED, PLATFORM_DISABLED, DUPLICATE, USER_RESPONDED, NO_CONTENT, NO_REPLY_NEEDED,
    NOT_PRIORITY, NOT_ENABLED, IDENTITY_UNCERTAIN, IDENTITY_MISMATCH, GROUP,
    COMPATIBILITY_UNVERIFIED, ACTION_UNAVAILABLE, NOTIFICATION_INACTIVE, CONTEXT_MISSING,
    HISTORY_INCOMPLETE, FAILURE_SUSPENSION, INVALID_CONFIGURATION, CLOCK_UNCERTAIN,
    STALE_MESSAGE, QUIET_HOURS, COOLDOWN, CONVERSATION_LIMIT, HOURLY_LIMIT, DAILY_LIMIT,
    CONTACT_DAILY_LIMIT, SENSITIVE_MESSAGE, UNCERTAIN_MESSAGE, DRAFT_MISSING, UNSAFE_DRAFT,
    GENERATOR_REVIEW, SAFE_SMALLTALK,
}

data class AutomationDecision(
    val disposition: AutomationDisposition,
    val reason: AutomationReason,
    /** Controlled UI copy only; never interpolate incoming content or identifiers here. */
    val detail: String,
)

data class AutomaticReplyAttempt(val at: Instant, val contactId: String, val conversationKey: String)

data class AutomationLimits(
    val cooldownSeconds: Long = 120,
    val maxConversation: Int = 3,
    val maxHour: Int = 10,
    val maxDay: Int = 30,
    val contactMaxDay: Int? = null,
    val maxMessageAgeSeconds: Long = 180,
)

/** Minutes after local midnight. Equal endpoints disable this window; null disables the feature. */
data class QuietHours(val startMinute: Int, val endMinute: Int, val zoneId: ZoneId? = null) {
    fun isQuietAt(instant: Instant, defaultZone: ZoneId): Boolean {
        if (startMinute !in 0..1439 || endMinute !in 0..1439) return true
        if (startMinute == endMinute) return false
        val local = instant.atZone(zoneId ?: defaultZone)
        val minute = local.hour * 60 + local.minute
        return if (startMinute < endMinute) minute >= startMinute && minute < endMinute
        else minute >= startMinute || minute < endMinute
    }
}

/**
 * This input is a snapshot, not authority to dispatch. The repository must durably reserve an
 * attempt and the platform must revalidate the live notification/action immediately before IPC.
 * All authorizing defaults fail closed. A display name alone is never a stable identity.
 */
data class AutomationInput(
    val now: Instant = Instant.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val contactId: String? = null,
    val conversationKey: String? = null,
    val observedIdentityKey: String? = null,
    val boundIdentityKey: String? = null,
    val incoming: String = "",
    val draft: String? = null,
    val userApprovedAutomation: Boolean = false,
    val globalPaused: Boolean = true,
    val appEnabled: Boolean = false,
    val recipientIdentityStable: Boolean = false,
    val senderAmbiguous: Boolean = false,
    val compatibilityVerified: Boolean = false,
    val hasReplyAction: Boolean = false,
    val notificationActive: Boolean = false,
    val isGroup: Boolean = false,
    val alreadyAttempted: Boolean = false,
    val userAlreadyResponded: Boolean = false,
    val sufficientContext: Boolean = false,
    val historyComplete: Boolean = false,
    val history: List<AutomaticReplyAttempt> = emptyList(),
    val consecutiveFailures: Int = 0,
    val limits: AutomationLimits = AutomationLimits(),
    val quietHours: QuietHours? = null,
    val observedAt: Instant? = null,
    val generatorRequiresApproval: Boolean = false,
)

enum class MessageSafety { SMALLTALK, NO_REPLY_NEEDED, SENSITIVE, UNCERTAIN }
enum class SensitiveCategory { FINANCIAL, SECURITY, MEDICAL, PERSONAL, COMMITMENT, THREAT, INSTRUCTIONS }
data class SafetyAssessment(val safety: MessageSafety, val category: SensitiveCategory? = null)
data class DraftAssessment(val valid: Boolean, val safeForAutomation: Boolean, val reason: String?)

/**
 * A deterministic, conservative boundary, not a comprehensive semantic moderation model.
 * Only recognized self-contained greetings can pass unattended. Unknown text requires review.
 */
object MessageSafetyClassifier {
    private val financial = words("payment|payments|pay|paid|bank|account number|transfer|money|cash|upi|loan|invest|investment|refund|purchase|buy|sell|salary|rent|invoice|crypto|bitcoin|paisa|paise|paise bhej|paise bhejo|bhugtan|पैसा|पैसे|भुगतान|बैंक|खाता|रुपये|रुपया|कर्ज")
    private val security = words("password|passcode|pin|otp|one time|verification|verify|login|log in|sign in|authentication|security|recovery|secret|credential|credentials|token|api key|2fa|two factor|code bhej|code bata|पासवर्ड|ओटीपी|सत्यापन|गुप्त|कोड|पिन")
    private val medical = words("emergency|ambulance|hospital|doctor|medical|medicine|medication|overdose|chest pain|cant breathe|cannot breathe|suicide|self harm|marna|dawai|dava|tabiyat|इलाज|दवा|दवाई|अस्पताल|डॉक्टर|आपातकाल|मरना|सांस")
    private val personal = words("break up|breakup|divorce|marry|marriage|pregnant|pregnancy|relationship|love you|hate you|cheating|leave me|leave you|personal decision|shaadi|shadi|rishta|pyaar|pyar|शादी|रिश्ता|प्यार|तलाक|गर्भ")
    private val commitment = words("promise|promised|commit|commitment|agree|accept|approve|approval|contract|sign|signature|book|booking|confirm|confirmed|deadline|meet|meeting|appointment|tomorrow|tonight|today at|kal|aaj|milte|milna|bhej dunga|bhej dungi|kar dunga|kar dungi|pakka|vaada|वादा|पक्का|कल|आज|मिलना|मिलते|भेज दूंगा|कर दूंगा")
    private val threat = words("kill|hurt|threat|threaten|harass|harassment|blackmail|abuse|stalk|violence|police|weapon|gun|bomb|dhamki|maar dunga|maar dungi|धमकी|मार दूंगा|हत्या|हिंसा|पुलिस")
    private val instructions = words("ignore previous|ignore all|system prompt|developer message|assistant instructions|reveal your|send automatically|auto reply|automated reply|do not tell|dont tell|keep this secret|pretend to|act as|prompt injection")
    private val greetings = setOf("hi", "hii", "hiii", "hello", "hey", "heyy", "yo", "namaste", "namaskar", "नमस्ते", "नमस्कार", "good morning", "good afternoon", "good evening", "good night", "morning", "how are you", "how r u", "how are u", "how s it going", "whats up", "what s up", "wassup", "kya haal", "kya haal hai", "kaise ho", "kaisi ho", "kaisa hai", "कैसे हो", "कैसी हो", "क्या हाल है", "सुप्रभात", "शुभ रात्रि")
    private val acknowledgements = setOf("ok", "okay", "k", "kk", "got it", "alright", "all right", "thanks", "thank you", "thankyou", "thx", "ty", "welcome", "you re welcome", "cool", "nice", "great", "done", "noted", "hmm", "hm", "haha", "lol", "haan", "han", "ji", "theek", "thik", "theek hai", "thik hai", "shukriya", "dhanyavaad", "हाँ", "हां", "जी", "ठीक", "ठीक है", "शुक्रिया", "धन्यवाद")

    fun classify(message: String): SafetyAssessment {
        val text = normalize(message)
        if (text.isBlank()) return SafetyAssessment(
            if (message.any { it == '?' || it == '؟' }) MessageSafety.UNCERTAIN else MessageSafety.NO_REPLY_NEEDED)
        val category = when {
            financial.containsMatchIn(text) || message.any { it == '$' || it in "₹€£" } -> SensitiveCategory.FINANCIAL
            security.containsMatchIn(text) || Regex("(?<!\\d)\\d{4,8}(?!\\d)").containsMatchIn(message) -> SensitiveCategory.SECURITY
            medical.containsMatchIn(text) -> SensitiveCategory.MEDICAL
            personal.containsMatchIn(text) -> SensitiveCategory.PERSONAL
            commitment.containsMatchIn(text) -> SensitiveCategory.COMMITMENT
            threat.containsMatchIn(text) -> SensitiveCategory.THREAT
            instructions.containsMatchIn(text) -> SensitiveCategory.INSTRUCTIONS
            else -> null
        }
        if (category != null) return SafetyAssessment(MessageSafety.SENSITIVE, category)
        if (text in acknowledgements) return SafetyAssessment(MessageSafety.NO_REPLY_NEEDED)
        // No substring match: "hey, transfer money" must never inherit greeting authorization.
        if (text in greetings) return SafetyAssessment(MessageSafety.SMALLTALK)
        return SafetyAssessment(MessageSafety.UNCERTAIN)
    }

    internal fun normalize(message: String): String = Normalizer.normalize(message, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{S}\\p{Z}\\p{C}]+"), " ").trim()

    private fun words(alternatives: String) = Regex("(?<![\\p{L}\\p{M}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{M}\\p{N}])", RegexOption.IGNORE_CASE)
}

/** Validates any suggestion, then applies a tighter allowance for unattended replies. */
object AutomationDraftValidator {
    private val control = Regex("[\\p{Cc}\\p{Cf}]")
    private val secret = Regex("(?i)(?:sk-[a-z0-9_-]{8,}|gh[pousr]_[a-z0-9]+|-----BEGIN|(?:password|passcode|otp|pin|verification code)\\s*[:=]|(?<!\\d)\\d{4,8}(?!\\d))")
    private val link = Regex("(?i)(?:https?://|www\\.|[a-z0-9-]+\\.(?:com|net|org|io|app|co|in)(?:/|\\b))")
    private val safeReplies = setOf("hi", "hii", "hello", "hey", "heyy", "yo", "namaste", "namaskar", "नमस्ते", "नमस्कार", "good morning", "good afternoon", "good evening", "good night", "morning", "hey kaise ho", "hi kaise ho", "hello kaise ho", "hey kya haal hai", "hey how are you", "hello how are you", "hi how are you", "नमस्ते कैसे हो")

    fun validate(draft: String?, maxCharacters: Int = 800): DraftAssessment {
        if (draft.isNullOrBlank()) return DraftAssessment(false, false, "No reply was generated.")
        if (maxCharacters !in 1..2000 || draft.length > maxCharacters) return DraftAssessment(false, false, "The reply exceeds the configured length.")
        if (control.containsMatchIn(draft) || secret.containsMatchIn(draft)) return DraftAssessment(false, false, "The reply contains control characters or possible secrets.")
        if (link.containsMatchIn(draft)) return DraftAssessment(true, false, "Links require your review.")
        val safety = MessageSafetyClassifier.classify(draft)
        if (safety.safety == MessageSafety.SENSITIVE) return DraftAssessment(true, false, "Sensitive wording or a possible commitment requires your review.")
        val safe = MessageSafetyClassifier.normalize(draft) in safeReplies
        return DraftAssessment(true, safe, if (safe) null else "This reply requires your review before sending.")
    }
}

class AutomationRules {
    fun evaluate(input: AutomationInput): AutomationDecision {
        fun ignore(reason: AutomationReason, detail: String) = AutomationDecision(AutomationDisposition.IGNORE, reason, detail)
        fun review(reason: AutomationReason, detail: String) = AutomationDecision(AutomationDisposition.APPROVAL, reason, detail)
        if (input.globalPaused) return ignore(AutomationReason.PAUSED, "Automation is paused.")
        if (!input.appEnabled) return ignore(AutomationReason.PLATFORM_DISABLED, "This messaging platform is disabled.")
        if (input.alreadyAttempted) return ignore(AutomationReason.DUPLICATE, "This message already has a reply attempt. No automatic retry.")
        if (input.userAlreadyResponded) return ignore(AutomationReason.USER_RESPONDED, "A local-user reply was observed.")
        if (input.incoming.isBlank()) return ignore(AutomationReason.NO_CONTENT, "No usable message preview is available.")
        if (input.contactId.isNullOrBlank()) return review(AutomationReason.NOT_PRIORITY, "This sender is not an explicitly configured priority contact.")
        if (!input.userApprovedAutomation) return review(AutomationReason.NOT_ENABLED, "Automatic replies are not enabled for this contact.")
        if (input.senderAmbiguous || !input.recipientIdentityStable || input.observedIdentityKey.isNullOrBlank() || input.boundIdentityKey.isNullOrBlank() || input.conversationKey.isNullOrBlank())
            return review(AutomationReason.IDENTITY_UNCERTAIN, "A stable, unambiguous recipient identity is required. A display name alone is insufficient.")
        if (input.observedIdentityKey != input.boundIdentityKey) return review(AutomationReason.IDENTITY_MISMATCH, "The current notification does not match this contact's bound identity.")
        if (input.isGroup) return review(AutomationReason.GROUP, "Group messages require manual review.")
        if (!input.compatibilityVerified) return review(AutomationReason.COMPATIBILITY_UNVERIFIED, "Complete a consenting-contact compatibility test before enabling unattended replies.")
        if (!input.notificationActive) return review(AutomationReason.NOTIFICATION_INACTIVE, "The original notification is no longer active.")
        if (!input.hasReplyAction) return review(AutomationReason.ACTION_UNAVAILABLE, "No supported live reply action is available. Copy and open the messaging app.")
        if (!input.sufficientContext) return review(AutomationReason.CONTEXT_MISSING, "There is insufficient context for an unattended reply.")
        if (!input.historyComplete) return review(AutomationReason.HISTORY_INCOMPLETE, "Reply-limit history is unavailable. Automatic sending is suspended.")
        if (input.consecutiveFailures >= 3) return ignore(AutomationReason.FAILURE_SUSPENSION, "Automation is suspended after repeated dispatch failures.")
        val limits = input.limits
        if (limits.cooldownSeconds !in 0L..86_400L || limits.maxConversation !in 1..100 || limits.maxHour !in 1..100 || limits.maxDay !in 1..1000 ||
            (limits.contactMaxDay != null && limits.contactMaxDay !in 1..1000) || limits.maxMessageAgeSeconds !in 1L..600L || input.consecutiveFailures < 0 ||
            input.quietHours?.let { it.startMinute !in 0..1439 || it.endMinute !in 0..1439 } == true)
            return review(AutomationReason.INVALID_CONFIGURATION, "Reply limits or quiet hours need valid settings.")
        val observedAt = input.observedAt ?: return review(AutomationReason.STALE_MESSAGE, "The message time cannot be verified.")
        if (observedAt.isAfter(input.now.plusSeconds(5)) || input.history.any { it.at.isAfter(input.now) })
            return review(AutomationReason.CLOCK_UNCERTAIN, "The device clock or reply timestamps changed. Review before sending.")
        if (observedAt.plusSeconds(limits.maxMessageAgeSeconds).isBefore(input.now))
            return review(AutomationReason.STALE_MESSAGE, "This message is too old for an unattended reply.")
        if (input.quietHours?.isQuietAt(input.now, input.zoneId) == true)
            return ignore(AutomationReason.QUIET_HOURS, "Quiet hours are active for this contact.")
        val contactAttempts = input.history.filter { it.contactId == input.contactId }
        val mostRecent = contactAttempts.maxOfOrNull { it.at }
        if (mostRecent != null && mostRecent.plusSeconds(limits.cooldownSeconds).isAfter(input.now))
            return ignore(AutomationReason.COOLDOWN, "This contact's reply cooldown is active.")
        if (contactAttempts.count { it.conversationKey == input.conversationKey } >= limits.maxConversation)
            return ignore(AutomationReason.CONVERSATION_LIMIT, "This conversation reached its automatic-reply limit.")
        val hourStart = input.now.minusSeconds(3600)
        if (input.history.count { it.at.isAfter(hourStart) } >= limits.maxHour)
            return ignore(AutomationReason.HOURLY_LIMIT, "The hourly automatic-reply limit has been reached.")
        // atStartOfDay handles 23/25-hour days and timezone transitions without a fixed 24h assumption.
        val dayStart = input.now.atZone(input.zoneId).toLocalDate().atStartOfDay(input.zoneId).toInstant()
        if (input.history.count { !it.at.isBefore(dayStart) } >= limits.maxDay)
            return ignore(AutomationReason.DAILY_LIMIT, "The daily automatic-reply limit has been reached.")
        if (limits.contactMaxDay != null && contactAttempts.count { !it.at.isBefore(dayStart) } >= limits.contactMaxDay)
            return ignore(AutomationReason.CONTACT_DAILY_LIMIT, "This contact reached the daily automatic-reply limit.")
        when (MessageSafetyClassifier.classify(input.incoming).safety) {
            MessageSafety.NO_REPLY_NEEDED -> return ignore(AutomationReason.NO_REPLY_NEEDED, "This acknowledgement does not need another reply.")
            MessageSafety.SENSITIVE -> return review(AutomationReason.SENSITIVE_MESSAGE, "This message involves sensitive content or a possible commitment. Review is required.")
            MessageSafety.UNCERTAIN -> return review(AutomationReason.UNCERTAIN_MESSAGE, "VEIL cannot safely infer the answer. Review this suggestion.")
            MessageSafety.SMALLTALK -> Unit
        }
        if (input.draft.isNullOrBlank()) return review(AutomationReason.DRAFT_MISSING, "Generate and validate a reply before sending.")
        val draft = AutomationDraftValidator.validate(input.draft)
        if (!draft.valid || !draft.safeForAutomation) return review(AutomationReason.UNSAFE_DRAFT, draft.reason ?: "The generated reply needs your review.")
        if (input.generatorRequiresApproval) return review(AutomationReason.GENERATOR_REVIEW, "The reply engine requested your approval.")
        return AutomationDecision(AutomationDisposition.ALLOW, AutomationReason.SAFE_SMALLTALK,
            "A conservative greeting passed the configured recipient, safety, and reply-limit checks.")
    }
}
