package dev.notesmaker.core.domain.model

import java.io.File

/*
 * File handles that cross module boundaries. They wrap java.io.File or a content URI string, which
 * keeps the domain free of Android types (NFR-64). Only :core:data builds paths or turns a URI string
 * into an android.net.Uri.
 */

/** A resolved, readable audio file. */
public data class AudioFile(val file: File)

/** An import copied into app storage but not yet attached to a session. */
public data class TempAudio(val file: File, val displayName: String?)

/** Where a recording writes: the crash-safe ADTS stream and the final M4A (ADR-14). */
public data class RecordingTarget(val adts: File, val m4a: File)

/** A user-picked file, as an opaque content URI string. */
public data class ImportSource(val uri: String, val displayName: String?, val mimeType: String?)

/** A file in app storage that belongs to no session row. */
public data class OrphanFile(val file: File)

public data class AudioInfo(val durationMs: Long, val mimeType: String, val sampleRate: Int, val channels: Int)

/** One window of 16 kHz mono float PCM on the session timeline. The array is owned by the receiver. */
public class PcmWindow(
    public val startMs: Long,
    public val samples: FloatArray,
    public val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    public val endMs: Long get() = startMs + samples.size * 1000L / sampleRate

    public companion object {
        public const val DEFAULT_SAMPLE_RATE: Int = 16_000
    }
}
