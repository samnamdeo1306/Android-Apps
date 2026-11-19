package dev.notesmaker.core.data.db

import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.error.ProcessingFailureCodec
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.EditFlags
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.Highlight
import dev.notesmaker.core.domain.model.HighlightKind
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.NewSession
import dev.notesmaker.core.domain.model.NoteSection
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.NotesProvenance
import dev.notesmaker.core.domain.model.ProcessingState
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SessionListItem
import dev.notesmaker.core.domain.model.SessionOrigin
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.TranscriptCheckpoint
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.model.TranscriptSegment
import dev.notesmaker.core.domain.pipeline.PipelineTriggerCodec

/*
 * Entity <-> domain mapping. Stored strings are stable names (LLD Section 3.1). A value this app
 * version does not know (for example written by a newer version before a downgrade) maps to a safe
 * default instead of crashing. Pure Kotlin, so it is unit tested on the JVM.
 */

internal object StoredNames {
    const val ORIGIN_RECORDED = "recorded"
    const val ORIGIN_IMPORTED = "imported"

    fun titleSource(source: TitleSource): String = when (source) {
        TitleSource.Fallback -> "fallback"
        TitleSource.Generated -> "generated"
        TitleSource.User -> "user"
    }

    /** Unknown → Generated: processing may overwrite it, which is the safe direction for an unknown value. */
    fun titleSource(stored: String): TitleSource = when (stored) {
        "fallback" -> TitleSource.Fallback
        "user" -> TitleSource.User
        else -> TitleSource.Generated
    }

    fun status(status: SessionStatus): String = status.name

    /** Null for an unknown value; the caller turns it into a retryable failure. */
    fun status(stored: String): SessionStatus? = SessionStatus.entries.firstOrNull { it.name == stored }

    fun highlightKind(kind: HighlightKind): String = kind.name

    fun highlightKind(stored: String): HighlightKind = HighlightKind.entries.firstOrNull { it.name == stored } ?: HighlightKind.KeyPoint
}

internal fun NewSession.toEntity(now: Long): SessionEntity = SessionEntity(
    id = id.value,
    title = title,
    titleSource = StoredNames.titleSource(titleSource),
    createdAt = createdAtEpochMs,
    origin = if (origin is SessionOrigin.Imported) StoredNames.ORIGIN_IMPORTED else StoredNames.ORIGIN_RECORDED,
    originName = (origin as? SessionOrigin.Imported)?.displayName,
    language = language.value,
    durationMs = durationMs,
    audioPath = audio?.relativePath,
    status = StoredNames.status(status),
    stage = null,
    progress = null,
    failureCode = null,
    failureDetail = null,
    queuedAt = queuedAtEpochMs,
    pipelineTrigger = trigger?.let(PipelineTriggerCodec::encode),
    notesUpgradeAvailable = false,
    recovered = false,
    updatedAt = now,
)

internal fun SessionEntity.toDomain(): Session {
    val knownStatus = StoredNames.status(status)
    val storedFailure = failureCode?.let { ProcessingFailureCodec.decode(it, failureDetail) }
    return Session(
        id = SessionId(id),
        title = title,
        titleSource = StoredNames.titleSource(titleSource),
        createdAtEpochMs = createdAt,
        origin = if (origin == StoredNames.ORIGIN_IMPORTED) SessionOrigin.Imported(originName) else SessionOrigin.Recorded,
        language = LanguageTag(language),
        durationMs = durationMs,
        audio = audioPath?.let(::AudioRef),
        // An unknown status is shown as a failure the user can retry.
        status = knownStatus ?: SessionStatus.Failed,
        processing = ProcessingState(
            stage = stage?.let(::StageId),
            progress = progress?.coerceIn(0f, 1f),
            failure = storedFailure ?: if (knownStatus == null) ProcessingFailure.Unknown("unrecognised status '$status'") else null,
            queuedAtEpochMs = queuedAt,
            trigger = pipelineTrigger?.let(PipelineTriggerCodec::decode),
        ),
        notesUpgradeAvailable = notesUpgradeAvailable,
        recovered = recovered,
    )
}

internal fun SessionListRow.toDomain(): SessionListItem = SessionListItem(
    id = SessionId(id),
    title = title,
    createdAtEpochMs = createdAt,
    durationMs = durationMs,
    status = StoredNames.status(status) ?: SessionStatus.Failed,
    stage = stage?.let(::StageId),
    progress = progress?.coerceIn(0f, 1f),
)

internal fun TranscriptEntity.toCheckpoint(): TranscriptCheckpoint = TranscriptCheckpoint(
    header = TranscriptHeader(EngineId(engineId), modelId?.let(::ModelId), LanguageTag(language)),
    committedUntilMs = committedUntilMs,
    nextIndex = nextIndex,
    complete = complete,
    revision = revision,
)

internal fun TranscriptWithSegments.toDomain(): Transcript = Transcript(
    sessionId = SessionId(header.sessionId),
    engineId = EngineId(header.engineId),
    modelId = header.modelId?.let(::ModelId),
    language = LanguageTag(header.language),
    segments = segments.sortedBy { it.idx }.map { TranscriptSegment(it.idx, it.startMs, it.endMs, it.text) },
    revision = header.revision,
    complete = header.complete,
)

internal fun TranscriptSegment.toEntity(id: SessionId): TranscriptSegmentEntity =
    TranscriptSegmentEntity(id.value, index, startMs, endMs, text)

internal fun NotesWithChildren.toDomain(): Notes = Notes(
    sessionId = SessionId(notes.sessionId),
    title = notes.title,
    summary = notes.summary,
    sections = sections.sortedBy { it.idx }.map { NoteSection(it.heading, it.startMs, it.endMs, it.points, it.edited) },
    highlights = highlights.sortedBy { it.idx }.map { Highlight(StoredNames.highlightKind(it.kind), it.text, it.atMs, it.edited) },
    provenance = NotesProvenance(
        generatorId = EngineId(notes.generatorId),
        modelId = notes.modelId?.let(::ModelId),
        promptVersion = notes.promptVersion,
        transcriptRevision = notes.transcriptRevision,
        generatedAtEpochMs = notes.generatedAt,
    ),
    edits = EditFlags(summaryEdited = notes.summaryEdited),
)

internal fun Notes.toEntities(): Triple<NotesEntity, List<NoteSectionEntity>, List<HighlightEntity>> {
    val id = sessionId.value
    return Triple(
        NotesEntity(
            sessionId = id,
            title = title,
            summary = summary,
            summaryEdited = edits.summaryEdited,
            generatorId = provenance.generatorId.value,
            modelId = provenance.modelId?.value,
            promptVersion = provenance.promptVersion,
            transcriptRevision = provenance.transcriptRevision,
            generatedAt = provenance.generatedAtEpochMs,
        ),
        sections.mapIndexed { i, s -> NoteSectionEntity(id, i, s.heading, s.startMs, s.endMs, s.points, s.edited) },
        highlights.mapIndexed { i, h -> HighlightEntity(id, i, StoredNames.highlightKind(h.kind), h.text, h.atMs, h.edited) },
    )
}
