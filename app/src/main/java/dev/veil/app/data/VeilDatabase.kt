package dev.veil.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "observations", indices = [Index("keyHash"), Index("postedAt"), Index("identityKey")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val keyHash: String,
    val app: String,
    val sender: String?,
    val preview: String?,
    val conversation: String?,
    val postedAt: Long,
    val messageAt: Long?,
    val messagingStyle: Boolean,
    val isGroup: Boolean,
    val actionCount: Int,
    val hasReplyAction: Boolean,
    val active: Boolean,
    val status: String,
    val attemptedAt: Long? = null,
    val acceptedAt: Long? = null,
    val receiptReportedAt: Long? = null,
    val failureReason: String? = null,
    val identityKey: String? = null,
    val suggestion: String? = null,
    @ColumnInfo(defaultValue = "'NONE'") val generationState: String = "NONE",
    val generationReason: String? = null,
    val engine: String? = null,
    @ColumnInfo(defaultValue = "0") val inputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0") val outputTokens: Int = 0,
    val dispatchKind: String? = null,
)

@Entity(tableName = "events", indices = [Index("at")])
data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long, val app: String?, val title: String, val detail: String)

@Entity(tableName = "contacts", indices = [Index("identityKey")])
data class ContactEntity(
    @PrimaryKey val id: String,
    val label: String,
    val app: String,
    val sender: String,
    val createdAt: Long,
    val identityKey: String? = null,
    @ColumnInfo(defaultValue = "0") val autoEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val paused: Boolean = false,
    @ColumnInfo(defaultValue = "120") val cooldownSeconds: Int = 120,
    @ColumnInfo(defaultValue = "5") val maxPerHour: Int = 5,
    @ColumnInfo(defaultValue = "20") val maxPerDay: Int = 20,
    @ColumnInfo(defaultValue = "3") val maxPerConversation: Int = 3,
    @ColumnInfo(defaultValue = "22") val quietStartHour: Int = 22,
    @ColumnInfo(defaultValue = "8") val quietEndHour: Int = 8,
    @ColumnInfo(defaultValue = "1") val quietEnabled: Boolean = true,
    val instruction: String? = null,
    @ColumnInfo(defaultValue = "0") val compatibilityVerified: Boolean = false,
)

/** Content-free dispatch ledger survives message-history deletion and retention. */
@Entity(tableName = "reply_attempts", indices = [Index("attemptedAt"), Index("contactId"), Index("identityKey")])
data class ReplyAttemptEntity(
    @PrimaryKey val messageId: String,
    val contactId: String?,
    val identityKey: String?,
    val app: String,
    val kind: String,
    val attemptedAt: Long,
    val acceptedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val failed: Boolean = false,
    val reason: String? = null,
)

