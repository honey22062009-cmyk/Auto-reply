package dev.veil.app.data

import android.app.Application
import dev.veil.app.ai.AiConfiguration
import dev.veil.app.ai.AiProvider
import dev.veil.app.ai.WritingStyle
import dev.veil.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticReportTest {
    @Test fun `diagnostic export excludes personal fields secrets and exception details`() {
        val observation = ObservedNotification("secret-message-id", "secret-key-hash", MessagingApp.WHATSAPP,
            "Private Sender Name", "private body with otp 123456", 50, 40, true, false, 1, true,
            "private-conversation-id")
        val state = VeilUiState(
            messages = listOf(MessageRecord(observation, ReplyStatus.FAILED, false, 60, failureReason = "private-failure")),
            contacts = listOf(PriorityContact("private-contact-id", "private-label", MessagingApp.WHATSAPP, "private-contact-sender")),
            activity = listOf(ActivityEvent(1, 1, MessagingApp.WHATSAPP, "private-event", "private-detail")),
            notice = "sk-secret-api-key",
        )
        val export = DiagnosticReport.create(state)
        listOf("secret-message-id", "secret-key-hash", "Private Sender Name", "123456", "private body",
            "private-conversation-id", "private-failure", "private-contact-id", "private-label", "private-contact-sender",
            "private-event", "private-detail", "sk-secret-api-key").forEach { assertFalse("Leaked $it", export.contains(it)) }
        val parsed = JSONObject(export)
        assertFalse(parsed.getBoolean("automaticSending"))
        assertFalse(parsed.getBoolean("cloudProcessing"))
        assertEquals(2, parsed.getJSONArray("platforms").length())
    }

    @Test fun `accepted dispatch and user report never imply independent delivery verification`() {
        val observation = ObservedNotification("id", "key", MessagingApp.INSTAGRAM, "Test", "Hello", 50,
            40, true, false, 1, true)
        val export = DiagnosticReport.create(VeilUiState(messages = listOf(
            MessageRecord(observation, ReplyStatus.ACCEPTED, true, 60, 70, 80)
        )))
        val platform = JSONObject(export).getJSONArray("platforms").getJSONObject(1)
        assertEquals(1, platform.getInt("testAttemptCount"))
        assertEquals(1, platform.getInt("androidAcceptedCount"))
        assertEquals(1, platform.getInt("userReportedReceiptCount"))
        assertFalse(platform.getBoolean("independentlyVerifiedReceipt"))
        assertEquals(70, platform.getLong("lastAcceptedTestAt"))
    }

    @Test fun `unknown capabilities are absent or false rather than claimed supported`() {
        val export = JSONObject(DiagnosticReport.create(VeilUiState()))
        assertFalse(export.getBoolean("notificationAccess"))
        assertFalse(export.getBoolean("listenerConnected"))
        val platform = export.getJSONArray("platforms").getJSONObject(0)
        assertFalse(platform.getBoolean("installed"))
        assertFalse(platform.getBoolean("lastPreviewAccessible"))
        assertFalse(platform.getBoolean("lastSenderAvailable"))
        assertFalse(platform.getBoolean("lastReplyActionObserved"))
        assertTrue(platform.isNull("lastAcceptedTestAt"))
        assertTrue(platform.isNull("version"))
    }

    @Test fun `version one diagnostic flags show configuration while excluding all writing content`() {
        val preferences = VeilPreferences(automationEnabled=true,
            ai=AiConfiguration(provider=AiProvider.OPENAI,model="gpt-5.4-mini",maxOutputTokens=512,
                cloudConsent=true,includeContext=true),
            style=WritingStyle(preferredPhrases="private preferred wording",avoidPhrases="private avoided wording",
                approvedExamples=listOf("private explicitly approved example")))
        val export=DiagnosticReport.create(VeilUiState(preferences=preferences,hasApiKey=true,
            previewDraft="private preview draft",notice="private API-looking secret"))
        val parsed=JSONObject(export)
        assertEquals(2,parsed.getInt("schema"))
        assertEquals("Version 1.0",parsed.getString("milestone"))
        assertTrue(parsed.getBoolean("automaticSending"))
        assertTrue(parsed.getBoolean("cloudProcessing"))
        val ai=parsed.getJSONObject("ai")
        assertEquals("OPENAI",ai.getString("provider"))
        assertEquals("gpt-5.4-mini",ai.getString("model"))
        assertEquals(512,ai.getInt("maxOutputTokens"))
        assertTrue(ai.getBoolean("cloudConsent"))
        assertTrue(ai.getBoolean("includeContext"))
        assertTrue(ai.getBoolean("apiKeyAvailable"))
        listOf("private preferred wording","private avoided wording","private explicitly approved example",
            "private preview draft","private API-looking secret").forEach { assertFalse(export.contains(it)) }
        assertFalse(JSONObject(DiagnosticReport.create(VeilUiState(preferences=preferences,hasApiKey=false)))
            .getBoolean("cloudProcessing"))
        assertFalse(JSONObject(DiagnosticReport.create(VeilUiState(preferences=preferences.copy(
            ai=preferences.ai.copy(cloudConsent=false)),hasApiKey=true))).getBoolean("cloudProcessing"))
    }

    @Test fun `test manual and automatic attempts remain distinct and only tests set last successful test`() {
        val observation=ObservedNotification("private-id","private-key",MessagingApp.WHATSAPP,"Tester","hello",50,
            40,true,false,1,true,identityKey="private-identity")
        val records=listOf(
            MessageRecord(observation,ReplyStatus.ACCEPTED,false,60,70,dispatchKind="TEST"),
            MessageRecord(observation.copy(id="private-manual"),ReplyStatus.ACCEPTED,false,80,90,dispatchKind="MANUAL"),
            MessageRecord(observation.copy(id="private-auto"),ReplyStatus.ACCEPTED,false,100,110,dispatchKind="AUTO"),
            MessageRecord(observation.copy(id="private-legacy"),ReplyStatus.ACCEPTED,false,30,35),
        )
        val report=DiagnosticReport.create(VeilUiState(messages=records))
        val platform=JSONObject(report).getJSONArray("platforms").getJSONObject(0)
        assertEquals(4,platform.getInt("dispatchAttemptCount"))
        assertEquals(2,platform.getInt("testAttemptCount"))
        assertEquals(1,platform.getInt("manualAttemptCount"))
        assertEquals(1,platform.getInt("automaticAttemptCount"))
        assertEquals(2,platform.getInt("testAndroidAcceptedCount"))
        assertEquals(1,platform.getInt("manualAndroidAcceptedCount"))
        assertEquals(1,platform.getInt("automaticAndroidAcceptedCount"))
        assertEquals(70L,platform.getLong("lastAcceptedTestAt"))
        assertFalse(report.contains("private-identity"))
        val noTests=JSONObject(DiagnosticReport.create(VeilUiState(messages=records.filter {
            it.dispatchKind=="MANUAL" || it.dispatchKind=="AUTO"
        }))).getJSONArray("platforms").getJSONObject(0)
        assertTrue(noTests.isNull("lastAcceptedTestAt"))
    }

    @Test fun `credential looking model configuration is redacted rather than exported`() {
        val secret="sk-proj-0123456789abcdefghijklmnop"
        val report=DiagnosticReport.create(VeilUiState(preferences=VeilPreferences(ai=AiConfiguration(model=secret))))
        assertFalse(report.contains(secret))
        assertEquals("redacted",JSONObject(report).getJSONObject("ai").getString("model"))
    }
}
