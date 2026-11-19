package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.contract.PersistenceUnderTest
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.EditFlags
import dev.notesmaker.core.domain.model.NewSession
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.NotesEdit
import dev.notesmaker.core.domain.model.ProcessingState
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SessionListItem
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.TranscriptCheckpoint
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.model.TranscriptSegment
import dev.notesmaker.core.domain.port.NotesRepository
import dev.notesmaker.core.domain.port.SessionRepository
import dev.notesmaker.core.domain.port.StatusChange
import dev.notesmaker.core.domain.port.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-in for the database: one store shared by the three repository fakes, so
 * deleting a session cascades to its transcript and notes exactly like the Room foreign keys.
 *
 * Every write is one atomic [MutableStateFlow.update], which gives the compare-and-set
 * [SessionRepository.transition] the same all-or-nothing behaviour as the SQL `UPDATE ... WHERE`.
 * The fakes pass the same contract suites as the Room repositories (ADR-31), so a test written
 * against them tells the truth about production.
 */
public class FakePersistence : PersistenceUnderTest {
    private val store = MutableStateFlow(Store())

    override val sessions: FakeSessionRepository = FakeSessionRepository(store)
    override val transcripts: FakeTranscriptRepository = FakeTranscriptRepository(store)
    override val notes: FakeNotesRepository = FakeNotesRepository(store)

    override fun close() {}
}

internal data class StoredTranscript(
    val header: TranscriptHeader,
    val segments: List<TranscriptSegment>,
    val committedUntilMs: Long,
    val nextIndex: Int,
    val complete: Boolean,
    val revision: Int,
)

internal data class Store(
    val sessions: Map<SessionId, Session> = emptyMap(),
    val transcripts: Map<SessionId, StoredTranscript> = emptyMap(),
    val notes: Map<SessionId, Notes> = emptyMap(),
)

public class FakeSessionRepository internal constructor(private val store: MutableStateFlow<Store>) : SessionRepository {

    override fun observeAll(): Flow<List<SessionListItem>> = store
        .map { s -> s.sessions.values.sortedByDescending { it.createdAtEpochMs }.map { it.toListItem() } }
        .distinctUntilChanged()

    override fun observe(id: SessionId): Flow<Session?> = store.map { it.sessions[id] }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Session? = store.value.sessions[id]

    override suspend fun create(draft: NewSession): Session {
        val session = Session(
            id = draft.id,
            title = draft.title,
            titleSource = draft.titleSource,
            createdAtEpochMs = draft.createdAtEpochMs,
            origin = draft.origin,
            language = draft.language,
            durationMs = draft.durationMs,
            audio = draft.audio,
            status = draft.status,
            processing = ProcessingState(queuedAtEpochMs = draft.queuedAtEpochMs, trigger = draft.trigger),
            notesUpgradeAvailable = false,
        )
        store.update { s ->
            check(draft.id !in s.sessions) { "Session ${draft.id} already exists" }
            s.copy(sessions = s.sessions + (draft.id to session))
        }
        return session
    }

    override suspend fun nextWaiting(): Session? = store.value.sessions.values
        .filter { it.status == SessionStatus.Waiting }
        .minWithOrNull(compareBy<Session>({ it.processing.queuedAtEpochMs ?: Long.MAX_VALUE }, { it.createdAtEpochMs }))

    override suspend fun rename(id: SessionId, title: String, source: TitleSource): Unit =
        modify(id) { it.copy(title = title, titleSource = source) }

    override suspend fun setDuration(id: SessionId, durationMs: Long): Unit = modify(id) { it.copy(durationMs = durationMs) }

    override suspend fun setAudio(id: SessionId, audio: AudioRef, durationMs: Long): Unit =
        modify(id) { it.copy(audio = audio, durationMs = durationMs) }

    override suspend fun updateProgress(id: SessionId, stage: StageId, progress: Float?): Unit =
        modify(id) { it.copy(processing = it.processing.copy(stage = stage, progress = progress)) }

    override suspend fun setNotesUpgradeAvailable(id: SessionId, available: Boolean): Unit =
        modify(id) { it.copy(notesUpgradeAvailable = available) }

    override suspend fun markRecovered(id: SessionId): Unit = modify(id) { it.copy(recovered = true) }

    override suspend fun transition(id: SessionId, expected: Set<SessionStatus>, change: StatusChange): Boolean {
        var applied = false
        store.update { s ->
            val current = s.sessions[id]
            applied = current != null && current.status in expected
            if (applied) s.copy(sessions = s.sessions + (id to current!!.apply(change))) else s
        }
        return applied
    }

    override suspend fun findByStatus(vararg status: SessionStatus): List<Session> =
        store.value.sessions.values.filter { it.status in status }.sortedBy { it.createdAtEpochMs }

    override suspend fun delete(id: SessionId) {
        store.update { s -> s.copy(sessions = s.sessions - id, transcripts = s.transcripts - id, notes = s.notes - id) }
    }

