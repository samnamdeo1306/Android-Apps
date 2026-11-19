package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.ImportSource
import dev.notesmaker.core.domain.model.OrphanFile
import dev.notesmaker.core.domain.model.RecordingTarget
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.TempAudio

/** The only owner of audio file paths (LLD Section 3.4). **Port.** */
public interface AudioStore {
    public suspend fun newRecordingTarget(id: SessionId): RecordingTarget

    public suspend fun importToTemp(source: ImportSource, onProgress: (copiedBytes: Long) -> Unit): Outcome<TempAudio, CaptureFailure>

    public suspend fun commitTemp(temp: TempAudio, id: SessionId): AudioRef

    public suspend fun discardTemp(temp: TempAudio)

    public suspend fun open(ref: AudioRef): AudioFile

    public suspend fun deleteSession(id: SessionId)

    public suspend fun freeBytes(): Long

    public suspend fun orphans(knownIds: Set<SessionId>): List<OrphanFile>
}
