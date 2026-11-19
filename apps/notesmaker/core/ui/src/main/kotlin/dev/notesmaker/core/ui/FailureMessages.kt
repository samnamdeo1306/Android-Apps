package dev.notesmaker.core.ui

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.ProcessingFailure

/** What the user can do about a failure (NFR-23). */
enum class RecoveryAction { Retry, InstallModel, FreeUpSpace, OpenSettings, None }

data class FailureMessage(val text: UiText, val action: RecoveryAction)

/**
 * The single mapping from domain failures to user messages (API design Section 1.3). The `when`
 * expressions are exhaustive over sealed types, so a new failure subtype does not compile until it
 * has a message and an action here.
 */
fun ProcessingFailure.toMessage(): FailureMessage = when (this) {
    is ProcessingFailure.ModelMissing -> FailureMessage(UiText.Resource(R.string.failure_model_missing), RecoveryAction.InstallModel)
    is ProcessingFailure.AudioUnreadable -> FailureMessage(UiText.Resource(R.string.failure_audio_unreadable), RecoveryAction.None)
    ProcessingFailure.OutOfMemory -> FailureMessage(UiText.Resource(R.string.failure_out_of_memory), RecoveryAction.Retry)
    is ProcessingFailure.EngineUnavailable -> FailureMessage(UiText.Resource(R.string.failure_engine_unavailable), RecoveryAction.Retry)
    ProcessingFailure.StorageFull -> FailureMessage(UiText.Resource(R.string.failure_storage_full), RecoveryAction.FreeUpSpace)
    ProcessingFailure.TimedOut -> FailureMessage(UiText.Resource(R.string.failure_timed_out), RecoveryAction.Retry)
    ProcessingFailure.Cancelled -> FailureMessage(UiText.Resource(R.string.failure_cancelled), RecoveryAction.Retry)
    is ProcessingFailure.Unknown -> FailureMessage(UiText.Resource(R.string.failure_unknown), RecoveryAction.Retry)
}

fun CaptureFailure.toMessage(): FailureMessage = when (this) {
    CaptureFailure.PermissionDenied -> FailureMessage(UiText.Resource(R.string.capture_permission_denied), RecoveryAction.OpenSettings)
    CaptureFailure.MicrophoneBusy -> FailureMessage(UiText.Resource(R.string.capture_microphone_busy), RecoveryAction.Retry)
    is CaptureFailure.InsufficientStorage -> FailureMessage(UiText.Resource(R.string.capture_insufficient_storage), RecoveryAction.FreeUpSpace)
    is CaptureFailure.UnsupportedFormat -> FailureMessage(UiText.Resource(R.string.capture_unsupported_format), RecoveryAction.None)
    CaptureFailure.Corrupt -> FailureMessage(UiText.Resource(R.string.capture_corrupt), RecoveryAction.None)
    is CaptureFailure.TooLong -> FailureMessage(
        UiText.Resource(R.string.capture_too_long, listOf(limitMs / MS_PER_HOUR)),
        RecoveryAction.None,
    )
    CaptureFailure.SourceUnavailable -> FailureMessage(UiText.Resource(R.string.capture_source_unavailable), RecoveryAction.Retry)
}

private const val MS_PER_HOUR = 3_600_000L
