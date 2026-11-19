package dev.notesmaker.core.domain.model

public data class Transcript(
    val sessionId: SessionId,
    val engineId: EngineId,
    val modelId: ModelId?,
    val language: LanguageTag,
    val segments: List<TranscriptSegment>,
    /** Bumped each time a transcription completes; notes record which revision they were built from. */
    val revision: Int,
    val complete: Boolean,
)

public data class TranscriptSegment(val index: Int, val startMs: Long, val endMs: Long, val text: String) {
    init {
        require(index >= 0) { "index must be >= 0" }
        require(startMs in 0..endMs) { "segment times must satisfy 0 <= startMs <= endMs, were $startMs..$endMs" }
    }
}

/** Which engine and model produced a transcript. Changing engine restarts transcription (LLD Section 5.3). */
public data class TranscriptHeader(val engineId: EngineId, val modelId: ModelId?, val language: LanguageTag)

/** Resume point for transcription (ADR-13): everything before [committedUntilMs] is final. */
public data class TranscriptCheckpoint(
    val header: TranscriptHeader,
    val committedUntilMs: Long,
    val nextIndex: Int,
    val complete: Boolean,
    val revision: Int,
)
