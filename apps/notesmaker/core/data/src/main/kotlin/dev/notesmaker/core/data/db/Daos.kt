package dev.notesmaker.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    /** Only the list columns, newest first (LLD Section 3.1, NFR-15). */
    @Query("SELECT id, title, created_at, duration_ms, status, stage, progress FROM sessions ORDER BY created_at DESC")
    fun observeList(): Flow<List<SessionListRow>>

    /** Uses idx_sessions_queue. A Waiting row always has queued_at; NULLs sort last defensively. */
    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY queued_at IS NULL, queued_at, created_at LIMIT 1")
    suspend fun firstInQueue(status: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status IN (:statuses) ORDER BY created_at")
    suspend fun findByStatus(statuses: List<String>): List<SessionEntity>

    @Query("UPDATE sessions SET title = :title, title_source = :titleSource, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, titleSource: String, now: Long)

    @Query("UPDATE sessions SET duration_ms = :durationMs, updated_at = :now WHERE id = :id")
    suspend fun setDuration(id: String, durationMs: Long, now: Long)

    @Query("UPDATE sessions SET audio_path = :audioPath, duration_ms = :durationMs, updated_at = :now WHERE id = :id")
    suspend fun setAudio(id: String, audioPath: String, durationMs: Long, now: Long)

    @Query("UPDATE sessions SET stage = :stage, progress = :progress, updated_at = :now WHERE id = :id")
    suspend fun updateProgress(id: String, stage: String, progress: Float?, now: Long)

    @Query("UPDATE sessions SET notes_upgrade_available = :available, updated_at = :now WHERE id = :id")
    suspend fun setNotesUpgradeAvailable(id: String, available: Boolean, now: Long)

    @Query("UPDATE sessions SET recovered = 1, updated_at = :now WHERE id = :id")
    suspend fun markRecovered(id: String, now: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    /*
     * Compare-and-set transitions (ADR-22). Each is one UPDATE whose WHERE clause checks the current
     * status, so SQLite applies it atomically; the returned row count says whether it applied.
     * The field effects match StatusChange's documentation and FakeSessionRepository.
     */

    @Query(
        """UPDATE sessions SET status = :to, queued_at = :queuedAt, pipeline_trigger = :trigger, stage = NULL,
           progress = NULL, failure_code = NULL, failure_detail = NULL, updated_at = :now
           WHERE id = :id AND status IN (:expected)""",
    )
    suspend fun enqueue(id: String, expected: List<String>, to: String, queuedAt: Long, trigger: String, now: Long): Int

    @Query(
        """UPDATE sessions SET status = :to, progress = NULL, failure_code = NULL, failure_detail = NULL, updated_at = :now
           WHERE id = :id AND status IN (:expected)""",
    )
    suspend fun moveClearingFailure(id: String, expected: List<String>, to: String, now: Long): Int

    @Query(
        """UPDATE sessions SET status = :to, progress = NULL, failure_code = :code, failure_detail = :detail, updated_at = :now
           WHERE id = :id AND status IN (:expected)""",
    )
    suspend fun fail(id: String, expected: List<String>, to: String, code: String, detail: String?, now: Long): Int

    @Query("UPDATE sessions SET status = :to, progress = NULL, updated_at = :now WHERE id = :id AND status IN (:expected)")
    suspend fun moveKeepingQueue(id: String, expected: List<String>, to: String, now: Long): Int
}

@Dao
internal abstract class TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE session_id = :id")
    abstract suspend fun header(id: String): TranscriptEntity?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE session_id = :id")
    abstract suspend fun getWithSegments(id: String): TranscriptWithSegments?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE session_id = :id")
    abstract fun observeWithSegments(id: String): Flow<TranscriptWithSegments?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHeader(header: TranscriptEntity)

    @Update
    abstract suspend fun updateHeader(header: TranscriptEntity)

    /** ABORT: reusing an index fails loudly instead of duplicating text (NFR-21). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("UPDATE transcripts SET complete = 1, revision = revision + 1 WHERE session_id = :id")
    abstract suspend fun markComplete(id: String)

    @Query("DELETE FROM transcript_segments WHERE session_id = :id")
    abstract suspend fun deleteSegments(id: String)

    @Query("UPDATE transcripts SET committed_until_ms = 0, next_index = 0, complete = 0 WHERE session_id = :id")
    abstract suspend fun resetCheckpoint(id: String)

    /** Segments and checkpoint in one transaction (ADR-13): a crash leaves both or neither. */
    @Transaction
    open suspend fun appendCommitted(header: TranscriptEntity, segments: List<TranscriptSegmentEntity>) {
        val existing = header(header.sessionId)
        if (existing == null) {
            insertHeader(header)
        } else {
            updateHeader(
                existing.copy(
                    engineId = header.engineId,
                    modelId = header.modelId,
                    language = header.language,
                    committedUntilMs = header.committedUntilMs,
                    nextIndex = maxOf(existing.nextIndex, header.nextIndex),
                ),
            )
        }
        if (segments.isNotEmpty()) insertSegments(segments)
    }

    /** Keeps the revision (ADR-33). */
    @Transaction
    open suspend fun clear(id: String) {
        deleteSegments(id)
        resetCheckpoint(id)
    }
}

@Dao
internal abstract class NotesDao {
    @Transaction
    @Query("SELECT * FROM notes WHERE session_id = :id")
    abstract suspend fun get(id: String): NotesWithChildren?

    @Transaction
    @Query("SELECT * FROM notes WHERE session_id = :id")
    abstract fun observe(id: String): Flow<NotesWithChildren?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertNotes(notes: NotesEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSections(sections: List<NoteSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHighlights(highlights: List<HighlightEntity>)

    @Query("DELETE FROM notes WHERE session_id = :id")
    abstract suspend fun deleteNotes(id: String)

    @Query("DELETE FROM note_sections WHERE session_id = :id")
    abstract suspend fun deleteSections(id: String)

    @Query("DELETE FROM highlights WHERE session_id = :id")
    abstract suspend fun deleteHighlights(id: String)

    @Query("UPDATE notes SET summary_json = :summary, summary_edited = 1 WHERE session_id = :id")
    abstract suspend fun editSummary(id: String, summary: List<String>)

    @Query("UPDATE note_sections SET heading = :heading, points_json = :points, edited = 1 WHERE session_id = :id AND idx = :index")
    abstract suspend fun editSection(id: String, index: Int, heading: String, points: List<String>)

    @Query("UPDATE highlights SET text = :text, edited = 1 WHERE session_id = :id AND idx = :index")
    abstract suspend fun editHighlight(id: String, index: Int, text: String)

    @Query("SELECT * FROM highlights WHERE session_id = :id ORDER BY idx")
    abstract suspend fun highlights(id: String): List<HighlightEntity>

    /** Delete-then-insert in one transaction makes the generate stage idempotent (NFR-21). */
    @Transaction
    open suspend fun replace(notes: NotesEntity, sections: List<NoteSectionEntity>, highlights: List<HighlightEntity>) {
        deleteHighlights(notes.sessionId)
        deleteSections(notes.sessionId)
        deleteNotes(notes.sessionId)
        insertNotes(notes)
        insertSections(sections)
        insertHighlights(highlights)
    }

    /** Re-numbers the rest so indexes stay 0..n-1 without gaps. */
    @Transaction
    open suspend fun removeHighlight(id: String, index: Int) {
        val current = highlights(id)
        if (index !in current.indices) return
        deleteHighlights(id)
        insertHighlights(current.filterIndexed { i, _ -> i != index }.mapIndexed { i, h -> h.copy(idx = i) })
    }
}
