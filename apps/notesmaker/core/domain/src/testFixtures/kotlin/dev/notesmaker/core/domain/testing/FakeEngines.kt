package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.engine.Capability
import dev.notesmaker.core.domain.engine.CapabilityRequest
import dev.notesmaker.core.domain.engine.CompletionRequest
import dev.notesmaker.core.domain.engine.EngineInfo
import dev.notesmaker.core.domain.engine.EngineKind
import dev.notesmaker.core.domain.engine.EngineLocation
import dev.notesmaker.core.domain.engine.LanguageModel
import dev.notesmaker.core.domain.engine.NoteGenerator
import dev.notesmaker.core.domain.engine.NotesInput
import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.engine.RawSegment
import dev.notesmaker.core.domain.engine.TranscriptionEngine
import dev.notesmaker.core.domain.engine.TranscriptionEvent
import dev.notesmaker.core.domain.engine.TranscriptionRequest
import dev.notesmaker.core.domain.error.LanguageModelFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.DraftHighlight
import dev.notesmaker.core.domain.model.DraftNotes
import dev.notesmaker.core.domain.model.DraftSection
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.HighlightKind
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.UiLabel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

public fun engineInfo(
    id: EngineId,
    kind: EngineKind,
    qualityRank: Int = 0,
    location: EngineLocation = EngineLocation.OnDevice,
): EngineInfo = EngineInfo(id, kind, UiLabel("engine.${id.value}", id.value), location, qualityRank, languages = null)

/**
 * A transcription engine that "hears" the segments registered for a file. It commits in windows of
 * [windowMs], extending a window to the end of a segment that crosses it, as the whisper engine does
 * (LLD Section 6.2), so it exercises the same resume and boundary rules. Passes
 * `TranscriptionEngineContract`.
 */
public class FakeTranscriptionEngine(
    override val info: EngineInfo = engineInfo(TestData.TRANSCRIPTION_ENGINE, EngineKind.Transcription),
    private val windowMs: Long = 30_000,
    private val modelId: ModelId = TestData.MODEL,
) : TranscriptionEngine {
    private class Script(val durationMs: Long, val segments: List<RawSegment>)

    private val scripts = ConcurrentHashMap<File, Script>()

    /** Set to false to simulate a missing model. */
    @Volatile
    public var modelInstalled: Boolean = true

    public val requests: MutableList<TranscriptionRequest> = CopyOnWriteArrayList()

    public fun register(audio: AudioFile, durationMs: Long, segments: List<RawSegment>) {
        require(segments.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) { "segments must be ordered and not overlap" }
        require(segments.all { it.endMs <= durationMs }) { "segments must end within the audio" }
        scripts[audio.file] = Script(durationMs, segments)
    }

    override suspend fun capability(request: CapabilityRequest): Capability =
        if (modelInstalled) Capability.Ready else Capability.NeedsModel(modelId, sizeBytes = 1_000)

    override fun transcribe(audio: AudioFile, request: TranscriptionRequest): Flow<TranscriptionEvent> = flow {
        requests += request
        if (!modelInstalled) {
            emit(TranscriptionEvent.Failed(ProcessingFailure.ModelMissing(modelId)))
            return@flow
        }
        val script = scripts[audio.file]
        if (script == null) {
            emit(TranscriptionEvent.Failed(ProcessingFailure.AudioUnreadable("not registered with FakeTranscriptionEngine")))
            return@flow
        }
        var committed = request.resumeFromMs
        while (committed < script.durationMs) {
            currentCoroutineContext().ensureActive()
            var until = minOf(committed + windowMs, script.durationMs)
            script.segments.firstOrNull { it.startMs < until && it.endMs > until }?.let { until = it.endMs }
            val from = committed
            val window = script.segments.filter { it.startMs >= from && it.endMs <= until }
            emit(TranscriptionEvent.Committed(window, until))
            emit(TranscriptionEvent.Progress(processedMs = until, totalMs = script.durationMs))
            committed = until
        }
    }
}

