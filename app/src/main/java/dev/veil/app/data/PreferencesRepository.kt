package dev.veil.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.veil.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.veilDataStore by preferencesDataStore("veil_preferences")

class PreferencesRepository(context: Context) {
    private val store = context.veilDataStore
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val apps = stringSetPreferencesKey("enabled_apps")
        val paused = booleanPreferencesKey("capture_paused")
        val appearance = stringPreferencesKey("appearance")
        val motion = booleanPreferencesKey("reduce_motion")
        val glass = floatPreferencesKey("glass_intensity")
        val retention = intPreferencesKey("retention_days")
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
}
