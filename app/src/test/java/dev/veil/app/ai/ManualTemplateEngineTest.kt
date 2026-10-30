package dev.veil.app.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManualTemplateEngineTest {
    private val engine = ManualTemplateEngine()

    @Test fun `exact casual greeting is deterministic and does not invent feelings`() = runBlocking {
        val request = GenerationRequest("Hey!", style = WritingStyle(language = ReplyLanguage.ENGLISH, emoji = 0))
        val result = engine.generate(request) as GenerationResult.Success
        assertEquals("Hey!", result.draft)
        assertFalse(result.requiresApproval)
        assertEquals(AiProvider.TEMPLATE, result.provider)
        assertEquals(result, engine.generate(request))
        assertNull(result.estimatedCostUsd)
    }

    @Test fun `Hindi and Hinglish templates follow explicit language and emoji choices`() = runBlocking {
        val hindi = engine.generate(GenerationRequest("धन्यवाद", style = WritingStyle(language = ReplyLanguage.HINDI, emoji = 0))) as GenerationResult.Success
        val hinglish = engine.generate(GenerationRequest("thanks yaar", style = WritingStyle(language = ReplyLanguage.HINGLISH, emoji = 70))) as GenerationResult.Success
        assertEquals("कोई बात नहीं!", hindi.draft)
        assertEquals("Arre, koi baat nahi! 😊", hinglish.draft)
        assertFalse(hindi.requiresApproval)
        assertFalse(hinglish.requiresApproval)
    }

    @Test fun `unknown availability cannot be answered with an invented promise`() = runBlocking {
        val result = engine.generate(GenerationRequest("Are you free tomorrow?", style = WritingStyle(language = ReplyLanguage.ENGLISH))) as GenerationResult.Success
        assertTrue(result.requiresApproval)
        assertTrue(result.warnings.any { it.contains("cannot verify") })
        assertFalse(result.draft.contains("yes", ignoreCase = true))
        assertFalse(result.draft.contains("tomorrow", ignoreCase = true))
    }

    @Test fun `financial and serious messages require personal approval`() = runBlocking {
        val result = engine.generate(GenerationRequest("Please transfer money to my bank", style = WritingStyle(language = ReplyLanguage.ENGLISH))) as GenerationResult.Success
        assertTrue(result.requiresApproval)
        assertTrue(result.warnings.any { it.contains("sensitive") })
        assertFalse(result.draft.contains("😊"))
        assertFalse(result.draft.contains("sent"))
    }

    @Test fun `credentials and empty input do not create sendable drafts`() = runBlocking {
        assertEquals(AiFailureCode.EMPTY_INPUT, (engine.generate(GenerationRequest("   ")) as GenerationResult.Failure).code)
        assertEquals(AiFailureCode.SENSITIVE_INPUT, (engine.generate(GenerationRequest("Your OTP is 123456")) as GenerationResult.Failure).code)
    }

    @Test fun `acknowledgments stay approval only to avoid endless bot loops`() = runBlocking {
        val result = engine.generate(GenerationRequest("okay")) as GenerationResult.Success
        assertTrue(result.requiresApproval)
    }

    @Test fun `draft validation rejects credential output and control spoofing`() {
        val style = WritingStyle()
        assertNotNull(DraftSafety.validate("Your password is abc", style))
        assertNotNull(DraftSafety.validate("Hello\u202E", style))
        assertNotNull(DraftSafety.validate("hello\u0000there", style))
        assertNotNull(DraftSafety.validate("hello\u0085there", style))
        assertNotNull(DraftSafety.validate("123456", style))
        assertNotNull(DraftSafety.validate("a".repeat(241), style))
        assertNotNull(DraftSafety.validate("my-private-key-value", style, "my-private-key-value"))
        assertNull(DraftSafety.validate("नमस्ते! 😊", style))
        assertNull(DraftSafety.validate("Hello\nHow are you?", style))
    }
}