/**
 * A deterministic note generator: groups segments into sections of [segmentsPerSection], titles
 * them with their first words, and marks sentences containing "remember" as key points.
 * Set [failure] to make it fail. Passes `NoteGeneratorContract`.
 */
public class FakeNoteGenerator(
    override val info: EngineInfo = engineInfo(TestData.NOTES_ENGINE, EngineKind.Notes),
    private val segmentsPerSection: Int = 3,
) : NoteGenerator {
    @Volatile
    public var failure: ProcessingFailure? = null

    @Volatile
    public var capability: Capability = Capability.Ready
    public val inputs: MutableList<NotesInput> = CopyOnWriteArrayList()

    override suspend fun capability(request: CapabilityRequest): Capability = capability

    override suspend fun generate(input: NotesInput, progress: ProgressSink): Outcome<DraftNotes, ProcessingFailure> {
        currentCoroutineContext().ensureActive()
        inputs += input
        failure?.let { return Outcome.Failure(it) }
        val segments = input.transcript.segments
        progress.report(0.5f)
        val sections = if (segments.isEmpty()) {
            listOf(DraftSection("Session", 0, input.durationMs, listOf("No speech was detected")))
        } else {
            segments.chunked(segmentsPerSection).map { chunk ->
                DraftSection(firstWords(chunk.first().text, 4), chunk.first().startMs, chunk.last().endMs, chunk.map { it.text })
            }
        }
        val highlights = segments.filter { "remember" in it.text.lowercase() }
            .map { DraftHighlight(HighlightKind.KeyPoint, it.text, it.startMs) }
        progress.report(1f)
        return Outcome.Success(
            DraftNotes(
                title = segments.firstOrNull()?.let { firstWords(it.text, 6) } ?: "Session",
                summary = segments.take(3).map { it.text }.ifEmpty { listOf("No speech was detected") },
                sections = sections,
                highlights = highlights,
            ),
        )
    }

    private fun firstWords(text: String, n: Int) = text.split(' ').filter { it.isNotBlank() }.take(n).joinToString(" ")
}

/**
 * A scripted language model. Replies come from [enqueue] in order, then from [responder].
 * Enforces [inputTokenBudget] and, when [foregroundOnly], the foreground rule of Gemini Nano.
 * Passes `LanguageModelContract`.
 */
public class FakeLanguageModel(
    override val info: EngineInfo = engineInfo(EngineId("fake.llm"), EngineKind.Notes, qualityRank = 10),
    override val inputTokenBudget: Int = 4_000,
    private val foregroundOnly: Boolean = false,
    private val foreground: FakeForegroundState = FakeForegroundState(true),
    private val responder: (CompletionRequest) -> String = { "{}" },
) : LanguageModel {
    private val scripted = ArrayDeque<Outcome<String, LanguageModelFailure>>()
    public val requests: MutableList<CompletionRequest> = CopyOnWriteArrayList()

    public fun enqueue(vararg outcomes: Outcome<String, LanguageModelFailure>) {
        synchronized(scripted) { scripted.addAll(outcomes) }
    }

    override suspend fun capability(request: CapabilityRequest): Capability =
        if (foregroundOnly && !request.inForeground) Capability.NeedsForeground else Capability.Ready

    override suspend fun complete(request: CompletionRequest): Outcome<String, LanguageModelFailure> {
        currentCoroutineContext().ensureActive()
        requests += request
        if (foregroundOnly && !foreground.isForeground.value) return Outcome.Failure(LanguageModelFailure.NotReady)
        if (estimateTokens(request.system) + estimateTokens(request.prompt) > inputTokenBudget) {
            return Outcome.Failure(LanguageModelFailure.InputTooLong)
        }
        val next = synchronized(scripted) { scripted.removeFirstOrNull() } ?: Outcome.Success(responder(request))
        return when (next) {
            is Outcome.Success -> Outcome.Success(next.value.take(request.maxOutputTokens * 4))
            is Outcome.Failure -> next
        }
    }
}
