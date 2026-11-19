package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.port.NotesRepository
import dev.notesmaker.core.domain.port.SessionRepository
import dev.notesmaker.core.domain.port.TranscriptRepository
import org.junit.After
import org.junit.Before

/** The three repositories over one empty store (a Room database or `FakePersistence`). */
public interface PersistenceUnderTest : AutoCloseable {
    public val sessions: SessionRepository
    public val transcripts: TranscriptRepository
    public val notes: NotesRepository
}

/**
 * Base of the repository contract suites (ADR-31). A subclass supplies a fresh, empty store per
 * test; JVM tests pass `FakePersistence`, instrumented tests pass an in-memory Room database.
 */
public abstract class PersistenceContract {
    protected abstract fun openPersistence(): PersistenceUnderTest

    private lateinit var persistence: PersistenceUnderTest

    protected val sessions: SessionRepository get() = persistence.sessions
    protected val transcripts: TranscriptRepository get() = persistence.transcripts
    protected val notes: NotesRepository get() = persistence.notes

    @Before
    public fun openStore() {
        persistence = openPersistence()
    }

    @After
    public fun closeStore() {
        persistence.close()
    }
}
