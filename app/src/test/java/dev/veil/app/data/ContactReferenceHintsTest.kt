package dev.veil.app.data

import dev.veil.app.domain.MessagingApp
import org.junit.Assert.*
import org.junit.Test

class ContactReferenceHintsTest {
    @Test fun usernameNormalizationAndIdNeverBecomeCrossPlatformHints() {
        val input = ContactReferenceHints.sanitize(MessagingApp.INSTAGRAM, "@Friend_02", "123456789", "+919876543210")!!
        assertEquals("friend_02", input.instagramUsername)
        assertEquals("123456789", input.instagramUserId)
        assertEquals("", input.whatsappPhone)
        assertNull(ContactReferenceHints.sanitize(MessagingApp.INSTAGRAM, "bad handle!", "123", ""))
        assertNull(ContactReferenceHints.sanitize(MessagingApp.INSTAGRAM, "valid", "not-a-number", ""))
    }

    @Test fun phoneIsNormalizedAndInvalidValuesAreRejected() {
        val reference = ContactReferenceHints.sanitize(MessagingApp.WHATSAPP, "@someone", "123",
            "+91 98765-43210")!!
        assertEquals("+919876543210", reference.whatsappPhone)
        assertEquals("", reference.instagramUsername)
        assertEquals("", reference.instagramUserId)
        assertNull(ContactReferenceHints.sanitize(MessagingApp.WHATSAPP, "", "", "123"))
        assertNull(ContactReferenceHints.sanitize(MessagingApp.WHATSAPP, "", "", "+9abcdefghi"))
    }

    @Test fun optionalHintsRemainBlankUnlessOwnerSuppliesThem() {
        assertEquals(ContactReferenceHints(), ContactReferenceHints.sanitize(MessagingApp.WHATSAPP, "", "", ""))
        assertEquals(ContactReferenceHints(), ContactReferenceHints.sanitize(MessagingApp.INSTAGRAM, "", "", ""))
    }
}
