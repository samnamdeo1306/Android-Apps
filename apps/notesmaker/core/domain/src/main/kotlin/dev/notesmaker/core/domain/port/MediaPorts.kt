package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.AudioInfo
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.PcmWindow
import dev.notesmaker.core.domain.model.PlayerState
import dev.notesmaker.core.domain.model.RecordingQuality
import dev.notesmaker.core.domain.model.RecordingState
import dev.notesmaker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Streams any supported file as 16 kHz mono float PCM, never decoding the whole file at once (ADR-08). **Port.** */
public interface AudioDecoder {
    public fun decode(file: AudioFile, fromMs: Long = 0, windowMs: Long = DEFAULT_WINDOW_MS): Flow<PcmWindow>

    public companion object {
        public const val DEFAULT_WINDOW_MS: Long = 30_000
    }
}

/** **Port.** */
public interface AudioProbe {
    public suspend fun probe(file: AudioFile): Outcome<AudioInfo, CaptureFailure>
}

/** Drives the recording service (LLD Section 4). **Port.** */
public interface RecordingController {
    public val state: StateFlow<RecordingState>

    /** Returns once the session row exists and the microphone is open (FR-01). */
    public suspend fun start(language: LanguageTag, quality: RecordingQuality): Outcome<SessionId, CaptureFailure>

    public fun pause()

    public fun resume()

    /** Finalization is asynchronous; observe [state] for `Stopped`. */
    public fun stop()

    public fun discard()
}

/** Voice-note playback for the session details screen (HLD 4.2, rule 3 exception). **Port.** */
public interface VoiceNotePlayer {
    public val state: StateFlow<PlayerState>

    public suspend fun load(id: SessionId, audio: AudioRef)

    public fun play()

    public fun pause()

    public fun seekTo(positionMs: Long)

    /** +/- 10 s (FR-61). */
    public fun skipBy(deltaMs: Long)

    public fun release()
}
