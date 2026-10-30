package dev.veil.app.ai

import java.text.Normalizer

enum class AiProvider { TEMPLATE, OPENAI }
enum class ReplyLanguage { ENGLISH, HINDI, HINGLISH }
enum class ContextRole { INCOMING, OWN_REPLY }

data class WritingStyle(
    val language: ReplyLanguage = ReplyLanguage.HINGLISH,
    val warmth: Int = 60,
    val humor: Int = 15,
    val emoji: Int = 20,
    val maxCharacters: Int = 240,
    val preferredPhrases: String = "",
    val avoidPhrases: String = "",
    val approvedExamples: List<String> = emptyList(),
)

data class AiConfiguration(
    val provider: AiProvider = AiProvider.TEMPLATE,
    val model: String = "gpt-5.4-mini",
    val maxOutputTokens: Int = 256,
    val cloudConsent: Boolean = false,
    val includeContext: Boolean = false,
)

data class ContextTurn(val role: ContextRole, val text: String)
data class GenerationRequest(
    val incoming: String,
    val context: List<ContextTurn> = emptyList(),
    val style: WritingStyle = WritingStyle(),
    val contactInstruction: String = "",
)

enum class AiFailureCode {
    CONSENT_REQUIRED, KEY_REQUIRED, INVALID_CONFIGURATION, EMPTY_INPUT, SENSITIVE_INPUT,
    AUTHENTICATION, RATE_LIMITED, TIMEOUT, NETWORK, PROVIDER_UNAVAILABLE,
    REFUSED, INCOMPLETE, INVALID_RESPONSE, UNSAFE_OUTPUT,
}

sealed interface GenerationResult {
    data class Success(
        val draft: String,
        val provider: AiProvider,
        val model: String,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        /** Unknown unless the owner supplies verified pricing elsewhere; never a fabricated quote. */
        val estimatedCostUsd: Double? = null,
        val requiresApproval: Boolean = true,
        val warnings: List<String> = emptyList(),
    ) : GenerationResult
    data class Failure(
        val code: AiFailureCode,
        val message: String,
        val retryAfterSeconds: Int? = null,
    ) : GenerationResult
}

interface ReplyEngineV1 { suspend fun generate(request: GenerationRequest): GenerationResult }

/** Small deterministic classifier, not a claim of semantic certainty or model-based moderation. */
object DraftSafety {
    private val credential = Regex("(?i)\\b(?:otp|one[ -]time(?: password| code)?|verification code|security code|password|passcode|pin code|cvv)\\b|पासवर्ड|ओटीपी|सत्यापन कोड")
    private val apiSecret = Regex("(?i)\\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{20,})")
    // A notification may contain a code without an English OTP label. Fail closed for these
    // short numeric sequences, including non-ASCII decimal digits, before cloud transmission.
    private val possibleCode = Regex("(?<![\\p{L}\\p{N}])\\p{Nd}{4,8}(?![\\p{L}\\p{N}])")
    private val sensitive = Regex("(?i)\\b(?:money|payment|pay|transfer|bank|loan|rupees|dollars|emergency|suicide|kill|threat|harass|hospital|medicine|medical|diagnos|pregnan|break[ -]?up|divorce|marry|commit|promise|guarantee|contract|agree|address|location|salary|legal|police)\\w*|पैस[ेा]|भुगतान|अस्पताल|दवा|शादी|धमकी")
    private val commitment = Regex("(?i)\\b(?:i(?:'|’)ll|i will|we will|i promise|i agree|i guarantee|definitely|pakka|zaroor|kal mil|i am free|i'm free|i sent|i paid)\\b|वादा|पक्का|ज़रूर|भेज दिया")
    private val personalQuestion = Regex("(?i)\\b(?:where|when|what time|how much|are you|can you|could you|will you|do you|did you|have you|remember|kab|kahan|kitna|aaoge|free ho|miloge)\\b|कहाँ|कब|कितन[ाीे]|आओगे|मिलोगे")

    fun containsCredentials(text: String): Boolean {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[\\p{Cf}]"), "")
        return credential.containsMatchIn(normalized) || apiSecret.containsMatchIn(normalized) ||
            possibleCode.containsMatchIn(normalized)
    }
    fun approvalReasons(incoming: String, draft: String): List<String> = buildList {
        if (sensitive.containsMatchIn(incoming) || sensitive.containsMatchIn(draft)) add("This may involve a sensitive subject; review personally.")
        if (commitment.containsMatchIn(draft)) add("The draft may make a commitment or claim an action; verify it before sending.")
        if (personalQuestion.containsMatchIn(incoming)) add("The question may require facts or availability VEIL cannot verify.")
    }

    fun validate(draft: String, style: WritingStyle, apiKey: String = ""): String? = when {
        draft.isBlank() -> "The provider returned an empty draft."
        draft.length > style.maxCharacters.coerceIn(40, 2000) -> "The draft exceeds your configured message length."
        draft.any { (it.isISOControl() && it != '\n') || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 } -> "The draft contains unsupported control characters."
        apiKey.isNotBlank() && draft.contains(apiKey) -> "The draft contains sensitive credential-like text."
        containsCredentials(draft) -> "The draft contains sensitive credential-like text."
        else -> null
    }
}
