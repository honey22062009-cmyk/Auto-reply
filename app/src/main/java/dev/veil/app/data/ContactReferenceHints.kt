package dev.veil.app.data

import dev.veil.app.domain.MessagingApp

/**
 * Owner-entered reference hints, never independent verification of notification identity.
 * In particular Android messaging notifications do not guarantee an Instagram numeric user ID
 * or the sender's WhatsApp telephone number. These values must never authorize sending.
 */
internal data class ContactReferenceHints(
    val instagramUsername: String = "",
    val instagramUserId: String = "",
    val whatsappPhone: String = "",
) {
    companion object {
        private val usernameFormat = Regex("[A-Za-z0-9._]{1,30}")
        private val numericIdFormat = Regex("[0-9]{1,32}")
        private val phoneFormat = Regex("\\+?[0-9]{7,15}")

        fun sanitize(app: MessagingApp, username: String, userId: String, phone: String): ContactReferenceHints? {
            return when (app) {
                MessagingApp.INSTAGRAM -> {
                    val handle = username.trim().removePrefix("@")
                    val id = userId.trim()
                    if ((handle.isNotEmpty() && !usernameFormat.matches(handle)) ||
                        (id.isNotEmpty() && !numericIdFormat.matches(id))) return null
                    ContactReferenceHints(instagramUsername = handle.lowercase(java.util.Locale.ROOT),
                        instagramUserId = id)
                }
                MessagingApp.WHATSAPP -> {
                    val number = phone.trim().replace(Regex("[ ()-]"), "")
                    if (number.isNotEmpty() && !phoneFormat.matches(number)) return null
                    ContactReferenceHints(whatsappPhone = number)
                }
            }
        }
    }
}
