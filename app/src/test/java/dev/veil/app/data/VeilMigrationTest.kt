package dev.veil.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Opens the checked-in V1 schema with V2 Room: validates the complete migrated schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VeilMigrationTest {
    private lateinit var context: Context
    private lateinit var name: String
    private var database: VeilDatabase? = null

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        name = "migration-${UUID.randomUUID()}.db"
        createLegacyDatabase()
    }

    @After fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test fun `migration preserves encrypted history and leaves every legacy contact automation disabled`() = runBlocking {
        val dao = openMigrated().dao()
        val accepted = dao.find("accepted")!!
        assertEquals("cipher-sender", accepted.sender)
        assertEquals("cipher-preview-with-'apostrophe", accepted.preview)
        assertEquals("cipher-conversation", accepted.conversation)
        assertEquals(1_000L, accepted.attemptedAt)
        assertEquals(1_100L, accepted.acceptedAt)
        assertEquals(1_200L, accepted.receiptReportedAt)
        assertEquals("ACCEPTED", accepted.status)
        assertEquals("TEST", accepted.dispatchKind)
        assertEquals("NONE", accepted.generationState)
        assertNull(accepted.identityKey)
        assertNull(accepted.suggestion)
        assertNull(accepted.engine)
        assertEquals(0, accepted.inputTokens)
        assertEquals(0, accepted.outputTokens)

        val contact = dao.findContact("legacy-contact")!!
        assertEquals("cipher-label", contact.label)
        assertEquals("cipher-contact-sender", contact.sender)
        assertFalse(contact.autoEnabled)
        assertFalse(contact.paused)
        assertFalse(contact.compatibilityVerified)
        assertNull(contact.identityKey)
        assertNull(contact.instruction)
        assertEquals(120, contact.cooldownSeconds)
        assertEquals(5, contact.maxPerHour)
        assertEquals(20, contact.maxPerDay)
        assertEquals(3, contact.maxPerConversation)
        assertTrue(contact.quietEnabled)
        assertEquals(22, contact.quietStartHour)
        assertEquals(8, contact.quietEndHour)
        assertEquals(1, dao.eventCount())
        assertEquals(4, dao.messageCount())
    }

    @Test fun `migration imports accepted failed and uncertain dispatches but never unattempted messages`() = runBlocking {
        val dao = openMigrated().dao()
        val accepted = dao.findAttempt("accepted")!!
        assertEquals("TEST", accepted.kind)
        assertEquals("WHATSAPP", accepted.app)
        assertEquals(1_000L, accepted.attemptedAt)
        assertEquals(1_100L, accepted.acceptedAt)
        assertFalse(accepted.failed)
        assertNull(accepted.contactId)
        assertNull(accepted.identityKey)
        assertNull(accepted.reason)
        assertTrue(dao.findAttempt("failed")!!.failed)
        assertNull(dao.findAttempt("failed")!!.acceptedAt)
        assertNotNull(dao.findAttempt("uncertain"))
        assertNull(dao.findAttempt("uncertain")!!.acceptedAt)
        assertNull(dao.findAttempt("fresh"))
        assertEquals(3, dao.attemptsSince(0L).size)
    }

    @Test fun `independent at most once ledger survives visible history deletion database reopening and duplicate insert`() = runBlocking {
        var db = openMigrated()
        db.dao().deleteMessages()
        db.dao().deleteEvents()
        db.close()
        database = null
        db = openMigrated()
        val dao = db.dao()
        assertEquals(0, dao.messageCount())
        assertEquals(0, dao.eventCount())
        assertNotNull(dao.findAttempt("uncertain"))
        val duplicate = ReplyAttemptEntity("accepted", "new-contact", "new-identity", "INSTAGRAM", "AUTO", 99_000L)
        assertEquals(-1L, dao.insertAttempt(duplicate))
        assertEquals("TEST", dao.findAttempt("accepted")!!.kind)
        assertEquals(1_000L, dao.findAttempt("accepted")!!.attemptedAt)
        assertEquals(3, dao.attemptsSince(0L).size)
    }

    @Test fun `ledger supports bounded retention and contact scoped rate history without retaining message bodies`() = runBlocking {
        val dao = openMigrated().dao()
        dao.insertAttempt(ReplyAttemptEntity("new-auto", "legacy-contact", "identity", "WHATSAPP", "AUTO", 5_000L))
        dao.insertAttempt(ReplyAttemptEntity("new-manual", "another-contact", "another-identity", "INSTAGRAM", "MANUAL", 6_000L))
        assertEquals(listOf("new-auto"), dao.attemptsForContact("legacy-contact", 0L).map { it.messageId })
        dao.purgeAttemptsBefore(5_000L)
        assertEquals(setOf("new-auto", "new-manual"), dao.attemptsSince(0L).map { it.messageId }.toSet())
        dao.deleteMessages()
        assertEquals(2, dao.attemptsSince(0L).size)
        dao.deleteAttempts() // Only the explicit reset API removes deduplication metadata.
        assertTrue(dao.attemptsSince(0L).isEmpty())
    }

    private fun openMigrated(): VeilDatabase {
        return Room.databaseBuilder(context, VeilDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(VeilDatabase.MIGRATION_1_2)
            .build().also {
                // Forces Room's migration and complete generated-schema validation now.
                it.openHelper.writableDatabase
                database = it
            }
    }

    private fun createLegacyDatabase() {
        val relative = "schemas/dev.veil.app.data.VeilDatabase/1.json"
        val schemaFile = listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("The checked-in V1 Room schema must be available for migration tests.")
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    legacy.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO contacts (id,label,app,sender,createdAt) VALUES (?,?,?,?,?)",
                arrayOf("legacy-contact", "cipher-label", "WHATSAPP", "cipher-contact-sender", 500L))
            legacy.execSQL("INSERT INTO events (id,at,app,title,detail) VALUES (1,500,'WHATSAPP','Detected','Sanitized event')")
            fun message(id: String, status: String, attempted: Long?, accepted: Long?, reported: Long?) {
                legacy.execSQL("""INSERT INTO observations
                    (id,keyHash,app,sender,preview,conversation,postedAt,messageAt,messagingStyle,isGroup,
                     actionCount,hasReplyAction,active,status,attemptedAt,acceptedAt,receiptReportedAt,failureReason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                    arrayOf(id, "opaque-key-$id", "WHATSAPP", "cipher-sender", "cipher-preview-with-'apostrophe",
                        "cipher-conversation", 900L, 800L, 1, 0, 1, 1, 1, status, attempted, accepted, reported, null))
            }
            message("accepted", "ACCEPTED", 1_000L, 1_100L, 1_200L)
            message("failed", "FAILED", 2_000L, null, null)
            message("uncertain", "DISPATCHING", 3_000L, null, null)
            message("fresh", "READY", null, null, null)
            legacy.version = 1
        }
    }
}
