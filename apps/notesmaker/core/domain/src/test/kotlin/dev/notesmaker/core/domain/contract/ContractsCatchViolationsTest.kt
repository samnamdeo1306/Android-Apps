package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.Capability
import dev.notesmaker.core.domain.engine.CapabilityRequest
import dev.notesmaker.core.domain.engine.EngineKind
import dev.notesmaker.core.domain.engine.NoteGenerator
import dev.notesmaker.core.domain.engine.NotesInput
import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.engine.RawSegment
import dev.notesmaker.core.domain.engine.TranscriptionEngine
import dev.notesmaker.core.domain.engine.TranscriptionEvent
import dev.notesmaker.core.domain.engine.TranscriptionRequest
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.DraftNotes
import dev.notesmaker.core.domain.model.DraftSection
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.pipeline.PipelineStage
import dev.notesmaker.core.domain.pipeline.PipelineTrigger
import dev.notesmaker.core.domain.pipeline.StageContext
import dev.notesmaker.core.domain.pipeline.StageOutcome
import dev.notesmaker.core.domain.testing.FakePersistence
import dev.notesmaker.core.domain.testing.TestData
import dev.notesmaker.core.domain.testing.engineInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * A contract suite is only useful if it fails for a wrong implementation. These tests feed each
 * suite a deliberately broken implementation and check that the matching rule fails.
 */
class ContractsCatchViolationsTest {

    private val audio = AudioFile(File("a.m4a"))

    private class FixedEngine(private val events: List<TranscriptionEvent>) : TranscriptionEngine {
        override val info = engineInfo(EngineId("broken"), EngineKind.Transcription)
        override suspend fun capability(request: CapabilityRequest) = Capability.Ready
        override fun transcribe(audio: AudioFile, request: TranscriptionRequest): Flow<TranscriptionEvent> = flowOf(*events.toTypedArray())
    }

    private fun engineContract(engine: TranscriptionEngine) = object : TranscriptionEngineContract() {
        override fun subject() = engine
        override fun speechAudio() = ContractAudio(audio, 60_000)
        override fun silentAudio() = ContractAudio(audio, 60_000)
    }

    @Test
    fun `untilMs going backwards is caught`() {
        val engine = FixedEngine(listOf(TranscriptionEvent.Committed(emptyList(), 30_000), TranscriptionEvent.Committed(emptyList(), 20_000)))
        assertFailsWith<AssertionError> { engineContract(engine).committedUntilIsStrictlyIncreasingAndWithinTheAudio() }
    }

    @Test
    fun `a segment outside its commit is caught`() {
        val engine = FixedEngine(listOf(TranscriptionEvent.Committed(listOf(RawSegment(25_000, 35_000, "late")), 30_000)))
        assertFailsWith<AssertionError> { engineContract(engine).segmentsLieInsideTheirCommitInOrderWithoutOverlap() }
    }

    @Test
    fun `text invented on silence is caught`() {
        val engine = FixedEngine(listOf(TranscriptionEvent.Committed(listOf(RawSegment(0, 1_000, "Thank you for watching")), 60_000)))
        assertFailsWith<AssertionError> { engineContract(engine).silenceProducesNoText() }
    }

    @Test
    fun `a generator that ignores cancellation is caught`() {
        val ignoresCancellation = object : NoteGenerator {
            override val info = engineInfo(EngineId("broken.notes"), EngineKind.Notes)
            override suspend fun capability(request: CapabilityRequest) = Capability.Ready
            override suspend fun generate(input: NotesInput, progress: ProgressSink): Outcome<DraftNotes, ProcessingFailure> =
                Outcome.Success(DraftNotes("Title", listOf("s"), listOf(DraftSection("h", 0, null, listOf("p"))), emptyList()))
        }
        val contract = object : NoteGeneratorContract() {
            override fun subject() = ignoresCancellation
        }
        assertFailsWith<AssertionError> { contract.aCancelledCallerGetsCancellationNotAResult() }
    }

    @Test
    fun `a stage that appends instead of replacing is caught`() {
        val persistence = FakePersistence()
        runBlocking { persistence.sessions.create(TestData.newSession(1, status = SessionStatus.Waiting)) }
        var copies = 0
        val appending = object : PipelineStage {
            override val id = StageId("appending")
            override suspend fun isComplete(ctx: StageContext) = copies > 0
            override suspend fun run(ctx: StageContext): StageOutcome {
                copies++
                return StageOutcome.Completed
            }
        }
        val contract = object : PipelineStageContract() {
            override fun harness() = object : Harness {
                override val stage = appending
                override suspend fun context(progress: ProgressSink) =
                    StageContext(persistence.sessions.get(TestData.sessionId(1))!!, progress, PipelineTrigger.Initial)
                override suspend fun outputCopies() = copies
            }
        }
        assertFailsWith<AssertionError> { contract.runningTwiceLeavesOneCopyOfTheOutput() }
    }
}
