package dev.notesmaker.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.notesmaker.core.data.db.NotesMakerDatabase
import dev.notesmaker.core.data.repository.RoomNotesRepository
import dev.notesmaker.core.data.repository.RoomSessionRepository
import dev.notesmaker.core.data.repository.RoomTranscriptRepository
import dev.notesmaker.core.domain.testing.FakeClock
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks the stored form of the data, below the repository API (LLD Section 3, NFR-22). */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NotesMakerDatabase::class.java)

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NotesMakerDatabase::class.java).build()
    private val sessions = RoomSessionRepository(db.sessionDao(), FakeClock())
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @After
    fun close() = db.close()

    @Test
    fun committedSchemaMatchesTheEntities() {
        // Opens version 1 from core/data/schemas/<db>/1.json and validates it against the compiled
        // entities, so a schema change without a version bump and a committed JSON fails here.
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    @Test
    fun foreignKeysAreEnforcedAndCascade() = runTest {
        val id = TestData.sessionId(1)
        sessions.create(TestData.newSession(1))
        RoomTranscriptRepository(db.transcriptDao()).appendCommitted(id, TestData.transcriptHeader, TestData.segments(3), 15_000)
        RoomNotesRepository(db.notesDao()).replace(TestData.notes(id))

        assertEquals(1L, longQuery("PRAGMA foreign_keys"))
        sessions.delete(id)

        for (table in listOf("transcripts", "transcript_segments", "notes", "note_sections", "highlights")) {
            assertEquals(0L, longQuery("SELECT COUNT(*) FROM $table"), "rows left in $table")
        }
    }

    @Test
    fun enumsAreStoredAsNamesNotOrdinals() = runTest {
        sessions.create(TestData.newSession(1))
        sql.query("SELECT status, title_source, pipeline_trigger FROM sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Waiting", c.getString(0))
            assertEquals("fallback", c.getString(1))
            assertEquals("initial", c.getString(2))
        }
    }

    @Test
    fun queueAndListIndexesExist() {
        val indexes = mutableListOf<String>()
        sql.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'sessions'").use { c ->
            while (c.moveToNext()) indexes += c.getString(0)
        }
        assertTrue("idx_sessions_queue" in indexes && "idx_sessions_created" in indexes, "indexes were $indexes")
    }

    @Test
    fun theQueueQueryUsesTheQueueIndex() {
        val plan = buildString {
            sql.query("EXPLAIN QUERY PLAN SELECT * FROM sessions WHERE status = 'Waiting' ORDER BY queued_at IS NULL, queued_at, created_at LIMIT 1")
                .use { c -> while (c.moveToNext()) append(c.getString(c.columnCount - 1)).append('\n') }
        }
        assertTrue("idx_sessions_queue" in plan, "plan was:\n$plan")
    }

    private fun longQuery(query: String): Long = sql.query(query).use { c ->
        c.moveToFirst()
        c.getLong(0)
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
