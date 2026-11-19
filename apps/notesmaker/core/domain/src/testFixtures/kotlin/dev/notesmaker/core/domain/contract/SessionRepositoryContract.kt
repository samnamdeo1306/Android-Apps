package dev.notesmaker.core.domain.contract

import app.cash.turbine.test
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.SessionOrigin
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.SessionStatus.Cancelled
import dev.notesmaker.core.domain.model.SessionStatus.Failed
import dev.notesmaker.core.domain.model.SessionStatus.Processing
import dev.notesmaker.core.domain.model.SessionStatus.Ready
import dev.notesmaker.core.domain.model.SessionStatus.Recording
import dev.notesmaker.core.domain.model.SessionStatus.Waiting
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.pipeline.PipelineTrigger
import dev.notesmaker.core.domain.port.StatusChange
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviour every `SessionRepository` must have (API design Section 3, ADR-22). */
public abstract class SessionRepositoryContract : PersistenceContract() {

    private val id = TestData.sessionId(1)

    @Test
    public fun createThenGetReturnsTheSameSession(): Unit = runTest {
        val draft = TestData.newSession(1, origin = SessionOrigin.Imported("Biology 101.mp3"), durationMs = 61_000)
        val created = sessions.create(draft)

        val read = assertNotNull(sessions.get(draft.id))
        assertEquals(created, read)
        assertEquals("Session 1", read.title)
        assertEquals(TitleSource.Fallback, read.titleSource)
        assertEquals(SessionOrigin.Imported("Biology 101.mp3"), read.origin)
        assertEquals(61_000, read.durationMs)
        assertEquals(Waiting, read.status)
        assertEquals(draft.queuedAtEpochMs, read.processing.queuedAtEpochMs)
        assertEquals(PipelineTrigger.Initial, read.processing.trigger)
        assertFalse(read.notesUpgradeAvailable)
        assertFalse(read.recovered)
    }

    @Test
    public fun recordedOriginRoundTrips(): Unit = runTest {
        sessions.create(TestData.newSession(1, status = Recording, origin = SessionOrigin.Recorded))
        assertEquals(SessionOrigin.Recorded, sessions.get(id)?.origin)
        assertEquals(Recording, sessions.get(id)?.status)
    }

    @Test
    public fun creatingAnExistingIdFails(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        assertFails { sessions.create(TestData.newSession(1)) }
    }

    @Test
    public fun getUnknownIsNull(): Unit = runTest {
        assertNull(sessions.get(id))
    }

