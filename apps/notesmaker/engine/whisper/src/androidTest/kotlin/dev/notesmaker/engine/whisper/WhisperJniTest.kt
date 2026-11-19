package dev.notesmaker.engine.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the native library is built for this ABI, links, and that the JNI names match. Model-based
 * transcription tests (TranscriptionEngineContract with real audio) arrive with the engine in the
 * transcription spike (LLD Section 12 step 3), together with a small test model.
 */
@RunWith(AndroidJUnit4::class)
class WhisperJniTest {

    @Before
    fun load() = WhisperNativeLoader.load()

    @Test
    fun loadingTwiceIsHarmless() {
        WhisperNativeLoader.load()
    }

    @Test
    fun runsThePinnedWhisperVersion() {
        assertEquals(BuildConfig.WHISPER_VERSION, WhisperJni.version())
    }

    @Test
    fun reportsCpuFeatures() {
        val info = WhisperJni.systemInfo()
        assertTrue(info.isNotBlank(), "empty system info")
        assertTrue("CPU" in info.uppercase(), "unexpected system info: $info")
    }

    @Test
    fun aMissingModelGivesANullHandleNotACrash() {
        assertEquals(0L, WhisperJni.initContext("/data/local/tmp/does-not-exist.bin", useGpu = false))
    }

    @Test
    fun zeroHandlesAreIgnored() {
        WhisperJni.freeContext(0)
        WhisperJni.requestAbort(0)
        WhisperJni.clearAbort(0)
    }

    @Test(expected = IllegalStateException::class)
    fun transcribeWithoutAContextThrowsAJavaException() {
        WhisperJni.transcribe(0, FloatArray(16_000), "en", null, 1, false, null)
    }
}
