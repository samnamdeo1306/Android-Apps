package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.HighlightKind
import dev.notesmaker.core.domain.model.Highlight
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.NewSession
import dev.notesmaker.core.domain.model.NoteSection
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.NotesProvenance
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SessionOrigin
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.model.TranscriptSegment
import dev.notesmaker.core.domain.pipeline.PipelineTrigger

/**
 * Builders for test data with sensible defaults, so a test states only what it is about.
 * Shared by JVM tests and instrumented tests (ADR-30).
 */
public object TestData {
    public val ENGLISH: LanguageTag = LanguageTag("en")

    /** 2026-11-18T08:53:20Z, a fixed "now" for tests. */
    public const val T0: Long = 1_795_510_400_000L

    public val TRANSCRIPTION_ENGINE: EngineId = EngineId("fake.transcription")
    public val NOTES_ENGINE: EngineId = EngineId("fake.notes")
    public val MODEL: ModelId = ModelId("fake-model")

    /** A UUID-shaped, readable ID: `00000000-0000-0000-0000-000000000042`. */
    public fun sessionId(n: Int): SessionId = SessionId("00000000-0000-0000-0000-%012d".format(n))

    public fun newSession(
        n: Int = 1,
        status: SessionStatus = SessionStatus.Waiting,
        createdAtEpochMs: Long = T0 + n * 1_000L,
        title: String = "Session $n",
        titleSource: TitleSource = TitleSource.Fallback,
        origin: SessionOrigin = SessionOrigin.Imported("lecture-$n.m4a"),
        queuedAtEpochMs: Long? = if (status == SessionStatus.Waiting) createdAtEpochMs else null,
        trigger: PipelineTrigger? = if (status == SessionStatus.Waiting) PipelineTrigger.Initial else null,
        durationMs: Long? = null,
    ): NewSession = NewSession(
        id = sessionId(n),
        title = title,
        titleSource = titleSource,
        createdAtEpochMs = createdAtEpochMs,
        origin = origin,
        language = ENGLISH,
        status = status,
        durationMs = durationMs,
        queuedAtEpochMs = queuedAtEpochMs,
        trigger = trigger,
    )

    public val transcriptHeader: TranscriptHeader = TranscriptHeader(TRANSCRIPTION_ENGINE, MODEL, ENGLISH)

    /** [count] consecutive segments of [lengthMs], indexed from [firstIndex]. */
    public fun segments(count: Int, firstIndex: Int = 0, startMs: Long = firstIndex * 5_000L, lengthMs: Long = 5_000L): List<TranscriptSegment> =
        List(count) { i ->
            val index = firstIndex + i
            val start = startMs + i * lengthMs
            TranscriptSegment(index, start, start + lengthMs, "Segment $index explains how photosynthesis turns light into sugar.")
        }

    public fun transcript(sessionId: SessionId = sessionId(1), segmentCount: Int = 12, revision: Int = 1): Transcript = Transcript(
        sessionId = sessionId,
        engineId = TRANSCRIPTION_ENGINE,
        modelId = MODEL,
        language = ENGLISH,
        segments = segments(segmentCount),
        revision = revision,
        complete = true,
    )

    public fun notes(
        sessionId: SessionId = sessionId(1),
        sectionCount: Int = 3,
        highlightCount: Int = 2,
        transcriptRevision: Int = 1,
        title: String = "Photosynthesis and light reactions",
    ): Notes = Notes(
        sessionId = sessionId,
        title = title,
        summary = listOf("Plants turn light into chemical energy.", "Chlorophyll absorbs red and blue light.", "Oxygen is a by-product."),
        sections = List(sectionCount) { i ->
            NoteSection(heading = "Part ${i + 1}", startMs = i * 60_000L, endMs = (i + 1) * 60_000L, points = listOf("Point ${i + 1}a", "Point ${i + 1}b"))
        },
        highlights = List(highlightCount) { i ->
            Highlight(kind = HighlightKind.entries[i % HighlightKind.entries.size], text = "Highlight ${i + 1}", atMs = i * 30_000L)
        },
        provenance = NotesProvenance(
            generatorId = NOTES_ENGINE,
            modelId = null,
            promptVersion = null,
            transcriptRevision = transcriptRevision,
            generatedAtEpochMs = T0,
        ),
    )
}
