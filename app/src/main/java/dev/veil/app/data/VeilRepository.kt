package dev.veil.app.data

import androidx.room.withTransaction
import dev.veil.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Serialized state transitions and durable, at-most-once manual test attempts. */
class VeilRepository(
    private val database: VeilDatabase,
    private val cipher: TextCipher,
    private val dispatch: suspend (String) -> DispatchResult,
    private val forget: (String) -> Unit,
    private val clearActions: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.dao()
    private val mutex = Mutex()
    private var preferences = VeilPreferences()
    private var connection: Boolean? = null

    val messages = dao.observeMessages().map { rows -> rows.map(::decode) }
    val activity = dao.observeEvents().map { rows -> rows.map {
        ActivityEvent(it.id, it.at, it.app?.let(MessagingApp::valueOf), it.title, it.detail)
    } }
    val contacts = dao.observeContacts().map { rows -> rows.map(::decodeContact) }

    suspend fun initialize() = mutex.withLock {
        dao.deactivateAll() // PendingIntents never survive process death.
    }

    suspend fun configure(value: VeilPreferences) = mutex.withLock {
        preferences = value
        if (!value.onboarded || value.capturePaused) dao.deactivateAll()
        MessagingApp.entries.filter { it !in value.enabledApps }.forEach { dao.deactivateApp(it.name) }
        prune()
    }

    suspend fun record(observed: ObservedNotification) = mutex.withLock {
        if (!preferences.onboarded || preferences.capturePaused || observed.app !in preferences.enabledApps) return@withLock
        // Ignore stale history and keep a strict local retention boundary, including replay on reconnect.
        if (observed.postedAt < cutoff()) return@withLock
        database.withTransaction {
            val previous = dao.find(observed.id)
            dao.deactivateOthers(observed.keyHash, observed.id)
            if (previous == null) {
                val entity = MessageEntity(observed.id, observed.keyHash, observed.app.name,
                    cipher.encrypt(observed.sender), cipher.encrypt(observed.preview), cipher.encrypt(observed.conversationHint),
                    observed.postedAt, observed.messageAt, observed.isMessagingStyle, observed.isGroup,
                    observed.actionCount, observed.hasReplyAction, true,
                    if (observed.hasReplyAction) ReplyStatus.READY.name else ReplyStatus.UNAVAILABLE.name)
                dao.insertMessage(entity)
                event(observed.app, "Notification detected", "Notification fields inspected locally.")
                event(observed.app, if (observed.hasReplyAction) "Reply action detected" else "Reply action unavailable",
                    if (observed.hasReplyAction) "A manual compatibility test may be available. No message was sent."
                    else "Use copy and open the messaging app if needed.")
            } else {
                // Updated actions may change; durable attempt/dismissal states are never reset by a callback.
                val status = if (previous.attemptedAt != null || previous.status == ReplyStatus.DISMISSED.name) previous.status
                    else if (observed.hasReplyAction) ReplyStatus.READY.name else ReplyStatus.UNAVAILABLE.name
                dao.updateMessage(previous.copy(keyHash = observed.keyHash, active = true, hasReplyAction = observed.hasReplyAction,
                    actionCount = observed.actionCount, status = status))
            }
            prune()
        }
    }

    suspend fun removed(keyHash: String) = mutex.withLock {
        dao.markRemoved(keyHash)
    }

    suspend fun connectionChanged(connected: Boolean) = mutex.withLock {
        if (!connected) dao.deactivateAll()
        if (connection != connected) {
            connection = connected
            event(null, if (connected) "Notification listener connected" else "Notification listener disconnected",
                if (connected) "Only explicitly enabled platforms are inspected."
                else "Live reply actions were invalidated. Android controls reconnection.")
        }
    }

    suspend fun sendTest(id: String): String = mutex.withLock {
        val entity = dao.find(id) ?: return@withLock "This notification is no longer in local history."
        PhaseOnePolicy.rejection(decode(entity), preferences)?.let { return@withLock it }
        val attemptedAt = now()
        // Commit BEFORE IPC. A crash or uncertain outcome must not allow another send.
        database.withTransaction {
            dao.updateMessage(entity.copy(status = ReplyStatus.DISPATCHING.name, attemptedAt = attemptedAt))
            event(MessagingApp.valueOf(entity.app), "Reply dispatch attempted", "User confirmed the fixed compatibility test.")
        }
        val result = try { dispatch(id) } catch (cancelled: CancellationException) {
            throw cancelled // Durable DISPATCHING state remains uncertain; never retry after cancellation.
        } catch (_: Exception) {
            DispatchResult.Rejected("The platform could not complete dispatch. No automatic retry will occur.")
        }
        when (result) {
            DispatchResult.Accepted -> {
                dao.updateMessage(entity.copy(status = ReplyStatus.ACCEPTED.name, attemptedAt = attemptedAt, acceptedAt = now()))
                event(MessagingApp.valueOf(entity.app), "Dispatch accepted by Android", "Receipt is unverified. Ask the consenting contact to check.")
                "Android accepted the test reply. Recipient receipt is not verified."
            }
            is DispatchResult.Rejected -> {
                // Persist only controlled, non-sensitive explanations from the platform adapter.
                dao.updateMessage(entity.copy(status = ReplyStatus.FAILED.name, attemptedAt = attemptedAt, failureReason = result.reason.take(300)))
                event(MessagingApp.valueOf(entity.app), "Reply dispatch failed", "No automatic retry. Start again with a new notification.")
                result.reason
            }
        }
    }

    suspend fun confirmReceipt(id: String): String = mutex.withLock {
        val row = dao.find(id) ?: return@withLock "Notification not found."
        if (row.acceptedAt == null) return@withLock "There is no accepted dispatch to confirm."
        if (row.receiptReportedAt == null) {
            dao.updateMessage(row.copy(receiptReportedAt = now()))
            event(MessagingApp.valueOf(row.app), "Receipt reported by user", "User says the consenting test contact confirmed receipt. Not independently verified.")
        }
        "Receipt recorded as user-reported, not independently verified."
    }

    suspend fun dismiss(id: String) = mutex.withLock {
        dao.find(id)?.let { dao.updateMessage(it.copy(status = ReplyStatus.DISMISSED.name, active = false)) }
        forget(id)
    }

    suspend fun deleteHistory() = mutex.withLock {
        clearActions()
        database.withTransaction { dao.deleteMessages(); dao.deleteEvents() }
    }

    suspend fun addContact(label: String, app: MessagingApp, sender: String): String = mutex.withLock {
        val cleanLabel = label.trim(); val cleanSender = sender.trim()
        if (cleanLabel.isBlank() || cleanSender.isBlank()) return@withLock "Enter a local label and an exact visible sender name."
        if (cleanLabel.length > 80 || cleanSender.length > 160) return@withLock "Use a shorter label or sender name."
        if (dao.contacts().any { it.app == app.name && cipher.decrypt(it.sender).equals(cleanSender, ignoreCase = true) })
            return@withLock "That sender name is already saved for this platform. Names are not verified identities."
        dao.insertContact(ContactEntity(UUID.randomUUID().toString(), cipher.encrypt(cleanLabel)!!,
            app.name, cipher.encrypt(cleanSender)!!, now()))
        "Contact saved locally. Automatic replies remain off."
    }

    suspend fun removeContact(id: String) = mutex.withLock { dao.deleteContact(id) }

    private suspend fun event(app: MessagingApp?, title: String, detail: String) {
        dao.insertEvent(EventEntity(at = now(), app = app?.name, title = title, detail = detail))
        dao.boundEvents()
    }
    private fun cutoff() = now() - preferences.retentionDays * 86_400_000L
    private suspend fun prune() {
        dao.purgeMessages(cutoff()); dao.purgeEvents(cutoff()); dao.boundMessages(); dao.boundEvents()
    }
    private fun decodeContact(row: ContactEntity) = PriorityContact(row.id,
        cipher.decrypt(row.label) ?: "Unavailable encrypted label", MessagingApp.valueOf(row.app),
        cipher.decrypt(row.sender) ?: "")
    private fun decode(row: MessageEntity) = MessageRecord(
        ObservedNotification(row.id, row.keyHash, MessagingApp.valueOf(row.app), cipher.decrypt(row.sender),
            cipher.decrypt(row.preview), row.postedAt, row.messageAt, row.messagingStyle, row.isGroup,
            row.actionCount, row.hasReplyAction, cipher.decrypt(row.conversation)),
        ReplyStatus.valueOf(row.status), row.active, row.attemptedAt, row.acceptedAt,
        row.receiptReportedAt, row.failureReason)
}
