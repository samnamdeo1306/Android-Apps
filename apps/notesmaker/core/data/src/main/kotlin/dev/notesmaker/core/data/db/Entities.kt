package dev.notesmaker.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Schema version 1 (LLD Section 3.1, ADR-23). Enums and IDs are stored as stable strings, never
 * ordinals; child tables use (session_id, idx) keys and cascade on session delete.
 */

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["status", "queued_at"], name = "idx_sessions_queue"),
        Index(value = ["created_at"], name = "idx_sessions_created"),
    ],
)
internal data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "title_source") val titleSource: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val origin: String,
    @ColumnInfo(name = "origin_name") val originName: String?,
    val language: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    val status: String,
    val stage: String?,
    val progress: Float?,
    @ColumnInfo(name = "failure_code") val failureCode: String?,
    @ColumnInfo(name = "failure_detail") val failureDetail: String?,
    @ColumnInfo(name = "queued_at") val queuedAt: Long?,
    // "trigger" is an SQL keyword; the column is named pipeline_trigger so hand-written SQL needs no quoting.
    @ColumnInfo(name = "pipeline_trigger") val pipelineTrigger: String?,
    @ColumnInfo(name = "notes_upgrade_available", defaultValue = "0") val notesUpgradeAvailable: Boolean,
    @ColumnInfo(defaultValue = "0") val recovered: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The columns of the sessions list query (NFR-15). */
internal data class SessionListRow(
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val status: String,
    val stage: String?,
    val progress: Float?,
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class TranscriptEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "engine_id") val engineId: String,
    @ColumnInfo(name = "model_id") val modelId: String?,
    val language: String,
    @ColumnInfo(name = "committed_until_ms") val committedUntilMs: Long,
    @ColumnInfo(name = "next_index") val nextIndex: Int,
    val complete: Boolean,
    val revision: Int,
)

@Entity(
    tableName = "transcript_segments",
    primaryKeys = ["session_id", "idx"],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class TranscriptSegmentEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val idx: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    val text: String,
)

internal data class TranscriptWithSegments(
    @Embedded val header: TranscriptEntity,
    @Relation(parentColumn = "session_id", entityColumn = "session_id")
    val segments: List<TranscriptSegmentEntity>,
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class NotesEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    /** The generated title; `sessions.title` is what is shown. */
    val title: String,
    @ColumnInfo(name = "summary_json") val summary: List<String>,
    @ColumnInfo(name = "summary_edited") val summaryEdited: Boolean,
    @ColumnInfo(name = "generator_id") val generatorId: String,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "prompt_version") val promptVersion: String?,
    @ColumnInfo(name = "transcript_revision") val transcriptRevision: Int,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
)

@Entity(
    tableName = "note_sections",
    primaryKeys = ["session_id", "idx"],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class NoteSectionEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val idx: Int,
    val heading: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "points_json") val points: List<String>,
    val edited: Boolean,
)

@Entity(
    tableName = "highlights",
    primaryKeys = ["session_id", "idx"],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class HighlightEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val idx: Int,
    val kind: String,
    val text: String,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    val edited: Boolean,
)

internal data class NotesWithChildren(
    @Embedded val notes: NotesEntity,
    @Relation(parentColumn = "session_id", entityColumn = "session_id")
    val sections: List<NoteSectionEntity>,
    @Relation(parentColumn = "session_id", entityColumn = "session_id")
    val highlights: List<HighlightEntity>,
)
