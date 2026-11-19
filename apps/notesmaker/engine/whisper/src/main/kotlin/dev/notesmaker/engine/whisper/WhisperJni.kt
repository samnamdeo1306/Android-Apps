package dev.notesmaker.engine.whisper

/**
 * The JNI surface of whisper.cpp (LLD Section 6.1), kept as small as possible so the native side
 * has no app logic. Implemented in `src/main/cpp/jni_bridge.cpp`; call [WhisperNativeLoader.load]
 * first. Members are public (not `internal`) because Kotlin mangles internal JVM names, which would
 * break the JNI symbol names.
 */
internal object WhisperJni {
    /** Loads a ggml model file. Returns a context handle, or 0 if the model could not be loaded. */
    external fun initContext(modelPath: String, useGpu: Boolean): Long

    /** Frees a handle from [initContext]. A 0 handle is ignored. */
    external fun freeContext(ctx: Long)

    /**
     * Transcribes one window of 16 kHz mono PCM. Segment times are in 10 ms units from the window start.
     * Returns null if [requestAbort] stopped it; throws IllegalStateException if whisper.cpp failed.
     */
    external fun transcribe(
        ctx: Long,
        samples: FloatArray,
        language: String,
        initialPrompt: String?,
        nThreads: Int,
        useVad: Boolean,
        vadModelPath: String?,
    ): Array<NativeSegment>?

    /** Makes a running [transcribe] return null at its next decoder step (cancellation, LLD Section 5.4). */
    external fun requestAbort(ctx: Long)

    external fun clearAbort(ctx: Long)

    /** SIMD features compiled in and detected, for benchmark records (ADR-19). */
    external fun systemInfo(): String

    /** whisper.cpp's own version string, to check the pinned version is the one running. */
    external fun version(): String
}

/** One segment as whisper.cpp returns it; [t0] and [t1] are in 10 ms units from the window start. */
internal class NativeSegment(@JvmField val t0: Long, @JvmField val t1: Long, @JvmField val text: String)
