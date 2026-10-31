package dev.veil.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.veil.app.domain.*
import dev.veil.app.ai.*
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.veilDataStore by preferencesDataStore("veil_preferences")

class PreferencesRepository(context: Context, private val cipher: TextCipher = SecretBox()) {
    private val store = context.veilDataStore
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val apps = stringSetPreferencesKey("enabled_apps")
        val paused = booleanPreferencesKey("capture_paused")
        val appearance = stringPreferencesKey("appearance")
        val motion = booleanPreferencesKey("reduce_motion")
        val glass = floatPreferencesKey("glass_intensity")
        val retention = intPreferencesKey("retention_days")
        val accent = stringPreferencesKey("accent")
        val companion = booleanPreferencesKey("companion")
        val automation = booleanPreferencesKey("automation")
        val ai = stringPreferencesKey("ai_configuration")
        val style = stringPreferencesKey("encrypted_style")
    }
    val preferences: Flow<VeilPreferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { p -> VeilPreferences(
        onboarded = p[Keys.onboarded] ?: false,
        enabledApps = p[Keys.apps].orEmpty().mapNotNull { name -> MessagingApp.entries.firstOrNull { it.name == name } }.toSet(),
        capturePaused = p[Keys.paused] ?: false,
        appearance = Appearance.entries.firstOrNull { it.name == p[Keys.appearance] } ?: Appearance.SYSTEM,
        reduceMotion = p[Keys.motion] ?: false,
        glassIntensity = (p[Keys.glass] ?: 0.65f).coerceIn(0f, 1f),
        retentionDays = (p[Keys.retention] ?: 1).coerceIn(1, 7),
        accent = AccentPalette.entries.firstOrNull { it.name == p[Keys.accent] } ?: AccentPalette.AURORA,
        companionEnabled = p[Keys.companion] ?: false,
        automationEnabled = p[Keys.automation] ?: false,
        ai = decodeAi(p[Keys.ai]),
        style = decodeStyle(cipher.decrypt(p[Keys.style])),
    ) }

    suspend fun finishOnboarding(apps: Set<MessagingApp>) { store.edit {
        it[Keys.onboarded] = true; it[Keys.apps] = apps.map { app -> app.name }.toSet()
    } }
    suspend fun setPaused(value: Boolean) { store.edit { it[Keys.paused] = value } }
    suspend fun setApp(app: MessagingApp, enabled: Boolean) { store.edit {
        val current = it[Keys.apps].orEmpty()
        it[Keys.apps] = if (enabled) current + app.name else current - app.name
    } }
    suspend fun setAppearance(value: Appearance) { store.edit { it[Keys.appearance] = value.name } }
    suspend fun setMotion(value: Boolean) { store.edit { it[Keys.motion] = value } }
    suspend fun setGlass(value: Float) { store.edit { it[Keys.glass] = value.coerceIn(0f, 1f) } }
    suspend fun setRetention(value: Int) { store.edit { it[Keys.retention] = value.coerceIn(1, 7) } }
    suspend fun setAccent(value: AccentPalette) { store.edit { it[Keys.accent] = value.name } }
    suspend fun setCompanion(value: Boolean) { store.edit { it[Keys.companion] = value } }
    suspend fun setAutomation(value: Boolean) { store.edit { it[Keys.automation] = value } }
    suspend fun saveAi(value: AiConfiguration) {
        require(value.model.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._:-]{0,79}"))) { "Invalid model" }
        require(value.maxOutputTokens in 64..1024)
        store.edit { it[Keys.ai] = JSONObject().put("provider", value.provider.name)
            .put("model", value.model).put("tokens", value.maxOutputTokens)
            .put("consent", value.cloudConsent).put("context", value.includeContext).toString() }
    }
    suspend fun saveStyle(value: WritingStyle) {
        require(value.maxCharacters in 40..1000)
        val json = JSONObject().put("language", value.language.name).put("warmth", value.warmth.coerceIn(0,100))
            .put("humor", value.humor.coerceIn(0,100)).put("emoji", value.emoji.coerceIn(0,100))
            .put("length", value.maxCharacters).put("preferred", value.preferredPhrases.take(512))
            .put("avoid", value.avoidPhrases.take(512)).put("examples", JSONArray(value.approvedExamples.take(12).map { it.take(500) }))
        store.edit { it[Keys.style] = cipher.encrypt(json.toString())!! }
    }
    private fun decodeAi(json: String?): AiConfiguration = runCatching {
        if (json == null) return AiConfiguration()
        val j = JSONObject(json)
        AiConfiguration(AiProvider.valueOf(j.getString("provider")), j.getString("model"),
            j.getInt("tokens").coerceIn(64,1024), j.optBoolean("consent"), j.optBoolean("context"))
    }.getOrDefault(AiConfiguration())
    private fun decodeStyle(json: String?): WritingStyle = runCatching {
        if (json == null) return WritingStyle()
        val j = JSONObject(json); val examples = j.optJSONArray("examples") ?: JSONArray()
        WritingStyle(ReplyLanguage.valueOf(j.getString("language")), j.optInt("warmth",60).coerceIn(0,100),
            j.optInt("humor",15).coerceIn(0,100), j.optInt("emoji",20).coerceIn(0,100),
            j.optInt("length",240).coerceIn(40,1000),j.optString("preferred").take(512),j.optString("avoid").take(512),
            (0 until minOf(examples.length(),12)).map { examples.getString(it).take(500) })
    }.getOrDefault(WritingStyle())
}
