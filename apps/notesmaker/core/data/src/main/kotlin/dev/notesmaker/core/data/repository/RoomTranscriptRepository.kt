package dev.notesmaker.core.data.repository

import dev.notesmaker.core.data.db.TranscriptDao
import dev.notesmaker.core.data.db.TranscriptEntity
import dev.notesmaker.core.data.db.toCheckpoint
import dev.notesmaker.core.data.db.toDomain
import dev.notesmaker.core.data.db.toEntity
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.TranscriptCheckpoint
import dev.notesmaker.core.domain.model.TranscriptHeader
import dev.notesmaker.core.domain.model.TranscriptSegment
import dev.notesmaker.core.domain.port.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class RoomTranscriptRepository @Inject constructor(private val dao: TranscriptDao) : TranscriptRepository {

    override fun observe(id: SessionId): Flow<Transcript?> =
        dao.observeWithSegments(id.value).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Transcript? = dao.getWithSegments(id.value)?.toDomain()

    override suspend fun appendCommitted(id: SessionId, header: TranscriptHeader, segments: List<TranscriptSegment>, committedUntilMs: Long) {
        val nextIndex = (segments.maxOfOrNull { it.index } ?: -1) + 1
        dao.appendCommitted(
            header = TranscriptEntity(
                sessionId = id.value,
                engineId = header.engineId.value,
                modelId = header.modelId?.value,
                language = header.language.value,
                committedUntilMs = committedUntilMs,
                nextIndex = nextIndex,
                complete = false,
                revision = 0,
            ),
            segments = segments.map { it.toEntity(id) },
        )
    }

    override suspend fun checkpoint(id: SessionId): TranscriptCheckpoint? = dao.header(id.value)?.toCheckpoint()

    override suspend fun markComplete(id: SessionId) = dao.markComplete(id.value)

    override suspend fun clear(id: SessionId) = dao.clear(id.value)
}
