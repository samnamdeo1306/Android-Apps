package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.NewSession
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SessionListItem
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.pipeline.PipelineTrigger
import kotlinx.coroutines.flow.Flow

/**
 * Sessions and their processing status. **Port**: implemented by `RoomSessionRepository` and by
 * `FakeSessionRepository`; both pass `SessionRepositoryContract` (ADR-31).
 */
public interface SessionRepository {
    /** Newest first. Title sorting is done in the use case with a collator (ADR-18). */
    public fun observeAll(): Flow<List<SessionListItem>>

    public fun observe(id: SessionId): Flow<Session?>

    public suspend fun get(id: SessionId): Session?

    public suspend fun create(draft: NewSession): Session

    /** Oldest `queuedAt` among `Waiting` sessions (FR-24). */
    public suspend fun nextWaiting(): Session?

    public suspend fun rename(id: SessionId, title: String, source: TitleSource)

    public suspend fun setDuration(id: SessionId, durationMs: Long)

    public suspend fun setAudio(id: SessionId, audio: AudioRef, durationMs: Long)

    public suspend fun updateProgress(id: SessionId, stage: StageId, progress: Float?)

    public suspend fun setNotesUpgradeAvailable(id: SessionId, available: Boolean)

    public suspend fun markRecovered(id: SessionId)

    /**
     * Compare-and-set status change (ADR-22). Applies [change] only if the current status is in
     * [expected] and returns whether it did. This is the only way to change status, so a cancel
     * from the UI and a completion from the worker can never both win.
     */
    public suspend fun transition(id: SessionId, expected: Set<SessionStatus>, change: StatusChange): Boolean

    public suspend fun findByStatus(vararg status: SessionStatus): List<Session>

    /** Deletes the row; transcript and notes go with it. Audio files are the caller's job (HLD 9.3). */
    public suspend fun delete(id: SessionId)
}

/** A status change and what it does to the processing fields. */
public sealed interface StatusChange {
    public val target: SessionStatus

    /** To `Waiting` with a new queue position. Clears stage, progress and failure. */
    public data class Enqueue(val queuedAtEpochMs: Long, val trigger: PipelineTrigger) : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Waiting
    }

    /** `Waiting` to `Processing`. */
    public data object Start : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Processing
    }

    /** To `Ready`. Clears progress and failure. */
    public data object Complete : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Ready
    }

    /** To `Failed`, recording why. */
    public data class Fail(val failure: ProcessingFailure) : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Failed
    }

    /** To `Cancelled` (shown as "Failed: Cancelled" with Retry). */
    public data object Cancel : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Cancelled
    }

    /** `Processing` back to `Waiting` after process death, keeping its queue position. */
    public data object Requeue : StatusChange {
        override val target: SessionStatus get() = SessionStatus.Waiting
    }
}
