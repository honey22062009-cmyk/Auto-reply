package dev.veil.app.domain

import org.junit.Assert.*
import org.junit.Test

class PhaseOnePolicyTest {
    private val enabled = VeilPreferences(onboarded = true, enabledApps = setOf(MessagingApp.WHATSAPP))
    private val ready = MessageRecord(
        observation = ObservedNotification(
            id = "message-fingerprint", keyHash = "notification-hash", app = MessagingApp.WHATSAPP,
            sender = "Consenting test contact", preview = "Please test now", postedAt = 100L,
            messageAt = 99L, isMessagingStyle = true, isGroup = false, actionCount = 1,
            hasReplyAction = true,
        ), status = ReplyStatus.READY, active = true,
    )

    @Test fun `first explicit test can pass only after setup with enabled platform`() {
        assertNull(PhaseOnePolicy.rejection(ready, enabled))
        assertNotNull(PhaseOnePolicy.rejection(ready, VeilPreferences()))
        assertNotNull(PhaseOnePolicy.rejection(ready, enabled.copy(onboarded = false)))
        assertNotNull(PhaseOnePolicy.rejection(ready, enabled.copy(enabledApps = emptySet())))
        assertNotNull(PhaseOnePolicy.rejection(ready.copy(observation = ready.observation.copy(app = MessagingApp.INSTAGRAM)), enabled))
    }

    @Test fun `global pause blocks a previously available test`() {
        assertNotNull(PhaseOnePolicy.rejection(ready, enabled.copy(capturePaused = true)))
    }

    @Test fun `all persisted attempt outcomes forbid another dispatch even after restart`() {
        for (status in listOf(ReplyStatus.DISPATCHING, ReplyStatus.ACCEPTED, ReplyStatus.FAILED, ReplyStatus.REMOVED)) {
            val restored = ready.copy(status = status, dispatchAttemptedAt = 101L)
            assertNotNull("$status must not permit a retry", PhaseOnePolicy.rejection(restored, enabled))
        }
    }

    @Test fun `notification removal and local dismissal invalidate test`() {
        assertNotNull(PhaseOnePolicy.rejection(ready.copy(active = false), enabled))
        assertNotNull(PhaseOnePolicy.rejection(ready.copy(status = ReplyStatus.DISMISSED), enabled))
    }

    @Test fun `groups have no reply permission even with a direct reply action`() {
        assertNotNull(PhaseOnePolicy.rejection(ready.copy(observation = ready.observation.copy(isGroup = true)), enabled))
    }

    @Test fun `hidden missing and blank sender or preview require opening source app`() {
        for (missing in listOf(null, "", " \t\n")) {
            assertNotNull(PhaseOnePolicy.rejection(ready.copy(observation = ready.observation.copy(sender = missing)), enabled))
            assertNotNull(PhaseOnePolicy.rejection(ready.copy(observation = ready.observation.copy(preview = missing)), enabled))
        }
    }

    @Test fun `notification alone is not sufficient without supported reply action`() {
        assertNotNull(PhaseOnePolicy.rejection(ready.copy(observation = ready.observation.copy(hasReplyAction = false)), enabled))
    }

    @Test fun `template is recognizable deterministic and independent of private content`() {
        val engine: ReplyEngine = ManualTemplateReplyEngine()
        assertEquals("VEIL compatibility test. Please confirm you received this message.", engine.testReply())
        repeat(10) { assertEquals(TEST_REPLY, engine.testReply()) }
    }

    @Test fun `only official personal app packages are supported`() {
        assertEquals(MessagingApp.WHATSAPP, MessagingApp.fromPackage("com.whatsapp"))
        assertEquals(MessagingApp.INSTAGRAM, MessagingApp.fromPackage("com.instagram.android"))
        assertNull(MessagingApp.fromPackage("com.whatsapp.w4b"))
        assertNull(MessagingApp.fromPackage("com.whatsapp.clone"))
        assertNull(MessagingApp.fromPackage("COM.WHATSAPP"))
    }

    @Test fun `fresh preferences cannot authorize collection or automation`() {
        val defaults = VeilPreferences()
        assertFalse(defaults.onboarded)
        assertTrue(defaults.enabledApps.isEmpty())
        assertEquals(1, defaults.retentionDays)
        assertNotNull(PhaseOnePolicy.rejection(ready, defaults))
    }

    @Test fun `framework acceptance and human receipt remain independent`() {
        val accepted = ready.copy(status = ReplyStatus.ACCEPTED, dispatchAttemptedAt = 101L, dispatchAcceptedAt = 102L)
        assertNull(accepted.receiptReportedAt)
        assertEquals("Accepted by Android", accepted.status.label)
    }
}
