package dev.veil.app.platform

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import dev.veil.app.domain.MessagingApp
import dev.veil.app.domain.ObservedNotification
import java.security.MessageDigest
import java.util.Locale

/** No notification extras or PendingIntents leave the platform boundary. */
internal data class InspectedNotification(
    val observation: ObservedNotification,
    val rawKey: String,
    val reply: ReplyAction?,
    val contentIntent: PendingIntent?,
)

internal data class ReplyAction(val intent: PendingIntent, val input: RemoteInput)

internal object NotificationInspector {
    private val hiddenPreview = Regex(
        "(?i)^(?:you have )?(?:(?:\\d+|a|one) )?new (?:messages?|notifications?)(?:[.!])?$"
    )
    private val genericSender = setOf("whatsapp", "instagram", "new message", "new messages")

    fun inspect(sbn: StatusBarNotification): InspectedNotification? {
        val app = MessagingApp.fromPackage(sbn.packageName) ?: return null
        val notification = sbn.notification
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        if (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE) return null

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val latest = style?.messages?.lastOrNull()
        // The framework represents an outgoing/local-user message with a null sender.
        if (latest != null && latest.person == null) return null
        if (latest != null && samePerson(latest.person, style?.user)) return null

        val extras = notification.extras
        val rawText = clean(latest?.text ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
        val preview = rawText?.takeUnless { hiddenPreview.matches(it) }
        val sender = clean(latest?.person?.name ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.takeUnless { it.lowercase(Locale.ROOT) in genericSender || hiddenPreview.matches(it) }
        // An empty non-message notification may be a sync/status alert, not a DM.
        if (style == null && notification.category == null && rawText == null) return null
        val conversation = clean(style?.conversationTitle ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        val isGroup = style?.isGroupConversation ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val messageAt = latest?.timestamp?.takeIf { it > 0L }
        val conversationIdentity = notification.shortcutId?.takeIf { it.isNotBlank() } ?: sbn.key
        val keyHash = digest(sbn.key)
        val senderIdentity = latest?.person?.key?.takeIf { it.isNotBlank() }
            ?: latest?.person?.uri?.takeIf { it.isNotBlank() }
            ?: sender.orEmpty()
        val id = digest(app.packageName, conversationIdentity, senderIdentity, sender.orEmpty(), preview.orEmpty(),
            messageAt?.toString().orEmpty())
        val reply = selectReply(notification.actions.orEmpty(), app.packageName)
        return InspectedNotification(
            observation = ObservedNotification(id, keyHash, app, sender, preview, sbn.postTime, messageAt,
                style != null, isGroup, notification.actions?.size ?: 0, reply != null, conversation),
            rawKey = sbn.key,
            reply = reply,
            contentIntent = notification.contentIntent?.takeIf { it.creatorPackage == app.packageName },
        )
    }

    /** Reject ambiguities instead of guessing from a localized action title. */
    fun selectReply(actions: Array<out Notification.Action>, expectedPackage: String): ReplyAction? {
        val candidates = actions.mapNotNull { action ->
            if (action.semanticAction != Notification.Action.SEMANTIC_ACTION_REPLY) return@mapNotNull null
            val intent = action.actionIntent ?: return@mapNotNull null
            if (intent.creatorPackage != expectedPackage) return@mapNotNull null
            if (Build.VERSION.SDK_INT >= 31 && (intent.isImmutable || intent.isActivity)) return@mapNotNull null
            val inputs = action.remoteInputs?.filter { it.allowFreeFormInput && it.resultKey.isNotBlank() }.orEmpty()
            if (inputs.size != 1) return@mapNotNull null
            ReplyAction(intent, inputs.single())
        }
        return candidates.singleOrNull()
    }

    private fun samePerson(sender: Person?, self: Person?): Boolean {
        if (sender == null || self == null) return false
        return (!sender.key.isNullOrBlank() && sender.key == self.key) ||
            (!sender.uri.isNullOrBlank() && sender.uri == self.uri)
    }

    private fun clean(value: CharSequence?): String? = value?.toString()?.trim()?.take(4096)?.takeIf { it.isNotEmpty() }

    fun digest(vararg values: String): String {
        val canonical = values.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
