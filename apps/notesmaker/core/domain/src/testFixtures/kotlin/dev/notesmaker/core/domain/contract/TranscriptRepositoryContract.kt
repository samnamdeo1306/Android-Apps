package dev.notesmaker.core.domain.contract

import app.cash.turbine.test
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviour every `TranscriptRepository` must have (ADR-13 checkpoints, NFR-21 idempotency, ADR-33). */
public abstract class TranscriptRepositoryContract : PersistenceContract() {

    private val id = TestData.sessionId(1)
    private val header = TestData.transcriptHeader

    private suspend fun givenSession() {
        sessions.create(TestData.newSession(1))
    }

    @Test
    public fun nothingBeforeTranscriptionStarts(): Unit = runTest {
        givenSession()
        assertNull(transcripts.checkpoint(id))
        assertNull(transcripts.get(id))
    }

    @Test
    public fun appendMovesTheCheckpoint(): Unit = runTest {
        givenSession()
        transcripts.appendCommitted(id, header, TestData.segments(3), committedUntilMs = 15_000)

        val cp = assertNotNull(transcripts.checkpoint(id))
        assertEquals(header, cp.header)
        assertEquals(15_000, cp.committedUntilMs)
        assertEquals(3, cp.nextIndex)
        assertFalse(cp.complete)
        assertEquals(0, cp.revision)

        transcripts.appendCommitted(id, header, TestData.segments(2, firstIndex = 3), committedUntilMs = 25_000)
        val next = assertNotNull(transcripts.checkpoint(id))
        assertEquals(25_000, next.committedUntilMs)
        assertEquals(5, next.nextIndex)
    }

    @Test
    public fun anEmptyCommitStillMovesTheCheckpoint(): Unit = runTest {
        // A silent window commits no segments but must not be transcribed again.
        givenSession()
        transcripts.appendCommitted(id, header, emptyList(), committedUntilMs = 30_000)
        assertEquals(30_000, transcripts.checkpoint(id)?.committedUntilMs)
        assertEquals(0, transcripts.checkpoint(id)?.nextIndex)
        assertEquals(emptyList(), transcripts.get(id)?.segments)
    }

    @Test
    public fun getReturnsSegmentsInIndexOrder(): Unit = runTest {
        givenSession()
        val all = TestData.segments(6)
        transcripts.appendCommitted(id, header, all.take(3), 15_000)
        transcripts.appendCommitted(id, header, all.drop(3), 30_000)

        val t = assertNotNull(transcripts.get(id))
        assertEquals(all, t.segments)
        assertEquals(header.engineId, t.engineId)
        assertEquals(header.modelId, t.modelId)
        assertEquals(header.language, t.language)
    }

    @Test
    public fun reusingASegmentIndexFailsAndKeepsTheFirstCopy(): Unit = runTest {
        givenSession()
        transcripts.appendCommitted(id, header, TestData.segments(2), 10_000)

        assertFails { transcripts.appendCommitted(id, header, TestData.segments(1, firstIndex = 1), 20_000) }

        assertEquals(2, transcripts.get(id)?.segments?.size)
        assertEquals(10_000, transcripts.checkpoint(id)?.committedUntilMs, "a failed append must not move the checkpoint")
    }

    @Test
    public fun appendForAnUnknownSessionFails(): Unit = runTest {
        assertFails { transcripts.appendCommitted(id, header, TestData.segments(1), 5_000) }
    }

    @Test
    public fun markCompleteBumpsTheRevision(): Unit = runTest {
        givenSession()
        transcripts.appendCommitted(id, header, TestData.segments(2), 10_000)
        transcripts.markComplete(id)

        val cp = assertNotNull(transcripts.checkpoint(id))
        assertTrue(cp.complete)
        assertEquals(1, cp.revision)
        assertTrue(assertNotNull(transcripts.get(id)).complete)
    }

    @Test
    public fun clearKeepsTheRevisionSoOldNotesLookStale(): Unit = runTest {
        givenSession()
        transcripts.appendCommitted(id, header, TestData.segments(2), 10_000)
        transcripts.markComplete(id)
        val notesBuiltFrom = transcripts.checkpoint(id)!!.revision

        transcripts.clear(id)
        val cleared = assertNotNull(transcripts.checkpoint(id))
        assertEquals(0, cleared.committedUntilMs)
        assertEquals(0, cleared.nextIndex)
        assertFalse(cleared.complete)
        assertEquals(emptyList(), transcripts.get(id)?.segments)

        val otherEngine = TranscriptHeader(EngineId("other.engine"), null, TestData.ENGLISH)
        transcripts.appendCommitted(id, otherEngine, TestData.segments(1), 5_000)
        transcripts.markComplete(id)
        assertTrue(transcripts.checkpoint(id)!!.revision > notesBuiltFrom)
        assertEquals(otherEngine, transcripts.checkpoint(id)!!.header)
    }

    @Test
    public fun observeEmitsOnAppend(): Unit = runTest {
        givenSession()
        transcripts.observe(id).test {
            assertNull(awaitItem())
            transcripts.appendCommitted(id, header, TestData.segments(2), 10_000)
            assertEquals(2, awaitItem()?.segments?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun deletingTheSessionDeletesItsTranscript(): Unit = runTest {
        givenSession()
        transcripts.appendCommitted(id, header, TestData.segments(2), 10_000)
        sessions.delete(id)
        assertNull(transcripts.get(id))
        assertNull(transcripts.checkpoint(id))
    }
}
