package dev.veil.app.data

import androidx.room.withTransaction
import dev.veil.app.domain.*
import dev.veil.app.ai.*
import java.time.Instant
import java.time.ZoneId
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
    private val replyDispatch: suspend (String, String, Boolean) -> DispatchResult = { _, _, _ -> DispatchResult.Rejected("Reply dispatch is unavailable.") },
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
        dao.messagesSnapshot(500).filter { it.generationState == "GENERATING" }.forEach {
            dao.updateMessage(it.copy(generationState = "FAILED", generationReason = "Generation was interrupted. Generate again manually."))
        }
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
                    cipher.encrypt(observed.sender), cipher.encrypt(observed.preview?.takeUnless(DraftSafety::containsCredentials)), cipher.encrypt(observed.conversationHint),
                    observed.postedAt, observed.messageAt, observed.isMessagingStyle, observed.isGroup,
                    observed.actionCount, observed.hasReplyAction, true,
                    if (observed.hasReplyAction) ReplyStatus.READY.name else ReplyStatus.UNAVAILABLE.name,
                    identityKey = observed.identityKey)
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
                    actionCount = observed.actionCount, status = status, identityKey = observed.identityKey))
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
        if (dao.findAttempt(id) != null) return@withLock "This message already had a dispatch attempt. Ask for a new message."
        PhaseOnePolicy.rejection(decode(entity), preferences)?.let { return@withLock it }
        val attemptedAt = now()
        // Commit BEFORE IPC. A crash or uncertain outcome must not allow another send.
        database.withTransaction {
            check(dao.insertAttempt(ReplyAttemptEntity(id,null,entity.identityKey,entity.app,"TEST",attemptedAt)) != -1L)
            dao.updateMessage(entity.copy(status = ReplyStatus.DISPATCHING.name, attemptedAt = attemptedAt, dispatchKind = "TEST"))
            event(MessagingApp.valueOf(entity.app), "Reply dispatch attempted", "User confirmed the fixed compatibility test.")
        }
        val result = try { dispatch(id) } catch (cancelled: CancellationException) {
            throw cancelled // Durable DISPATCHING state remains uncertain; never retry after cancellation.
        } catch (_: Exception) {
            DispatchResult.Rejected("The platform could not complete dispatch. No automatic retry will occur.")
        }
        when (result) {
            DispatchResult.Accepted -> {
                dao.updateAttempt(ReplyAttemptEntity(id,null,entity.identityKey,entity.app,"TEST",attemptedAt,now()))
                dao.updateMessage(entity.copy(status = ReplyStatus.ACCEPTED.name, attemptedAt = attemptedAt, acceptedAt = now(),dispatchKind = "TEST"))
                event(MessagingApp.valueOf(entity.app), "Dispatch accepted by Android", "Receipt is unverified. Ask the consenting contact to check.")
                "Android accepted the test reply. Recipient receipt is not verified."
            }
            is DispatchResult.Rejected -> {
                dao.updateAttempt(ReplyAttemptEntity(id,null,entity.identityKey,entity.app,"TEST",attemptedAt,failed=true))
                // Persist only controlled, non-sensitive explanations from the platform adapter.
                dao.updateMessage(entity.copy(status = ReplyStatus.FAILED.name, attemptedAt = attemptedAt, failureReason = result.reason.take(300),dispatchKind = "TEST"))
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

    suspend fun addContact(label: String, app: MessagingApp, sender: String,
        instagramUsername: String = "", instagramUserId: String = "", whatsappPhone: String = ""): String = mutex.withLock {
        val cleanLabel = label.trim(); val cleanSender = sender.trim()
        if (cleanLabel.isBlank() || cleanSender.isBlank()) return@withLock "Enter a local label and an exact visible sender name."
        if (cleanLabel.length > 80 || cleanSender.length > 160) return@withLock "Use a shorter label or sender name."
        val hints = ContactReferenceHints.sanitize(app, instagramUsername, instagramUserId, whatsappPhone)
            ?: return@withLock "Check the username, numeric user ID, or phone number format."
        if (dao.contacts().any { it.app == app.name && cipher.decrypt(it.sender).equals(cleanSender, ignoreCase = true) })
            return@withLock "That sender name is already saved for this platform. Names are not verified identities."
        dao.insertContact(ContactEntity(UUID.randomUUID().toString(), cipher.encrypt(cleanLabel)!!,
            app.name, cipher.encrypt(cleanSender)!!, now(),
            instagramUsername = hints.instagramUsername.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) },
            instagramUserId = hints.instagramUserId.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) },
            whatsappPhone = hints.whatsappPhone.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) }))
        "Contact saved locally. Automatic replies remain off."
    }

    suspend fun removeContact(id: String) = mutex.withLock { dao.deleteContact(id) }

    suspend fun message(id: String): MessageRecord? = mutex.withLock { dao.find(id)?.let(::decode) }
    suspend fun contactSnapshot(): List<PriorityContact> = mutex.withLock { dao.contacts().map(::decodeContact) }
    suspend fun claimGeneration(id: String, force: Boolean = false): MessageRecord? = mutex.withLock {
        val row = dao.find(id) ?: return@withLock null
        if (!preferences.onboarded || preferences.capturePaused || MessagingApp.valueOf(row.app) !in preferences.enabledApps ||
            row.status == ReplyStatus.DISMISSED.name || row.attemptedAt != null || dao.findAttempt(id) != null ||
            row.generationState == "GENERATING" || (!force && row.generationState != "NONE")) return@withLock null
        if (cipher.decrypt(row.preview).isNullOrBlank()) return@withLock null
        dao.updateMessage(row.copy(generationState = "GENERATING", generationReason = null))
        decode(row)
    }
    suspend fun context(identity: String?, excluding: String): List<ContextTurn> = mutex.withLock {
        if (identity == null) return@withLock emptyList()
        dao.messagesForIdentity(identity,6).filter { it.id != excluding && it.dispatchKind != "TEST" }.reversed().flatMap { row ->
            buildList {
                cipher.decrypt(row.preview)?.takeUnless(DraftSafety::containsCredentials)?.let { add(ContextTurn(ContextRole.INCOMING,it)) }
                // A dispatch is not proof of delivery; use only owner-reported received test/reply context.
                if (row.receiptReportedAt != null && row.dispatchKind != "TEST") cipher.decrypt(row.suggestion)?.let {
                    add(ContextTurn(ContextRole.OWN_REPLY,it))
                }
            }
        }.takeLast(6)
    }
    suspend fun storeGeneration(id: String, result: GenerationResult) = mutex.withLock {
        val row = dao.find(id) ?: return@withLock
        if (row.attemptedAt != null || row.status == ReplyStatus.DISMISSED.name) return@withLock
        when (result) {
            is GenerationResult.Success -> {
                val safety = MessageSafetyClassifier.classify(cipher.decrypt(row.preview).orEmpty())
                val needsReview = result.requiresApproval || safety.safety != MessageSafety.SMALLTALK
                dao.updateMessage(row.copy(suggestion = cipher.encrypt(result.draft),
                    generationState = if (needsReview) "APPROVAL" else "SUGGESTED",
                    generationReason = if (needsReview) "Review the draft; sensitive or missing context must be checked personally." else "Suggested reply. Sending is governed by your rules.",
                    engine = result.provider.name, inputTokens = result.inputTokens, outputTokens = result.outputTokens))
                event(MessagingApp.valueOf(row.app), if (needsReview) "Approval requested" else "Reply suggested",
                    "${result.provider.name}: ${result.inputTokens} input / ${result.outputTokens} output tokens. Price not estimated.")
            }
            is GenerationResult.Failure -> {
                dao.updateMessage(row.copy(generationState = "FAILED",generationReason = result.message.take(300)))
                event(MessagingApp.valueOf(row.app),"Reply generation failed","No message was sent. Review engine settings or use an offline template.")
            }
        }
    }
    suspend fun bindContact(contactId: String, messageId: String): String = mutex.withLock {
        val contact = dao.findContact(contactId) ?: return@withLock "Contact not found."
        val row = dao.find(messageId) ?: return@withLock "Notification is no longer retained."
        if (row.app != contact.app || row.isGroup || row.identityKey == null || row.sender == null)
            return@withLock "This notification has no supported one-to-one identity anchor for that platform. Name-only automation stays off."
        val expectedSender = cipher.decrypt(contact.sender)?.trim()
        val actualSender = cipher.decrypt(row.sender)?.trim()
        if (expectedSender.isNullOrBlank() || !expectedSender.equals(actualSender, ignoreCase = true))
            return@withLock "The notification sender does not match the saved name. Review the contact before binding."
        if (dao.contacts().any { it.id != contact.id && it.identityKey == row.identityKey }) return@withLock "That identity is already bound to another contact. Resolve the ambiguity first."
        val verified = row.dispatchKind == "TEST" && row.acceptedAt != null && row.receiptReportedAt != null
        dao.updateContact(contact.copy(identityKey = row.identityKey, sender = row.sender,
            autoEnabled = false, compatibilityVerified = verified))
        if (verified) "Identity bound with a user-reported successful test. Enable this contact's automation explicitly when ready."
        else "Identity bound. Automatic replies stay off until you complete and report a compatibility test, then bind that tested notification."
    }
    suspend fun saveContact(value: PriorityContact): String = mutex.withLock {
        val old = dao.findContact(value.id) ?: return@withLock "Contact not found."
        require(value.label.isNotBlank() && value.label.length <=80 && value.sender.isNotBlank() && value.sender.length<=160)
        require(value.cooldownSeconds in 0..86400 && value.maxPerHour in 1..100 && value.maxPerDay in 1..1000 && value.maxPerConversation in 1..100)
        require(value.quietStartHour in 0..23 && value.quietEndHour in 0..23 && value.instruction.length<=1000)
        val hints = ContactReferenceHints.sanitize(value.app, value.instagramUsername,
            value.instagramUserId, value.whatsappPhone)
            ?: return@withLock "Check the username, numeric user ID, or phone number format."
        val changedIdentity = value.sender.trim() != cipher.decrypt(old.sender) || value.app.name != old.app ||
            hints.instagramUsername != cipher.decrypt(old.instagramUsername).orEmpty() ||
            hints.instagramUserId != cipher.decrypt(old.instagramUserId).orEmpty() ||
            hints.whatsappPhone != cipher.decrypt(old.whatsappPhone).orEmpty()
        if (value.autoEnabled && (changedIdentity || old.identityKey == null || !old.compatibilityVerified))
            return@withLock "Bind a stable identity with a completed, user-reported compatibility test before enabling automatic replies."
        dao.updateContact(old.copy(label = cipher.encrypt(value.label.trim())!!,sender = cipher.encrypt(value.sender.trim())!!,
            app = value.app.name,identityKey = if (changedIdentity) null else old.identityKey,
            compatibilityVerified = !changedIdentity && old.compatibilityVerified,
            autoEnabled = value.autoEnabled && !changedIdentity,paused = value.paused,
            cooldownSeconds = value.cooldownSeconds,maxPerHour=value.maxPerHour,maxPerDay=value.maxPerDay,
            maxPerConversation=value.maxPerConversation,quietEnabled=value.quietEnabled,
            quietStartHour=value.quietStartHour,quietEndHour=value.quietEndHour,instruction=cipher.encrypt(value.instruction),
            instagramUsername = hints.instagramUsername.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) },
            instagramUserId = hints.instagramUserId.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) },
            whatsappPhone = hints.whatsappPhone.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) }))
        event(value.app,if (value.autoEnabled) "Contact automation enabled" else "Contact rules updated",
            "Explicit contact settings saved. Global pause and all safety rules still apply.")
        "Contact settings saved."
    }

    /** Final policy evaluation and durable reservation share the same serialized mutation boundary. */
    suspend fun sendReply(id: String, text: String, automatic: Boolean = false,
        generatorRequiresApproval: Boolean = true): String = mutex.withLock {
        val row = dao.find(id) ?: return@withLock "Notification no longer retained."
        val record = decode(row)
        PhaseOnePolicy.rejection(record,preferences)?.let { return@withLock it }
        if (dao.findAttempt(id) != null) return@withLock "This message already has a dispatch attempt. No automatic retry."
        if (text.isBlank() || text.length >1000 || text.any { (it.isISOControl() && it != '\n') || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 })
            return@withLock "Use 1–1,000 characters without unsupported control characters."
        if (DraftSafety.containsCredentials(text)) return@withLock "Credential-like text must be handled directly in the messaging app."
        val matches = dao.contacts().filter { it.app == row.app && it.identityKey != null && it.identityKey == row.identityKey }
        val contact = matches.singleOrNull()
        if (automatic) {
            val attempts = dao.attemptsSince(now()-30*86_400_000L)
            val zone = ZoneId.systemDefault()
            val dayKey = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate().toString()
            val identity = row.identityKey.orEmpty()
            val priorNeedsReview=dao.messagesForIdentity(identity,6).any { previous ->
                previous.id!=id && previous.dispatchKind!="TEST" && previous.postedAt>=now()-6*3_600_000L &&
                    MessageSafetyClassifier.classify(cipher.decrypt(previous.preview).orEmpty()).safety in setOf(MessageSafety.SENSITIVE,MessageSafety.UNCERTAIN)
            }
            if (priorNeedsReview) {
                val detail="Recent notification context contains an unresolved or sensitive topic. Review this reply personally."
                dao.updateMessage(row.copy(generationState="APPROVAL",generationReason=detail))
                return@withLock detail
            }
            val history = attempts.filter { it.kind == "AUTO" || it.identityKey == identity }.map {
                AutomaticReplyAttempt(Instant.ofEpochMilli(it.attemptedAt),it.identityKey ?: it.contactId.orEmpty(),
                    (it.identityKey ?: it.contactId.orEmpty())+":"+Instant.ofEpochMilli(it.attemptedAt).atZone(zone).toLocalDate())
            }
            val recentFailures = attempts.filter { it.kind == "AUTO" }.takeWhile { it.failed || it.acceptedAt==null }.size
            if (contact != null && attempts.count { it.identityKey==identity && it.attemptedAt>now()-3_600_000L } >= contact.maxPerHour) {
                val detail="This contact's hourly reply limit has been reached."
                dao.updateMessage(row.copy(generationState="APPROVAL",generationReason=detail))
                return@withLock detail
            }
            val decision = AutomationRules().evaluate(AutomationInput(
                now=Instant.ofEpochMilli(now()), zoneId=zone,contactId=contact?.identityKey,
                conversationKey="$identity:$dayKey",observedIdentityKey=row.identityKey,boundIdentityKey=contact?.identityKey,
                incoming=record.observation.preview.orEmpty(),draft=text,
                userApprovedAutomation=contact?.autoEnabled==true && contact.paused.not(),
                globalPaused=preferences.capturePaused || !preferences.automationEnabled,
                appEnabled=record.observation.app in preferences.enabledApps,recipientIdentityStable=row.identityKey!=null,
                senderAmbiguous=matches.size!=1,compatibilityVerified=contact?.compatibilityVerified==true,
                hasReplyAction=row.hasReplyAction,notificationActive=row.active,isGroup=row.isGroup,
                alreadyAttempted=row.attemptedAt!=null,userAlreadyResponded=!row.active,
                sufficientContext=MessageSafetyClassifier.classify(record.observation.preview.orEmpty()).safety==MessageSafety.SMALLTALK,
                historyComplete=true,history=history,consecutiveFailures=recentFailures,
                limits=AutomationLimits(contact?.cooldownSeconds?.toLong() ?:120,contact?.maxPerConversation ?:3,
                    10,30,contact?.maxPerDay ?:20),
                quietHours=contact?.takeIf { it.quietEnabled }?.let { QuietHours(it.quietStartHour*60,it.quietEndHour*60) },
                observedAt=row.messageAt?.let(Instant::ofEpochMilli),generatorRequiresApproval=generatorRequiresApproval))
            if (decision.disposition != AutomationDisposition.ALLOW) {
                dao.updateMessage(row.copy(generationState="APPROVAL",generationReason=decision.detail))
                return@withLock decision.detail
            }
        }
        val at = now(); val kind = if (automatic) "AUTO" else "MANUAL"
        val attempt = ReplyAttemptEntity(id,contact?.id,row.identityKey,row.app,kind,at)
        database.withTransaction {
            check(dao.insertAttempt(attempt) != -1L)
            dao.updateMessage(row.copy(status=ReplyStatus.DISPATCHING.name,attemptedAt=at,
                suggestion=cipher.encrypt(text),dispatchKind=kind))
            event(record.observation.app,"Reply dispatch attempted",if (automatic) "Configured contact and safety rules permitted this attempt." else "The user reviewed and confirmed this reply.")
        }
        val result = try { replyDispatch(id,text,automatic) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DispatchResult.Rejected("Reply dispatch could not complete. No automatic retry.") }
        val latest = dao.find(id)
        when(result) {
            DispatchResult.Accepted -> {
                dao.updateAttempt(attempt.copy(acceptedAt=now()))
                latest?.let { dao.updateMessage(it.copy(status=ReplyStatus.ACCEPTED.name,acceptedAt=now())) }
                event(record.observation.app,"Dispatch accepted by Android","$kind reply. Recipient receipt remains unverified.")
                "Android accepted the reply. Recipient receipt is unverified."
            }
            is DispatchResult.Rejected -> {
                dao.updateAttempt(attempt.copy(failed=true))
                latest?.let { dao.updateMessage(it.copy(status=ReplyStatus.FAILED.name,failureReason=result.reason.take(300))) }
                event(record.observation.app,"Reply dispatch failed","No automatic retry. Check Compatibility Lab.")
                result.reason
            }
        }
    }

    private suspend fun event(app: MessagingApp?, title: String, detail: String) {
        dao.insertEvent(EventEntity(at = now(), app = app?.name, title = title, detail = detail))
        dao.boundEvents()
    }
    private fun cutoff() = now() - preferences.retentionDays * 86_400_000L
    private suspend fun prune() {
        dao.purgeMessages(cutoff()); dao.purgeEvents(cutoff()); dao.boundMessages(); dao.boundEvents()
        dao.purgeAttemptsBefore(now()-30*86_400_000L)
    }
    private fun decodeContact(row: ContactEntity) = PriorityContact(row.id,
        cipher.decrypt(row.label) ?: "Unavailable encrypted label", MessagingApp.valueOf(row.app),
        cipher.decrypt(row.sender) ?: "",row.identityKey,row.autoEnabled,row.paused,row.cooldownSeconds,
        row.maxPerHour,row.maxPerDay,row.maxPerConversation,row.quietStartHour,row.quietEndHour,row.quietEnabled,
        cipher.decrypt(row.instruction).orEmpty(),row.compatibilityVerified,
        cipher.decrypt(row.instagramUsername).orEmpty(),cipher.decrypt(row.instagramUserId).orEmpty(),
        cipher.decrypt(row.whatsappPhone).orEmpty())
    private fun decode(row: MessageEntity) = MessageRecord(
        ObservedNotification(row.id, row.keyHash, MessagingApp.valueOf(row.app), cipher.decrypt(row.sender),
            cipher.decrypt(row.preview), row.postedAt, row.messageAt, row.messagingStyle, row.isGroup,
            row.actionCount, row.hasReplyAction, cipher.decrypt(row.conversation),row.identityKey),
        ReplyStatus.valueOf(row.status), row.active, row.attemptedAt, row.acceptedAt,
        row.receiptReportedAt, row.failureReason,cipher.decrypt(row.suggestion),row.generationState,
        row.generationReason,row.engine,row.dispatchKind)
}