@Dao
interface VeilDao {
    @Query("SELECT * FROM observations ORDER BY postedAt DESC LIMIT 100") fun observeMessages(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM events ORDER BY at DESC, id DESC LIMIT 200") fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM contacts ORDER BY createdAt ASC") fun observeContacts(): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contacts") suspend fun contacts(): List<ContactEntity>
    @Query("SELECT * FROM contacts WHERE id = :id") suspend fun findContact(id: String): ContactEntity?
    @Update suspend fun updateContact(contact: ContactEntity)
    @Query("SELECT * FROM observations ORDER BY postedAt DESC LIMIT :limit") suspend fun messagesSnapshot(limit: Int = 100): List<MessageEntity>
    @Query("SELECT * FROM observations WHERE identityKey = :identityKey ORDER BY postedAt DESC LIMIT :limit") suspend fun messagesForIdentity(identityKey: String, limit: Int = 6): List<MessageEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttempt(attempt: ReplyAttemptEntity): Long
    @Update suspend fun updateAttempt(attempt: ReplyAttemptEntity)
    @Query("SELECT * FROM reply_attempts WHERE messageId = :messageId") suspend fun findAttempt(messageId: String): ReplyAttemptEntity?
    @Query("SELECT * FROM reply_attempts WHERE attemptedAt >= :since ORDER BY attemptedAt DESC") suspend fun attemptsSince(since: Long): List<ReplyAttemptEntity>
    @Query("SELECT * FROM reply_attempts WHERE contactId = :contactId AND attemptedAt >= :since ORDER BY attemptedAt DESC") suspend fun attemptsForContact(contactId: String, since: Long): List<ReplyAttemptEntity>
    @Query("DELETE FROM reply_attempts WHERE attemptedAt < :cutoff") suspend fun purgeAttemptsBefore(cutoff: Long)
    /** Explicit full reset only. Normal history deletion must retain deduplication metadata. */
    @Query("DELETE FROM reply_attempts") suspend fun deleteAttempts()
    @Query("SELECT * FROM observations WHERE id = :id") suspend fun find(id: String): MessageEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(message: MessageEntity): Long
    @Update suspend fun updateMessage(message: MessageEntity)
    @Insert suspend fun insertEvent(event: EventEntity)
    @Insert suspend fun insertContact(contact: ContactEntity)
    @Query("DELETE FROM contacts WHERE id = :id") suspend fun deleteContact(id: String)
    @Query("UPDATE observations SET active = 0 WHERE keyHash = :keyHash AND id != :exceptId") suspend fun deactivateOthers(keyHash: String, exceptId: String)
    @Query("UPDATE observations SET active = 0, status = CASE WHEN attemptedAt IS NULL AND status != 'DISMISSED' THEN 'REMOVED' ELSE status END WHERE keyHash = :keyHash") suspend fun markRemoved(keyHash: String)
    @Query("UPDATE observations SET active = 0") suspend fun deactivateAll()
    @Query("UPDATE observations SET active = 0 WHERE app = :app") suspend fun deactivateApp(app: String)
    @Query("DELETE FROM observations WHERE postedAt < :cutoff") suspend fun purgeMessages(cutoff: Long)
    @Query("DELETE FROM observations WHERE id NOT IN (SELECT id FROM observations ORDER BY postedAt DESC LIMIT 500)") suspend fun boundMessages()
    @Query("DELETE FROM events WHERE at < :cutoff") suspend fun purgeEvents(cutoff: Long)
    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY at DESC, id DESC LIMIT 1000)") suspend fun boundEvents()
    @Query("DELETE FROM observations") suspend fun deleteMessages()
    @Query("DELETE FROM events") suspend fun deleteEvents()
    @Query("SELECT COUNT(*) FROM observations") suspend fun messageCount(): Int
    @Query("SELECT COUNT(*) FROM events") suspend fun eventCount(): Int
}

@Database(entities = [MessageEntity::class, EventEntity::class, ContactEntity::class, ReplyAttemptEntity::class], version = 2, exportSchema = true)
abstract class VeilDatabase : RoomDatabase() {
    abstract fun dao(): VeilDao

    companion object {
        /** Additive migration: old encrypted fields and dispatch outcomes remain intact. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN identityKey TEXT")
                db.execSQL("ALTER TABLE observations ADD COLUMN suggestion TEXT")
                db.execSQL("ALTER TABLE observations ADD COLUMN generationState TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE observations ADD COLUMN generationReason TEXT")
                db.execSQL("ALTER TABLE observations ADD COLUMN engine TEXT")
                db.execSQL("ALTER TABLE observations ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE observations ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE observations ADD COLUMN dispatchKind TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_observations_identityKey ON observations (identityKey)")
                db.execSQL("ALTER TABLE contacts ADD COLUMN identityKey TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN autoEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN cooldownSeconds INTEGER NOT NULL DEFAULT 120")
                db.execSQL("ALTER TABLE contacts ADD COLUMN maxPerHour INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE contacts ADD COLUMN maxPerDay INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE contacts ADD COLUMN maxPerConversation INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE contacts ADD COLUMN quietStartHour INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE contacts ADD COLUMN quietEndHour INTEGER NOT NULL DEFAULT 8")
                db.execSQL("ALTER TABLE contacts ADD COLUMN quietEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE contacts ADD COLUMN instruction TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN compatibilityVerified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_identityKey ON contacts (identityKey)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS reply_attempts (
                    messageId TEXT NOT NULL PRIMARY KEY, contactId TEXT, identityKey TEXT,
                    app TEXT NOT NULL, kind TEXT NOT NULL, attemptedAt INTEGER NOT NULL,
                    acceptedAt INTEGER, failed INTEGER NOT NULL DEFAULT 0, reason TEXT)""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reply_attempts_attemptedAt ON reply_attempts (attemptedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reply_attempts_contactId ON reply_attempts (contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reply_attempts_identityKey ON reply_attempts (identityKey)")
                // Legacy one-shot tests must remain blocked even after their visible history is deleted.
                db.execSQL("""INSERT OR IGNORE INTO reply_attempts
                    (messageId, contactId, identityKey, app, kind, attemptedAt, acceptedAt, failed, reason)
                    SELECT id, NULL, NULL, app, 'TEST', attemptedAt, acceptedAt,
                        CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END, NULL
                    FROM observations WHERE attemptedAt IS NOT NULL""".trimIndent())
                db.execSQL("UPDATE observations SET dispatchKind = 'TEST' WHERE attemptedAt IS NOT NULL")
            }
        }
    }
}
