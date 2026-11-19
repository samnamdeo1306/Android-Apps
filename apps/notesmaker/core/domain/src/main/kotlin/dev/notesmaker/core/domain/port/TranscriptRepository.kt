package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.TranscriptCheckpoint
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/** Transcripts with their resume checkpoint (ADR-13). **Port**, checked by `TranscriptRepositoryContract`. */
public interface TranscriptRepository {
    public fun observe(id: SessionId): Flow<Transcript?>

    public suspend fun get(id: SessionId): Transcript?

    /**
     * Appends segments and moves the checkpoint in one transaction. Segment indexes are the
     * caller's; reusing an index fails loudly instead of duplicating text (NFR-21).
     */
    public suspend fun appendCommitted(
        id: SessionId,
        header: TranscriptHeader,
        segments: List<TranscriptSegment>,
        committedUntilMs: Long,
    )

    /** Null when transcription never started. */
    public suspend fun checkpoint(id: SessionId): TranscriptCheckpoint?

    /** Marks the transcript complete and bumps its revision. */
    public suspend fun markComplete(id: SessionId)

    /**
     * Removes the segments and resets the checkpoint to the start, for re-transcription. The
     * revision is kept, so the next completion gets a revision no earlier notes were built from (ADR-33).
     */
    public suspend fun clear(id: SessionId)
}
