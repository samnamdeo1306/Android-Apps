package dev.notesmaker.core.domain.error

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.ModelId

/**
 * Converts a [ProcessingFailure] to and from its persisted form: the stable `code` and a detail
 * string holding the failure's parameters (`sessions.failure_code`, `sessions.failure_detail`).
 *
 * Lives in the domain, not in `:core:data`, so every adapter that persists failures agrees on the
 * format and the round trip is tested once on the JVM.
 */
public object ProcessingFailureCodec {

    public data class Encoded(val code: String, val detail: String?)

    private const val SEPARATOR = '\n'

    public fun encode(failure: ProcessingFailure): Encoded = when (failure) {
        is ProcessingFailure.ModelMissing -> Encoded(failure.code, failure.modelId.value)
        is ProcessingFailure.AudioUnreadable -> Encoded(failure.code, failure.detail)
        is ProcessingFailure.EngineUnavailable -> Encoded(failure.code, failure.engineId.value + SEPARATOR + failure.reason)
        is ProcessingFailure.Unknown -> Encoded(failure.code, failure.detail)
        ProcessingFailure.OutOfMemory,
        ProcessingFailure.StorageFull,
        ProcessingFailure.TimedOut,
        ProcessingFailure.Cancelled,
        -> Encoded(failure.code, null)
    }

    /**
     * Never throws: an unknown code (for example written by a newer app version before a downgrade)
     * becomes [ProcessingFailure.Unknown], which the UI shows with a Retry action (LLD Section 3.1).
     */
    public fun decode(code: String, detail: String?): ProcessingFailure = when (code) {
        "model_missing" -> ProcessingFailure.ModelMissing(ModelId(detail.orEmpty()))
        "audio_unreadable" -> ProcessingFailure.AudioUnreadable(detail.orEmpty())
        "out_of_memory" -> ProcessingFailure.OutOfMemory
        "engine_unavailable" -> {
            val engine = detail.orEmpty().substringBefore(SEPARATOR)
            val reason = detail.orEmpty().substringAfter(SEPARATOR, missingDelimiterValue = "")
            ProcessingFailure.EngineUnavailable(EngineId(engine), reason)
        }
        "storage_full" -> ProcessingFailure.StorageFull
        "timed_out" -> ProcessingFailure.TimedOut
        "cancelled" -> ProcessingFailure.Cancelled
        "unknown" -> ProcessingFailure.Unknown(detail.orEmpty())
        else -> ProcessingFailure.Unknown("unrecognised failure code '$code'" + (detail?.let { ": $it" } ?: ""))
    }
}
