package dev.notesmaker.core.domain.error

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.ModelId

/**
 * Every failure that can stop processing a session (ADR-20). Persisted by [code] in
 * `sessions.failure_code` through [ProcessingFailureCodec]; a released code is never renamed.
 * Adding a subtype is a Plug-in API change (API design Section 12): every `when` must handle it.
 */
public sealed interface ProcessingFailure {
    public val code: String
    public val retryable: Boolean

    public data class ModelMissing(val modelId: ModelId) : ProcessingFailure {
        override val code: String get() = "model_missing"
        override val retryable: Boolean get() = true
    }

    public data class AudioUnreadable(val detail: String) : ProcessingFailure {
        override val code: String get() = "audio_unreadable"
        override val retryable: Boolean get() = false
    }

    public data object OutOfMemory : ProcessingFailure {
        override val code: String get() = "out_of_memory"
        override val retryable: Boolean get() = true
    }

    public data class EngineUnavailable(val engineId: EngineId, val reason: String) : ProcessingFailure {
        override val code: String get() = "engine_unavailable"
        override val retryable: Boolean get() = true
    }

    public data object StorageFull : ProcessingFailure {
        override val code: String get() = "storage_full"
        override val retryable: Boolean get() = true
    }

    /** Foreground-service time limit or engine timeout. */
    public data object TimedOut : ProcessingFailure {
        override val code: String get() = "timed_out"
        override val retryable: Boolean get() = true
    }

    public data object Cancelled : ProcessingFailure {
        override val code: String get() = "cancelled"
        override val retryable: Boolean get() = true
    }

    /** A bug caught by the pipeline. [detail] is for logs only and never contains transcript text. */
    public data class Unknown(val detail: String) : ProcessingFailure {
        override val code: String get() = "unknown"
        override val retryable: Boolean get() = true
    }
}

/** Failures of creating a session from an input (record or import). */
public sealed interface CaptureFailure {
    public data object PermissionDenied : CaptureFailure

    public data object MicrophoneBusy : CaptureFailure

    public data class InsufficientStorage(val requiredBytes: Long, val freeBytes: Long) : CaptureFailure

    public data class UnsupportedFormat(val mimeType: String?) : CaptureFailure

    public data object Corrupt : CaptureFailure

    public data class TooLong(val durationMs: Long, val limitMs: Long) : CaptureFailure

    /** URI revoked or provider offline. */
    public data object SourceUnavailable : CaptureFailure
}

/** Failures of one language model request (API design Section 5.4). */
public sealed interface LanguageModelFailure {
    /** The app left the foreground or the model was unloaded. */
    public data object NotReady : LanguageModelFailure

    public data object QuotaExceeded : LanguageModelFailure

    public data object InputTooLong : LanguageModelFailure

    public data class Backend(val detail: String) : LanguageModelFailure
}

/** User input rejected by a use case, for example a blank title. */
public sealed interface ValidationFailure {
    public data object Blank : ValidationFailure

    public data class TooLong(val maxLength: Int) : ValidationFailure
}
