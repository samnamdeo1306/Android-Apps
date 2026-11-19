package dev.notesmaker.core.domain.model

public sealed interface RecordingState {
    public data object Idle : RecordingState

    public data class Active(
        val sessionId: SessionId,
        val elapsedMs: Long,
        /** 0..1, for the level meter (FR-01). */
        val level: Float,
        /** Null while recording. */
        val paused: PauseReason?,
    ) : RecordingState

    /** Remuxing after stop. */
    public data class Finishing(val sessionId: SessionId) : RecordingState

    public data class Stopped(val sessionId: SessionId, val result: StopResult) : RecordingState
}

public enum class PauseReason { User, PhoneCall, MicrophoneTaken, AudioFocusLost }

public enum class StopResult { Saved, Discarded, LowStorage, Failed }

public data class PlayerState(
    val sessionId: SessionId? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)
