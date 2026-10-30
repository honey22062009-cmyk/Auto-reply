package dev.veil.app.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** Offline reply suggestions with explicit limits: this is not an on-device language model. */
class ManualTemplateEngine : ReplyEngineV1 {
    override suspend fun generate(request: GenerationRequest): GenerationResult {
        currentCoroutineContext().ensureActive()
        val incoming = request.incoming.trim()
        if (incoming.isEmpty()) return GenerationResult.Failure(AiFailureCode.EMPTY_INPUT, "No message text is available to suggest a reply.")
        if (DraftSafety.containsCredentials(incoming)) return GenerationResult.Failure(AiFailureCode.SENSITIVE_INPUT, "Security codes and credential-related messages require your personal reply.")
        val normalized = incoming.lowercase(Locale.ROOT).trim(' ', '!', '.', '?', '।')
        val greeting = normalized.matches(Regex("(?:hi|hii+|hey+|hello|hola|namaste|namaskar|नमस्ते|नमस्कार|good morning|good evening|good afternoon|gm)"))
        val thanks = normalized.matches(Regex("(?:thanks|thank you|thankyou|thx|ty|shukriya|dhanyavad|धन्यवाद)(?: (?:so much|yaar|bro))?"))
        val goodbye = normalized.matches(Regex("(?:bye|goodbye|good night|gn|take care|शुभ रात्रि|अलविदा)"))
        val acknowledgment = normalized.matches(Regex("(?:ok|okay|alright|thik hai|theek hai|ठीक है)"))
        val reasons = DraftSafety.approvalReasons(incoming, "")
        val emoji = request.style.emoji.coerceIn(0, 100) >= 25
        val plain = when (request.style.language) {
            ReplyLanguage.ENGLISH -> when {
                greeting -> "Hey!"
                thanks -> "You're welcome!"
                goodbye -> "Take care!"
                acknowledgment -> "Got it."
                reasons.isNotEmpty() -> "This needs a personal reply from me."
                else -> "I need to check before confirming."
            }
            ReplyLanguage.HINDI -> when {
                greeting -> "नमस्ते!"
                thanks -> "कोई बात नहीं!"
                goodbye -> "अपना ख्याल रखना!"
                acknowledgment -> "ठीक है।"
                reasons.isNotEmpty() -> "इस पर मुझे खुद ध्यान से जवाब देना होगा।"
                else -> "पुष्टि करने से पहले मुझे देखना होगा।"
            }
            ReplyLanguage.HINGLISH -> when {
                greeting -> "Hey!"
                thanks -> "Arre, koi baat nahi!"
                goodbye -> "Apna khayal rakhna!"
                acknowledgment -> "Theek hai."
                reasons.isNotEmpty() -> "Iska jawab mujhe khud dhyan se dena hoga."
                else -> "Confirm karne se pehle mujhe check karna hoga."
            }
        }
        val simple = greeting || thanks || goodbye
        val draft = plain + if (emoji && simple) " 😊" else ""
        DraftSafety.validate(draft, request.style)?.let {
            return GenerationResult.Failure(AiFailureCode.UNSAFE_OUTPUT, "The template does not fit your length setting. Increase the limit or write a reply manually.")
        }
        return GenerationResult.Success(draft, AiProvider.TEMPLATE, "offline-templates-v1",
            requiresApproval = !simple || reasons.isNotEmpty(),
            warnings = reasons + if (simple) emptyList() else listOf("Template suggestion only; missing context needs your review."))
    }
}
