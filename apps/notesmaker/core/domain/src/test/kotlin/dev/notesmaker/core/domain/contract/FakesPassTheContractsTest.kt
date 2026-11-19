package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.engine.RawSegment
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.pipeline.PipelineStage
import dev.notesmaker.core.domain.pipeline.PipelineTrigger
import dev.notesmaker.core.domain.pipeline.StageContext
import dev.notesmaker.core.domain.testing.FakeForegroundState
import dev.notesmaker.core.domain.testing.FakeLanguageModel
import dev.notesmaker.core.domain.testing.FakeNoteGenerator
import dev.notesmaker.core.domain.testing.FakeNotesExporter
import dev.notesmaker.core.domain.testing.FakePersistence
import dev.notesmaker.core.domain.testing.FakePipelineStage
import dev.notesmaker.core.domain.testing.FakeTranscriptionEngine
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.runBlocking
import java.io.File

/*
 * The shared fakes run the same contract suites as the production adapters (ADR-30, ADR-31), so a
 * test written against a fake tells the truth about the real implementation.
 */

class FakeSessionRepositoryContractTest : SessionRepositoryContract() {
    override fun openPersistence() = FakePersistence()
}

class FakeTranscriptRepositoryContractTest : TranscriptRepositoryContract() {
    override fun openPersistence() = FakePersistence()
}

class FakeNotesRepositoryContractTest : NotesRepositoryContract() {
    override fun openPersistence() = FakePersistence()
}

class FakeTranscriptionEngineContractTest : TranscriptionEngineContract() {
    private val speech = ContractAudio(AudioFile(File("speech.m4a")), durationMs = 95_000)
    private val silence = ContractAudio(AudioFile(File("silence.m4a")), durationMs = 40_000)

    private fun engine() = FakeTranscriptionEngine(windowMs = 30_000).apply {
        // One segment (28 s to 33 s) crosses the first window boundary on purpose.
        register(
            speech.audio,
            speech.durationMs,
            listOf(
                RawSegment(1_000, 9_000, "Today we look at photosynthesis."),
                RawSegment(10_000, 27_000, "Plants capture light with chlorophyll."),
                RawSegment(28_000, 33_000, "Remember the two stages."),
                RawSegment(40_000, 61_000, "The light reactions make ATP."),
                RawSegment(70_000, 94_000, "The Calvin cycle fixes carbon."),
            ),
        )
        register(silence.audio, silence.durationMs, emptyList())
    }

    override fun subject() = engine()
    override fun speechAudio() = speech
    override fun silentAudio() = silence
    override fun subjectWithoutModel() = engine().apply { modelInstalled = false }
}

class FakeNoteGeneratorContractTest : NoteGeneratorContract() {
    override fun subject() = FakeNoteGenerator()
}

class FakeLanguageModelContractTest : LanguageModelContract() {
    override fun subject() = FakeLanguageModel(responder = { "x".repeat(10_000) })
}

class ForegroundOnlyFakeLanguageModelContractTest : LanguageModelContract() {
    override val foregroundOnly = true
    override fun subject() = FakeLanguageModel(foregroundOnly = true, foreground = FakeForegroundState(true))
}

class FakeNotesExporterContractTest : NotesExporterContract() {
    override fun subject() = FakeNotesExporter()
}

class FakePipelineStageContractTest : PipelineStageContract() {
    override fun harness(): Harness = object : Harness {
        private val persistence = FakePersistence()
        private val fake = FakePipelineStage()
        override val stage: PipelineStage = fake

        init {
            runBlocking { persistence.sessions.create(TestData.newSession(1, status = SessionStatus.Waiting)) }
        }

        override suspend fun context(progress: ProgressSink) =
            StageContext(persistence.sessions.get(TestData.sessionId(1))!!, progress, PipelineTrigger.Initial)

        override suspend fun outputCopies() = fake.outputs.size
    }
}
