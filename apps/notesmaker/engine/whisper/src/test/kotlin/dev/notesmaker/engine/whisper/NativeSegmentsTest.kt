package dev.notesmaker.engine.whisper

import dev.notesmaker.core.domain.engine.RawSegment
import org.junit.Test
import kotlin.test.assertEquals

class NativeSegmentsTest {

    @Test
    fun `times become milliseconds on the session timeline`() {
        val segments = listOf(NativeSegment(t0 = 0, t1 = 250, text = " Good morning, everyone."))
        assertEquals(listOf(RawSegment(60_000, 62_500, "Good morning, everyone.")), segments.toRawSegments(windowStartMs = 60_000))
    }

    @Test
    fun `non-speech markers and blank text are dropped`() {
        val segments = listOf(
            NativeSegment(0, 100, "[BLANK_AUDIO]"),
            NativeSegment(100, 200, " (music) "),
            NativeSegment(200, 300, "[ Silence ] (applause)"),
            NativeSegment(300, 400, "   "),
            NativeSegment(400, 500, "Photosynthesis (from Greek) means putting together with light."),
        )
        assertEquals(
            listOf(RawSegment(4_000, 5_000, "Photosynthesis (from Greek) means putting together with light.")),
            segments.toRawSegments(0),
        )
    }

    @Test
    fun `an end before the start is clamped to the start`() {
        assertEquals(listOf(RawSegment(1_000, 1_000, "x")), listOf(NativeSegment(100, 90, "x")).toRawSegments(0))
    }
}
