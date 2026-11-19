package dev.notesmaker.core.data.repository

import dev.notesmaker.core.data.db.NotesDao
import dev.notesmaker.core.data.db.toDomain
import dev.notesmaker.core.data.db.toEntities
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.NotesEdit
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.port.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class RoomNotesRepository @Inject constructor(private val dao: NotesDao) : NotesRepository {

    override fun observe(id: SessionId): Flow<Notes?> = dao.observe(id.value).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Notes? = dao.get(id.value)?.toDomain()

    override suspend fun replace(notes: Notes) {
        val (entity, sections, highlights) = notes.toEntities()
        dao.replace(entity, sections, highlights)
    }

    override suspend fun applyEdit(id: SessionId, edit: NotesEdit) {
        // Each UPDATE matches no row when the notes or the index do not exist, which is the documented no-op.
        when (edit) {
            is NotesEdit.Summary -> dao.editSummary(id.value, edit.summary)
            is NotesEdit.Section -> dao.editSection(id.value, edit.index, edit.heading, edit.points)
            is NotesEdit.HighlightText -> dao.editHighlight(id.value, edit.index, edit.text)
            is NotesEdit.RemoveHighlight -> dao.removeHighlight(id.value, edit.index)
        }
    }
}
