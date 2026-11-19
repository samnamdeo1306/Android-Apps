package dev.notesmaker.engine.whisper

/**
 * Loads `libnotesmaker_whisper.so` once per process.
 *
 * ADR-24 also plans an ARMv8.2 (dot product + FP16) variant chosen from the CPU's features. It is
 * added with the transcription spike, which measures whether the gain is worth a second native
 * build (ADR-32); until then every device loads this baseline library.
 */
internal object WhisperNativeLoader {
    const val LIBRARY = "notesmaker_whisper"

    private val loaded: Unit by lazy { System.loadLibrary(LIBRARY) }

    /** Throws UnsatisfiedLinkError if the library is missing for this ABI. Safe to call repeatedly. */
    fun load() = loaded
}
