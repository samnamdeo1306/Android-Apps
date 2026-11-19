package dev.notesmaker.core.domain.model

import dev.notesmaker.core.domain.port.Sensitive
import dev.notesmaker.core.domain.testing.TestData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelInvariantsTest {

    @Test
    fun `segments cannot end before they start or start before zero`() {
        assertFailsWith<IllegalArgumentException> { TranscriptSegment(0, 5_000, 4_000, "x") }
        assertFailsWith<IllegalArgumentException> { TranscriptSegment(0, -1, 4_000, "x") }
        assertFailsWith<IllegalArgumentException> { TranscriptSegment(-1, 0, 1, "x") }
    }

    @Test
    fun `progress must be a fraction`() {
        assertFailsWith<IllegalArgumentException> { ProcessingState(progress = 1.5f) }
        assertEquals(0.5f, ProcessingState(progress = 0.5f).progress)
    }

    @Test
    fun `a new session starts as Recording or Waiting`() {
        assertFailsWith<IllegalArgumentException> { TestData.newSession(1, status = SessionStatus.Ready) }
        assertFailsWith<IllegalArgumentException> { TestData.newSession(1, status = SessionStatus.Waiting, queuedAtEpochMs = null) }
    }

    @Test
    fun `session IDs cannot be blank`() {
        assertFailsWith<IllegalArgumentException> { SessionId(" ") }
    }

    @Test
    fun `pcm window end is computed from the sample count`() {
        assertEquals(31_000, PcmWindow(startMs = 1_000, samples = FloatArray(16_000 * 30)).endMs)
    }

    @Test
    fun `sensitive text never prints its content`() {
        val secret = Sensitive("The exam is on Friday")
        assertEquals("<redacted 21 chars>", secret.toString())
        assertEquals("note: <redacted 21 chars>", "note: $secret")
    }
}
