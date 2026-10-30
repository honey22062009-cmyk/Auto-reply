package dev.veil.app.data

import android.content.Context

/** Only an authenticated encryption envelope enters private preferences. */
class ApiKeyStore(context: Context, private val cipher: TextCipher = SecretBox()) {
    private val store = context.getSharedPreferences("veil_secrets", Context.MODE_PRIVATE)
    @Synchronized fun read(): String? = cipher.decrypt(store.getString("openai", null))?.takeIf { it.isNotBlank() }
    @Synchronized fun hasKey() = read() != null
    @Synchronized fun save(value: String) {
        require(value.trim().length in 12..512 && value.none { it.isISOControl() })
        check(store.edit().putString("openai",cipher.encrypt(value.trim())).commit())
    }
    @Synchronized fun delete() { check(store.edit().remove("openai").commit()) }
}
