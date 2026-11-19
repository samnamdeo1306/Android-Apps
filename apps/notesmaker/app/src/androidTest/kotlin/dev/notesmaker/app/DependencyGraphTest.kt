package dev.notesmaker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.notesmaker.core.domain.engine.EngineRegistry
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.port.Clock
import dev.notesmaker.core.domain.port.IdGenerator
import dev.notesmaker.core.domain.port.NotesRepository
import dev.notesmaker.core.domain.port.SessionRepository
import dev.notesmaker.core.domain.port.TranscriptRepository
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The production Hilt graph, as `:app` assembles it from every module's bindings (ADR-12):
 * ports resolve to working adapters, singletons are shared, and the engine sets exist.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DependencyGraphTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var sessionsAgain: SessionRepository
    @Inject lateinit var transcripts: TranscriptRepository
    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var engines: EngineRegistry
    @Inject lateinit var clock: Clock
    @Inject lateinit var ids: IdGenerator

    private val created = mutableListOf<SessionId>()

    @Before
    fun inject() = hilt.inject()

    @After
    fun cleanUp() = runTest { created.forEach { sessions.delete(it) } }

    @Test
    fun repositoriesAreSingletons() {
        assertSame(sessions, sessionsAgain)
    }

    @Test
    fun portsResolveToAWorkingDatabase() = runTest {
        val id = ids.newSessionId().also { created += it }
        sessions.create(TestData.newSession(1).copy(id = id, createdAtEpochMs = clock.nowEpochMs(), queuedAtEpochMs = clock.nowEpochMs()))
        transcripts.appendCommitted(id, TestData.transcriptHeader, TestData.segments(2), 10_000)
        notes.replace(TestData.notes(id))

        assertEquals(id, sessions.get(id)?.id)
        assertEquals(2, transcripts.get(id)?.segments?.size)
        assertEquals(TestData.notes(id), notes.get(id))
    }

    @Test
    fun environmentAdaptersAreReal() {
        assertTrue(clock.nowEpochMs() > TestData.T0 - 365L * 24 * 3_600_000, "clock is not wall time")
        assertNotEquals(ids.newSessionId(), ids.newSessionId())
    }

    @Test
    fun engineSetsAreDeclaredAndIdsAreUnique() {
        engines.verifyUniqueIds()
    }
}
