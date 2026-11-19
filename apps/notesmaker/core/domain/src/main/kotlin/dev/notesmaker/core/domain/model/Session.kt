package dev.notesmaker.core.domain.model

import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.pipeline.PipelineTrigger

/** One recorded or imported class and everything derived from it. */
public data class Session(
    val id: SessionId,
    val title: String,
    val titleSource: TitleSource,
    val createdAtEpochMs: Long,
    val origin: SessionOrigin,
    val language: LanguageTag,
    /** Null until the audio is probed or the recording stops. */
    val durationMs: Long?,
    /** Null while recording. */
    val audio: AudioRef?,
    val status: SessionStatus,
    val processing: ProcessingState,
    val notesUpgradeAvailable: Boolean,
    /** True when the recording was recovered after the app died (FR-05). */
    val recovered: Boolean = false,
)

/** Status values are persisted by name; never rename one (LLD Section 3.1). */
public enum class SessionStatus { Recording, Waiting, Processing, Ready, Failed, Cancelled }

public enum class TitleSource {
    /** "Session on <date>", set when the session is created. */
    Fallback,

    /** From the notes generator. */
    Generated,

    /** Typed by the user; never overwritten by processing (FR-64). */
    User,
}

public sealed interface SessionOrigin {
    public data object Recorded : SessionOrigin

    public data class Imported(val displayName: String?) : SessionOrigin
}

public data class ProcessingState(
    /** Current or last stage. */
    val stage: StageId? = null,
    /** 0..1 within the stage, null if not measurable. */
    val progress: Float? = null,
    val failure: ProcessingFailure? = null,
    /** Queue order (FR-24). */
    val queuedAtEpochMs: Long? = null,
    /** Why the session is queued or processing; null before it was first queued. */
    val trigger: PipelineTrigger? = null,
) {
    init {
        require(progress == null || progress in 0f..1f) { "progress must be in 0..1, was $progress" }
    }
}

/** What a new session row starts with. The caller owns the ID, because file paths need it first (ADR-23). */
public data class NewSession(
    val id: SessionId,
    val title: String,
    val titleSource: TitleSource,
    val createdAtEpochMs: Long,
    val origin: SessionOrigin,
    val language: LanguageTag,
    val status: SessionStatus,
    val audio: AudioRef? = null,
    val durationMs: Long? = null,
    val queuedAtEpochMs: Long? = null,
    val trigger: PipelineTrigger? = null,
) {
    init {
        require(status == SessionStatus.Recording || status == SessionStatus.Waiting) {
            "A session starts as Recording or Waiting (LLD Section 5.1), not $status"
        }
        require(status != SessionStatus.Waiting || queuedAtEpochMs != null) { "A Waiting session needs queuedAtEpochMs" }
    }
}

/** Lightweight row for the sessions list (ADR-18, NFR-15): no transcript or notes data. */
public data class SessionListItem(
    val id: SessionId,
    val title: String,
    val createdAtEpochMs: Long,
    val durationMs: Long?,
    val status: SessionStatus,
    val stage: StageId?,
    val progress: Float?,
)
