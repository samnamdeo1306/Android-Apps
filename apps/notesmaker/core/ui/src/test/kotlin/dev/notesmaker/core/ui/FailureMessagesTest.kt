package dev.notesmaker.core.ui

import dev.notesmaker.core.domain.error.CaptureFailure
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.ModelId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** NFR-23: every failure reaches the user as a message with a recovery action, one test per failure. */
class FailureMessagesTest {

    private val processing = mapOf(
        ProcessingFailure.ModelMissing(ModelId("m")) to RecoveryAction.InstallModel,
        ProcessingFailure.AudioUnreadable("x") to RecoveryAction.None,
        ProcessingFailure.OutOfMemory to RecoveryAction.Retry,
        ProcessingFailure.EngineUnavailable(EngineId("e"), "r") to RecoveryAction.Retry,
        ProcessingFailure.StorageFull to RecoveryAction.FreeUpSpace,
        ProcessingFailure.TimedOut to RecoveryAction.Retry,
        ProcessingFailure.Cancelled to RecoveryAction.Retry,
        ProcessingFailure.Unknown("bug") to RecoveryAction.Retry,
    )

    private val capture = mapOf(
        CaptureFailure.PermissionDenied to RecoveryAction.OpenSettings,
        CaptureFailure.MicrophoneBusy to RecoveryAction.Retry,
        CaptureFailure.InsufficientStorage(10, 1) to RecoveryAction.FreeUpSpace,
        CaptureFailure.UnsupportedFormat("audio/x-ms-wma") to RecoveryAction.None,
        CaptureFailure.Corrupt to RecoveryAction.None,
        CaptureFailure.TooLong(4 * 3_600_000L, 3 * 3_600_000L) to RecoveryAction.None,
        CaptureFailure.SourceUnavailable to RecoveryAction.Retry,
    )

    @Test
    fun `each processing failure has its own message and the right action`() {
        val messages = processing.map { (failure, action) ->
            val message = failure.toMessage()
            assertEquals(action, message.action, "action for $failure")
            assertIs<UiText.Resource>(message.text).id
        }
        assertEquals(messages.size, messages.toSet().size, "two failures share a message")
    }

    @Test
    fun `each capture failure has its own message and the right action`() {
        val messages = capture.map { (failure, action) ->
            val message = failure.toMessage()
            assertEquals(action, message.action, "action for $failure")
            assertIs<UiText.Resource>(message.text).id
        }
        assertEquals(messages.size, messages.toSet().size, "two failures share a message")
    }

    @Test
    fun `the length limit is shown in hours`() {
        val text = assertIs<UiText.Resource>(CaptureFailure.TooLong(4 * 3_600_000L, 3 * 3_600_000L).toMessage().text)
        assertEquals(listOf<Any>(3L), text.args)
    }
}