    private fun modify(id: SessionId, change: (Session) -> Session) {
        store.update { s -> s.sessions[id]?.let { s.copy(sessions = s.sessions + (id to change(it))) } ?: s }
    }

    private fun Session.toListItem() = SessionListItem(id, title, createdAtEpochMs, durationMs, status, processing.stage, processing.progress)

    /** The field effects of each change, mirrored by the SQL in `SessionDao`. */
    private fun Session.apply(change: StatusChange): Session {
        val p = processing
        val next = when (change) {
            is StatusChange.Enqueue -> p.copy(stage = null, progress = null, failure = null, queuedAtEpochMs = change.queuedAtEpochMs, trigger = change.trigger)
            StatusChange.Start -> p.copy(progress = null, failure = null)
            StatusChange.Complete -> p.copy(progress = null, failure = null)
            is StatusChange.Fail -> p.copy(progress = null, failure = change.failure)
            StatusChange.Cancel -> p.copy(progress = null, failure = null)
            StatusChange.Requeue -> p.copy(progress = null)
        }
        return copy(status = change.target, processing = next)
    }
}

public class FakeTranscriptRepository internal constructor(private val store: MutableStateFlow<Store>) : TranscriptRepository {

    override fun observe(id: SessionId): Flow<Transcript?> = store.map { it.transcripts[id]?.toTranscript(id) }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Transcript? = store.value.transcripts[id]?.toTranscript(id)

    override suspend fun appendCommitted(id: SessionId, header: TranscriptHeader, segments: List<TranscriptSegment>, committedUntilMs: Long) {
        store.update { s ->
            check(id in s.sessions) { "No session $id (foreign key)" }
            val existing = s.transcripts[id] ?: StoredTranscript(header, emptyList(), 0, 0, complete = false, revision = 0)
            val taken = existing.segments.map { it.index }.toSet()
            val reused = segments.map { it.index }.filter { it in taken || segments.count { s2 -> s2.index == it } > 1 }
            check(reused.isEmpty()) { "Segment indexes already stored for $id: $reused (primary key)" }
            val next = maxOf(existing.nextIndex, (segments.maxOfOrNull { it.index } ?: -1) + 1)
            val updated = existing.copy(
                header = header,
                segments = (existing.segments + segments).sortedBy { it.index },
                committedUntilMs = committedUntilMs,
                nextIndex = next,
            )
            s.copy(transcripts = s.transcripts + (id to updated))
        }
    }

    override suspend fun checkpoint(id: SessionId): TranscriptCheckpoint? = store.value.transcripts[id]?.let {
        TranscriptCheckpoint(it.header, it.committedUntilMs, it.nextIndex, it.complete, it.revision)
    }

    override suspend fun markComplete(id: SessionId): Unit =
        modify(id) { it.copy(complete = true, revision = it.revision + 1) }

    override suspend fun clear(id: SessionId): Unit =
        modify(id) { it.copy(segments = emptyList(), committedUntilMs = 0, nextIndex = 0, complete = false) }

    private fun modify(id: SessionId, change: (StoredTranscript) -> StoredTranscript) {
        store.update { s -> s.transcripts[id]?.let { s.copy(transcripts = s.transcripts + (id to change(it))) } ?: s }
    }

    private fun StoredTranscript.toTranscript(id: SessionId) =
        Transcript(id, header.engineId, header.modelId, header.language, segments, revision, complete)
}

public class FakeNotesRepository internal constructor(private val store: MutableStateFlow<Store>) : NotesRepository {

    override fun observe(id: SessionId): Flow<Notes?> = store.map { it.notes[id] }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Notes? = store.value.notes[id]

    override suspend fun replace(notes: Notes) {
        store.update { s ->
            check(notes.sessionId in s.sessions) { "No session ${notes.sessionId} (foreign key)" }
            s.copy(notes = s.notes + (notes.sessionId to notes))
        }
    }

    override suspend fun applyEdit(id: SessionId, edit: NotesEdit) {
        store.update { s ->
            val notes = s.notes[id] ?: return@update s
            s.copy(notes = s.notes + (id to notes.apply(edit)))
        }
    }

    private fun Notes.apply(edit: NotesEdit): Notes = when (edit) {
        is NotesEdit.Summary -> copy(summary = edit.summary, edits = EditFlags(summaryEdited = true))
        is NotesEdit.Section -> copy(
            sections = sections.mapIndexed { i, section ->
                if (i == edit.index) section.copy(heading = edit.heading, points = edit.points, edited = true) else section
            },
        )
        is NotesEdit.HighlightText -> copy(
            highlights = highlights.mapIndexed { i, h -> if (i == edit.index) h.copy(text = edit.text, edited = true) else h },
        )
        is NotesEdit.RemoveHighlight -> copy(highlights = highlights.filterIndexed { i, _ -> i != edit.index })
    }
}
