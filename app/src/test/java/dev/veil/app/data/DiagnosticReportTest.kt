package dev.veil.app.data

import android.app.Application
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
}
