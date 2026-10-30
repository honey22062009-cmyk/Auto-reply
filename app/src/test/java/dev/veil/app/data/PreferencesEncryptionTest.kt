package dev.veil.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.veil.app.ai.*
import dev.veil.app.domain.AccentPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Validates persistence boundaries using an opaque fake, not a claim of Keystore execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class PreferencesEncryptionTest {
    @Test fun `style examples enter DataStore only as opaque encrypted text and restore explicitly`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val cipher=OpaqueCipher()
        val repository=PreferencesRepository(context,cipher)
        val privateExample="Explicitly approved private writing example"
        val style=WritingStyle(language=ReplyLanguage.HINDI,warmth=72,humor=18,emoji=40,maxCharacters=360,
            preferredPhrases="Synthetic preferred phrase",avoidPhrases="Synthetic avoided phrase",approvedExamples=listOf(privateExample))
        repository.saveStyle(style)
        repository.setAccent(AccentPalette.AMBER)
        repository.setCompanion(false)
        val restored=PreferencesRepository(context,cipher).preferences.first()
        assertEquals(style,restored.style)
        assertEquals(AccentPalette.AMBER,restored.accent)
        assertFalse(restored.companionEnabled)
        assertFalse(restored.ai.cloudConsent)
        val file=context.filesDir.resolve("datastore/veil_preferences.preferences_pb")
        assertTrue(file.exists())
        val stored=file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(stored.contains(privateExample))
        assertFalse(stored.contains(style.preferredPhrases))
        assertFalse(stored.contains(style.avoidPhrases))
    }

    @Test fun `cloud selection and cloud sharing permission remain separate saved choices`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=PreferencesRepository(context,OpaqueCipher())
        assertEquals(AiProvider.TEMPLATE,repository.preferences.first().ai.provider)
        assertFalse(repository.preferences.first().automationEnabled)
        val selected=AiConfiguration(provider=AiProvider.OPENAI,cloudConsent=false,includeContext=false)
        repository.saveAi(selected)
        val stored=repository.preferences.first().ai
        assertEquals(AiProvider.OPENAI,stored.provider)
        assertFalse(stored.cloudConsent)
        assertFalse(stored.includeContext)
        assertTrue(runCatching { repository.saveAi(selected.copy(model="../invalid/model")) }.isFailure)
        assertTrue(runCatching { repository.saveAi(selected.copy(maxOutputTokens=4096)) }.isFailure)
        assertEquals(selected,repository.preferences.first().ai)
    }

    @Test fun `API key vault stores only encrypted value and supports deletion across instances`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val cipher=OpaqueCipher()
        val key="synthetic-not-a-real-provider-secret"
        val vault=ApiKeyStore(context,cipher)
        assertFalse(vault.hasKey())
        vault.save(key)
        assertTrue(vault.hasKey())
        assertEquals(key,ApiKeyStore(context,cipher).read())
        val fields=context.getSharedPreferences("veil_secrets",Context.MODE_PRIVATE).all
        assertEquals(1,fields.size)
        assertFalse(fields.values.any { it.toString().contains(key) })
        vault.delete()
        assertFalse(ApiKeyStore(context,cipher).hasKey())
        assertTrue(context.getSharedPreferences("veil_secrets",Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun `failed encryption cannot replace an existing saved key with plaintext`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val cipher=OpaqueCipher()
        val original=ApiKeyStore(context,cipher)
        original.save("synthetic-original-provider-secret")
        val broken=object:TextCipher {
            override fun encrypt(value:String?):String? = error("Synthetic encryption failure")
            override fun decrypt(value:String?):String? = null
        }
        assertTrue(runCatching { ApiKeyStore(context,broken).save("synthetic-replacement-provider-secret") }.isFailure)
        assertEquals("synthetic-original-provider-secret",original.read())
        assertFalse(ApiKeyStore(context,broken).hasKey())
    }

    private class OpaqueCipher:TextCipher {
        private val values=mutableMapOf<String,String>()
        override fun encrypt(value:String?):String? = value?.let { "opaque-${values.size}".also { token -> values[token]=it } }
        override fun decrypt(value:String?):String? = values[value]
    }
}
