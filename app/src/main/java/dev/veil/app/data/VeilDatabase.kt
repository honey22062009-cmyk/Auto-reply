package dev.veil.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "observations", indices = [Index("keyHash"), Index("postedAt")])
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
)

@Entity(tableName = "events", indices = [Index("at")])
data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long, val app: String?, val title: String, val detail: String)

@Entity(tableName = "contacts")
data class ContactEntity(@PrimaryKey val id: String, val label: String, val app: String,
    val sender: String, val createdAt: Long)

@Dao
interface VeilDao {
    @Query("SELECT * FROM observations ORDER BY postedAt DESC LIMIT 100") fun observeMessages(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM events ORDER BY at DESC, id DESC LIMIT 200") fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM contacts ORDER BY createdAt ASC") fun observeContacts(): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contacts") suspend fun contacts(): List<ContactEntity>
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

@Database(entities = [MessageEntity::class, EventEntity::class, ContactEntity::class], version = 1, exportSchema = true)
abstract class VeilDatabase : RoomDatabase() { abstract fun dao(): VeilDao }
