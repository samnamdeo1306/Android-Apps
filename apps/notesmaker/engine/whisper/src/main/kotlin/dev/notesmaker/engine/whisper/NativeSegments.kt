package dev.notesmaker.engine.whisper

import dev.notesmaker.core.domain.engine.RawSegment

/** whisper.cpp timestamps are in units of 10 ms. */
internal const val WHISPER_TIME_UNIT_MS = 10L

/**
 * Markers whisper prints for non-speech, such as `[BLANK_AUDIO]`, `(music)` or `[ Silence ]`.
 * A segment that is only such markers is dropped (LLD Section 6.2).
 */
private val NON_SPEECH = Regex("""^\s*(?:[\[(][^\])]*[\])]\s*)+$""")

/**
 * Converts one window's native segments to segments on the session timeline: times to
 * milliseconds offset by [windowStartMs], text trimmed, non-speech markers and empty text dropped.
 */
internal fun List<NativeSegment>.toRawSegments(windowStartMs: Long): List<RawSegment> =
    mapNotNull { native ->
        val text = native.text.trim()
        if (text.isEmpty() || NON_SPEECH.matches(text)) return@mapNotNull null
        val start = windowStartMs + native.t0 * WHISPER_TIME_UNIT_MS
        val end = windowStartMs + native.t1 * WHISPER_TIME_UNIT_MS
        RawSegment(startMs = start, endMs = maxOf(start, end), text = text)
    }