    @Test
    public fun observeAllIsNewestFirstAndUpdates(): Unit = runTest {
        sessions.observeAll().test {
            assertEquals(emptyList(), awaitItem())
            sessions.create(TestData.newSession(1, createdAtEpochMs = TestData.T0))
            assertEquals(listOf(TestData.sessionId(1)), awaitItem().map { it.id })
            sessions.create(TestData.newSession(2, createdAtEpochMs = TestData.T0 + 10))
            assertEquals(listOf(TestData.sessionId(2), TestData.sessionId(1)), awaitItem().map { it.id })
            sessions.rename(TestData.sessionId(1), "Cell biology", TitleSource.User)
            assertEquals("Cell biology", awaitItem().single { it.id == TestData.sessionId(1) }.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun observeEmitsNullThenTheSession(): Unit = runTest {
        sessions.observe(id).test {
            assertNull(awaitItem())
            sessions.create(TestData.newSession(1))
            assertEquals(id, awaitItem()?.id)
            sessions.updateProgress(id, StageId("transcribe"), 0.25f)
            assertEquals(0.25f, awaitItem()?.processing?.progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun simpleUpdatesAreStored(): Unit = runTest {
        sessions.create(TestData.newSession(1, status = Recording))
        sessions.rename(id, "Thermodynamics", TitleSource.Generated)
        sessions.setDuration(id, 1_000)
        sessions.setAudio(id, AudioRef("sessions/$id/audio.m4a"), 2_000)
        sessions.updateProgress(id, StageId("transcribe"), 0.5f)
        sessions.setNotesUpgradeAvailable(id, true)
        sessions.markRecovered(id)

        val s = assertNotNull(sessions.get(id))
        assertEquals("Thermodynamics", s.title)
        assertEquals(TitleSource.Generated, s.titleSource)
        assertEquals(AudioRef("sessions/$id/audio.m4a"), s.audio)
        assertEquals(2_000, s.durationMs)
        assertEquals(StageId("transcribe"), s.processing.stage)
        assertEquals(0.5f, s.processing.progress)
        assertTrue(s.notesUpgradeAvailable)
        assertTrue(s.recovered)
    }

    @Test
    public fun updatesOfAnUnknownSessionDoNothing(): Unit = runTest {
        sessions.rename(id, "Nothing", TitleSource.User)
        assertFalse(sessions.transition(id, SessionStatus.entries.toSet(), StatusChange.Start))
        assertNull(sessions.get(id))
    }

    @Test
    public fun transitionAppliesOnlyFromAnExpectedStatus(): Unit = runTest {
        sessions.create(TestData.newSession(1))

        assertFalse(sessions.transition(id, setOf(Processing), StatusChange.Complete))
        assertEquals(Waiting, sessions.get(id)?.status)

        assertTrue(sessions.transition(id, setOf(Waiting), StatusChange.Start))
        assertEquals(Processing, sessions.get(id)?.status)
    }

    @Test
    public fun cancelledSessionNeverBecomesReady(): Unit = runTest {
        // LLD Section 5.1: the user cancels while the worker is finishing.
        sessions.create(TestData.newSession(1))
        sessions.transition(id, setOf(Waiting), StatusChange.Start)

        assertTrue(sessions.transition(id, setOf(Waiting, Processing), StatusChange.Cancel))
        assertFalse(sessions.transition(id, setOf(Processing), StatusChange.Complete))
        assertEquals(Cancelled, sessions.get(id)?.status)
    }

    @Test
    public fun retryTwiceEnqueuesOnce(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.transition(id, setOf(Waiting), StatusChange.Start)
        sessions.transition(id, setOf(Processing), StatusChange.Fail(ProcessingFailure.TimedOut))

        val retry = StatusChange.Enqueue(TestData.T0 + 5_000, PipelineTrigger.Retry)
        assertTrue(sessions.transition(id, setOf(Failed, Cancelled), retry))
        assertFalse(sessions.transition(id, setOf(Failed, Cancelled), retry))
    }

    @Test
    public fun concurrentTransitionsHaveExactlyOneWinner(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.transition(id, setOf(Waiting), StatusChange.Start)

        val results = withContext(Dispatchers.Default) {
            List(16) { i ->
                async {
                    val change = if (i % 2 == 0) StatusChange.Cancel else StatusChange.Complete
                    sessions.transition(id, setOf(Processing), change)
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it })
        assertTrue(sessions.get(id)?.status in setOf(Cancelled, Ready))
    }

    @Test
    public fun enqueueSetsQueueFieldsAndClearsFailure(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.transition(id, setOf(Waiting), StatusChange.Start)
        sessions.updateProgress(id, StageId("transcribe"), 0.4f)
        sessions.transition(id, setOf(Processing), StatusChange.Fail(ProcessingFailure.StorageFull))

        val trigger = PipelineTrigger.RegenerateNotes(EngineId("llm.gemini-nano"))
        sessions.transition(id, setOf(Failed), StatusChange.Enqueue(TestData.T0 + 99, trigger))

        val p = assertNotNull(sessions.get(id)).processing
        assertEquals(TestData.T0 + 99, p.queuedAtEpochMs)
        assertEquals(trigger, p.trigger)
        assertNull(p.failure)
        assertNull(p.stage)
        assertNull(p.progress)
    }

    @Test
    public fun requeueKeepsQueuePosition(): Unit = runTest {
        val draft = TestData.newSession(1)
        sessions.create(draft)
        sessions.transition(id, setOf(Waiting), StatusChange.Start)
        sessions.updateProgress(id, StageId("transcribe"), 0.7f)

        assertTrue(sessions.transition(id, setOf(Processing), StatusChange.Requeue))

        val s = assertNotNull(sessions.get(id))
        assertEquals(Waiting, s.status)
        assertEquals(draft.queuedAtEpochMs, s.processing.queuedAtEpochMs)
        assertEquals(PipelineTrigger.Initial, s.processing.trigger)
        assertEquals(StageId("transcribe"), s.processing.stage)
        assertNull(s.processing.progress)
    }

    @Test
    public fun completeClearsProgressAndKeepsLastStage(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.transition(id, setOf(Waiting), StatusChange.Start)
        sessions.updateProgress(id, StageId("finalize"), 0.9f)
        sessions.transition(id, setOf(Processing), StatusChange.Complete)

        val s = assertNotNull(sessions.get(id))
        assertEquals(Ready, s.status)
        assertEquals(StageId("finalize"), s.processing.stage)
        assertNull(s.processing.progress)
        assertNull(s.processing.failure)
    }

    @Test
    public fun everyFailureRoundTrips(): Unit = runTest {
        val failures = listOf(
            ProcessingFailure.ModelMissing(ModelId("whisper-base.en-q5_1")),
            ProcessingFailure.AudioUnreadable("codec error 0x1"),
            ProcessingFailure.OutOfMemory,
            ProcessingFailure.EngineUnavailable(EngineId("whisper.cpp"), "context init failed"),
            ProcessingFailure.StorageFull,
            ProcessingFailure.TimedOut,
            ProcessingFailure.Cancelled,
            ProcessingFailure.Unknown("IllegalStateException in stage"),
        )
        failures.forEachIndexed { i, failure ->
            val sid = TestData.sessionId(100 + i)
            sessions.create(TestData.newSession(100 + i))
            sessions.transition(sid, setOf(Waiting), StatusChange.Start)
            sessions.transition(sid, setOf(Processing), StatusChange.Fail(failure))
            assertEquals(failure, sessions.get(sid)?.processing?.failure)
            assertEquals(Failed, sessions.get(sid)?.status)
        }
    }

    @Test
    public fun nextWaitingIsOldestQueuedWaitingSession(): Unit = runTest {
        sessions.create(TestData.newSession(1, queuedAtEpochMs = TestData.T0 + 300))
        sessions.create(TestData.newSession(2, queuedAtEpochMs = TestData.T0 + 100))
        sessions.create(TestData.newSession(3, queuedAtEpochMs = TestData.T0 + 200))
        sessions.create(TestData.newSession(4, status = Recording))

        assertEquals(TestData.sessionId(2), sessions.nextWaiting()?.id)
        sessions.transition(TestData.sessionId(2), setOf(Waiting), StatusChange.Start)
        assertEquals(TestData.sessionId(3), sessions.nextWaiting()?.id)
    }

    @Test
    public fun nextWaitingIsNullWhenQueueIsEmpty(): Unit = runTest {
        sessions.create(TestData.newSession(1, status = Recording))
        assertNull(sessions.nextWaiting())
    }

    @Test
    public fun findByStatusReturnsMatchingSessions(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.create(TestData.newSession(2, status = Recording))
        sessions.create(TestData.newSession(3))
        sessions.transition(TestData.sessionId(3), setOf(Waiting), StatusChange.Start)

        assertEquals(setOf(TestData.sessionId(2), TestData.sessionId(3)), sessions.findByStatus(Recording, Processing).map { it.id }.toSet())
        assertEquals(emptyList(), sessions.findByStatus(Ready))
    }

    @Test
    public fun deleteRemovesTheSession(): Unit = runTest {
        sessions.create(TestData.newSession(1))
        sessions.delete(id)
        assertNull(sessions.get(id))
        assertEquals(emptyList(), sessions.findByStatus(*SessionStatus.entries.toTypedArray()))
    }
}
