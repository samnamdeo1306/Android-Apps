package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.NotesEdit
import dev.notesmaker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow

/** **Port**, checked by `NotesRepositoryContract`. */
public interface NotesRepository {
    public fun observe(id: SessionId): Flow<Notes?>

    public suspend fun get(id: SessionId): Notes?

    /** Replaces all notes of the session in one transaction (NFR-21). */
    public suspend fun replace(notes: Notes)

    /** Applies a user edit and flags the edited item (FR-65). Out-of-range indexes are ignored. */
    public suspend fun applyEdit(id: SessionId, edit: NotesEdit)
}
