package dev.notesmaker.core.domain.engine

import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import kotlinx.coroutines.flow.Flow

/**
 * Turns audio into timed text (ADR-21). **Plug-in API.**
 */
public interface TranscriptionEngine : Engine {
    /**
     * Transcribes [audio] from [TranscriptionRequest.resumeFromMs] to the end and emits events as it goes.
     *
     * Contract (checked by `TranscriptionEngineContract`, NFR-71):
     *  - `Committed.untilMs` is strictly increasing and never exceeds the audio duration.
     *  - Every segment in a `Committed` event lies within `[previous untilMs, untilMs]`, and segments
     *    are in time order without overlap. Times are absolute on the session timeline.
     *  - Everything before `untilMs` is final: the caller persists it and will resume after it.
     *  - The flow completes normally at the end of the audio, or throws `CancellationException`.
     *  - Expected failures are emitted as a terminal `Failed` event, never thrown.
     */
    public fun transcribe(audio: AudioFile, request: TranscriptionRequest): Flow<TranscriptionEvent>
}

public data class TranscriptionRequest(
    val language: LanguageTag,
    val modelId: ModelId?,
    val resumeFromMs: Long = 0,
    /** Tail of already-committed text, for context across a resume. */
    val continuationText: String? = null,
) {
    init {
        require(resumeFromMs >= 0) { "resumeFromMs must be >= 0" }
    }
}

public sealed interface TranscriptionEvent {
    public data class Committed(val segments: List<RawSegment>, val untilMs: Long) : TranscriptionEvent

    public data class Progress(val processedMs: Long, val totalMs: Long) : TranscriptionEvent

    public data class Failed(val failure: ProcessingFailure) : TranscriptionEvent
}

public data class RawSegment(val startMs: Long, val endMs: Long, val text: String)
