package dev.notesmaker.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.notesmaker.core.data.db.NotesMakerDatabase
import dev.notesmaker.core.data.repository.RoomNotesRepository
import dev.notesmaker.core.data.repository.RoomSessionRepository
import dev.notesmaker.core.data.repository.RoomTranscriptRepository
import dev.notesmaker.core.domain.contract.NotesRepositoryContract
import dev.notesmaker.core.domain.contract.PersistenceUnderTest
import dev.notesmaker.core.domain.contract.SessionRepositoryContract
import dev.notesmaker.core.domain.contract.TranscriptRepositoryContract
import dev.notesmaker.core.domain.testing.FakeClock
import org.junit.runner.RunWith

/*
 * The same contract suites the in-memory fakes pass on the JVM, run here against Room and real
 * SQLite (ADR-31). If a fake and Room ever disagree, one of these suites fails.
 */

internal class RoomPersistence : PersistenceUnderTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NotesMakerDatabase::class.java).build()
    override val sessions = RoomSessionRepository(db.sessionDao(), FakeClock())
    override val transcripts = RoomTranscriptRepository(db.transcriptDao())
    override val notes = RoomNotesRepository(db.notesDao())

    override fun close() = db.close()
}

@RunWith(AndroidJUnit4::class)
class RoomSessionRepositoryTest : SessionRepositoryContract() {
    override fun openPersistence(): PersistenceUnderTest = RoomPersistence()
}

@RunWith(AndroidJUnit4::class)
class RoomTranscriptRepositoryTest : TranscriptRepositoryContract() {
    override fun openPersistence(): PersistenceUnderTest = RoomPersistence()
}

@RunWith(AndroidJUnit4::class)
class RoomNotesRepositoryTest : NotesRepositoryContract() {
    override fun openPersistence(): PersistenceUnderTest = RoomPersistence()
}
