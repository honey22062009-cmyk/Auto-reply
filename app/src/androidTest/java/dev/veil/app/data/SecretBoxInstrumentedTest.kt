package dev.veil.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android device/emulator: validates actual Android Keystore, not a fake cipher. */
@RunWith(AndroidJUnit4::class)
class SecretBoxInstrumentedTest {
    @Test fun envelopesAreRandomAuthenticatedAndReadableByNewInstance() {
        val box = SecretBox()
        val message = "Explicit synthetic test: kal milte hain 😊"
        val first = box.encrypt(message)!!
        val second = box.encrypt(message)!!
        assertNotEquals(first, second)
        assertFalse(first.contains(message))
        assertEquals(message, SecretBox().decrypt(first))
        val tampered = first.dropLast(4) + if (first.takeLast(4) == "AAAA") "BBBB" else "AAAA"
        assertNull(box.decrypt(tampered))
        assertNull(box.decrypt("not valid base64!"))
        assertNull(box.encrypt(null))
        assertNull(box.decrypt(null))
    }
}
