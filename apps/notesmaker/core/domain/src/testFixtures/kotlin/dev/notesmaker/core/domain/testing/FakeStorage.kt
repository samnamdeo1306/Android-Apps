package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.ImportSource
import dev.notesmaker.core.domain.model.OrphanFile
import dev.notesmaker.core.domain.model.RecordingTarget
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.TempAudio
import dev.notesmaker.core.domain.model.UserPreferences
import dev.notesmaker.core.domain.port.AudioStore
import dev.notesmaker.core.domain.port.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Audio store that only computes paths under [root] and records what happened; it never touches
 * the disk. Use the real `FileAudioStore` in instrumented tests for file behaviour.
 */
public class FakeAudioStore(private val root: File = File("/fake-audio-store")) : AudioStore {
    /** What the next [importToTemp] returns; null means success with a generated temp file. */
    public var nextImportResult: Outcome<TempAudio, CaptureFailure>? = null
    public var importSizeBytes: Long = 1_000_000
    public var free: Long = 10L * 1024 * 1024 * 1024
    public var orphanSessionIds: Set<SessionId> = emptySet()

    public val committed: MutableList<Pair<TempAudio, SessionId>> = CopyOnWriteArrayList()
    public val discarded: MutableList<TempAudio> = CopyOnWriteArrayList()
    public val deletedSessions: MutableList<SessionId> = CopyOnWriteArrayList()
    private val tempCounter = AtomicInteger()

    override suspend fun newRecordingTarget(id: SessionId): RecordingTarget =
        RecordingTarget(adts = File(root, "sessions/$id/audio.aac"), m4a = File(root, "sessions/$id/audio.m4a"))

    override suspend fun importToTemp(source: ImportSource, onProgress: (Long) -> Unit): Outcome<TempAudio, CaptureFailure> {
        nextImportResult?.let { return it }
        onProgress(importSizeBytes)
        return Outcome.Success(TempAudio(File(root, "imports/${tempCounter.incrementAndGet()}.tmp"), source.displayName))
    }

    override suspend fun commitTemp(temp: TempAudio, id: SessionId): AudioRef {
        committed += temp to id
        val extension = temp.displayName?.substringAfterLast('.', missingDelimiterValue = "m4a") ?: "m4a"
        return AudioRef("sessions/$id/audio.$extension")
    }

    override suspend fun discardTemp(temp: TempAudio) {
        discarded += temp
    }

    override suspend fun open(ref: AudioRef): AudioFile = AudioFile(File(root, ref.relativePath))

    override suspend fun deleteSession(id: SessionId) {
        deletedSessions += id
    }

    override suspend fun freeBytes(): Long = free

    override suspend fun orphans(knownIds: Set<SessionId>): List<OrphanFile> =
        (orphanSessionIds - knownIds).map { OrphanFile(File(root, "sessions/$it")) }
}

public class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    override val preferences: MutableStateFlow<UserPreferences> = MutableStateFlow(initial)

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        preferences.update(transform)
    }
}
