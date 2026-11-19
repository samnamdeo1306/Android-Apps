package dev.notesmaker.core.data.repository

import dev.notesmaker.core.data.db.SessionDao
import dev.notesmaker.core.data.db.StoredNames
import dev.notesmaker.core.data.db.toDomain
import dev.notesmaker.core.data.db.toEntity
import dev.notesmaker.core.domain.error.ProcessingFailureCodec
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.NewSession
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SessionListItem
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.pipeline.PipelineTriggerCodec
import dev.notesmaker.core.domain.port.Clock
import dev.notesmaker.core.domain.port.SessionRepository
import dev.notesmaker.core.domain.port.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room adapter for [SessionRepository]. Main-safe: Room runs queries on its own executors. */
internal class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val clock: Clock,
) : SessionRepository {

    override fun observeAll(): Flow<List<SessionListItem>> =
        dao.observeList().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observe(id: SessionId): Flow<Session?> = dao.observe(id.value).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun get(id: SessionId): Session? = dao.get(id.value)?.toDomain()

    override suspend fun create(draft: NewSession): Session {
        val entity = draft.toEntity(clock.nowEpochMs())
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun nextWaiting(): Session? = dao.firstInQueue(StoredNames.status(SessionStatus.Waiting))?.toDomain()

    override suspend fun rename(id: SessionId, title: String, source: TitleSource) =
        dao.rename(id.value, title, StoredNames.titleSource(source), clock.nowEpochMs())

    override suspend fun setDuration(id: SessionId, durationMs: Long) = dao.setDuration(id.value, durationMs, clock.nowEpochMs())

    override suspend fun setAudio(id: SessionId, audio: AudioRef, durationMs: Long) =
        dao.setAudio(id.value, audio.relativePath, durationMs, clock.nowEpochMs())

    override suspend fun updateProgress(id: SessionId, stage: StageId, progress: Float?) =
        dao.updateProgress(id.value, stage.value, progress, clock.nowEpochMs())

    override suspend fun setNotesUpgradeAvailable(id: SessionId, available: Boolean) =
        dao.setNotesUpgradeAvailable(id.value, available, clock.nowEpochMs())

    override suspend fun markRecovered(id: SessionId) = dao.markRecovered(id.value, clock.nowEpochMs())

    override suspend fun transition(id: SessionId, expected: Set<SessionStatus>, change: StatusChange): Boolean {
        if (expected.isEmpty()) return false
        val from = expected.map(StoredNames::status)
        val to = StoredNames.status(change.target)
        val now = clock.nowEpochMs()
        val rows = when (change) {
            is StatusChange.Enqueue -> dao.enqueue(id.value, from, to, change.queuedAtEpochMs, PipelineTriggerCodec.encode(change.trigger), now)
            StatusChange.Start, StatusChange.Complete, StatusChange.Cancel -> dao.moveClearingFailure(id.value, from, to, now)
            is StatusChange.Fail -> {
                val encoded = ProcessingFailureCodec.encode(change.failure)
                dao.fail(id.value, from, to, encoded.code, encoded.detail, now)
            }
            StatusChange.Requeue -> dao.moveKeepingQueue(id.value, from, to, now)
        }
        return rows == 1
    }

    override suspend fun findByStatus(vararg status: SessionStatus): List<Session> =
        if (status.isEmpty()) emptyList() else dao.findByStatus(status.map(StoredNames::status)).map { it.toDomain() }

    override suspend fun delete(id: SessionId) = dao.delete(id.value)
}
