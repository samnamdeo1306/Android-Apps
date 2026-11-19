package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.AudioInfo
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.PauseReason
import dev.notesmaker.core.domain.model.PcmWindow
import dev.notesmaker.core.domain.model.PlayerState
import dev.notesmaker.core.domain.model.RecordingQuality
import dev.notesmaker.core.domain.model.RecordingState
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.StopResult
import dev.notesmaker.core.domain.port.AudioDecoder
import dev.notesmaker.core.domain.port.AudioProbe
import dev.notesmaker.core.domain.port.RecordingController
import dev.notesmaker.core.domain.port.VoiceNotePlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Decodes registered files into windows produced by [samples] (silence by default). */
public class FakeAudioDecoder(
    private val samples: (startMs: Long, count: Int) -> FloatArray = { _, count -> FloatArray(count) },
) : AudioDecoder {
    private val durations = ConcurrentHashMap<File, Long>()

    public fun register(file: AudioFile, durationMs: Long) {
        durations[file.file] = durationMs
    }

    override fun decode(file: AudioFile, fromMs: Long, windowMs: Long): Flow<PcmWindow> = flow {
        val duration = checkNotNull(durations[file.file]) { "FakeAudioDecoder: ${file.file} not registered" }
        var start = fromMs
        while (start < duration) {
            val end = minOf(start + windowMs, duration)
            val count = ((end - start) * PcmWindow.DEFAULT_SAMPLE_RATE / 1000).toInt()
            emit(PcmWindow(start, samples(start, count)))
            start = end
        }
    }
}

public class FakeAudioProbe : AudioProbe {
    private val results = ConcurrentHashMap<File, Outcome<AudioInfo, CaptureFailure>>()

    public fun register(file: AudioFile, result: Outcome<AudioInfo, CaptureFailure>) {
        results[file.file] = result
    }

    override suspend fun probe(file: AudioFile): Outcome<AudioInfo, CaptureFailure> =
        results[file.file] ?: Outcome.Failure(CaptureFailure.Corrupt)
}

/**
 * Recording controller whose state the test drives. [start] succeeds with [nextSessionId] unless
 * [nextStartFailure] is set.
 */
public class FakeRecordingController : RecordingController {
    override val state: MutableStateFlow<RecordingState> = MutableStateFlow(RecordingState.Idle)
    public var nextSessionId: SessionId = TestData.sessionId(1)
    public var nextStartFailure: CaptureFailure? = null
    public val calls: MutableList<String> = CopyOnWriteArrayList()

    override suspend fun start(language: LanguageTag, quality: RecordingQuality): Outcome<SessionId, CaptureFailure> {
        calls += "start($language, $quality)"
        nextStartFailure?.let { return Outcome.Failure(it) }
        state.value = RecordingState.Active(nextSessionId, elapsedMs = 0, level = 0f, paused = null)
        return Outcome.Success(nextSessionId)
    }

    override fun pause() {
        calls += "pause"
        state.update { (it as? RecordingState.Active)?.copy(paused = PauseReason.User) ?: it }
    }

    override fun resume() {
        calls += "resume"
        state.update { (it as? RecordingState.Active)?.copy(paused = null) ?: it }
    }

    override fun stop() {
        calls += "stop"
        state.update { (it as? RecordingState.Active)?.let { a -> RecordingState.Stopped(a.sessionId, StopResult.Saved) } ?: it }
    }

    override fun discard() {
        calls += "discard"
        state.update { (it as? RecordingState.Active)?.let { a -> RecordingState.Stopped(a.sessionId, StopResult.Discarded) } ?: it }
    }
}

public class FakeVoiceNotePlayer : VoiceNotePlayer {
    override val state: MutableStateFlow<PlayerState> = MutableStateFlow(PlayerState())
    public val seeks: MutableList<Long> = CopyOnWriteArrayList()
    public var released: Boolean = false
        private set

    override suspend fun load(id: SessionId, audio: AudioRef) {
        state.value = PlayerState(sessionId = id, durationMs = state.value.durationMs)
    }

    override fun play() {
        state.update { it.copy(playing = true) }
    }

    override fun pause() {
        state.update { it.copy(playing = false) }
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        state.update { it.copy(positionMs = positionMs.coerceIn(0, maxOf(it.durationMs, 0))) }
    }

    override fun skipBy(deltaMs: Long) {
        seekTo(state.value.positionMs + deltaMs)
    }

    override fun release() {
        released = true
        state.value = PlayerState()
    }
}
