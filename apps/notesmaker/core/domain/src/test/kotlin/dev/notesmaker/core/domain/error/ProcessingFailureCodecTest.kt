package dev.notesmaker.core.domain.error

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.ModelId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessingFailureCodecTest {

    /** One of each subtype. The `when` makes adding a subtype a compile error here until it is listed (NFR-23). */
    private val all: List<ProcessingFailure> = listOf(
        ProcessingFailure.ModelMissing(ModelId("whisper-base.en-q5_1")),
        ProcessingFailure.AudioUnreadable("unsupported codec"),
        ProcessingFailure.OutOfMemory,
        ProcessingFailure.EngineUnavailable(EngineId("llm.gemini-nano"), "quota\nexceeded"),
        ProcessingFailure.StorageFull,
        ProcessingFailure.TimedOut,
        ProcessingFailure.Cancelled,
        ProcessingFailure.Unknown("NullPointerException"),
    ).onEach {
        when (it) {
            is ProcessingFailure.ModelMissing, is ProcessingFailure.AudioUnreadable, ProcessingFailure.OutOfMemory,
            is ProcessingFailure.EngineUnavailable, ProcessingFailure.StorageFull, ProcessingFailure.TimedOut,
            ProcessingFailure.Cancelled, is ProcessingFailure.Unknown,
            -> Unit
        }
    }

    @Test
    fun `every failure round-trips`() {
        all.forEach { failure ->
            val encoded = ProcessingFailureCodec.encode(failure)
            assertEquals(failure, ProcessingFailureCodec.decode(encoded.code, encoded.detail))
        }
    }

    @Test
    fun `codes are unique and stable`() {
        // These strings are stored in databases on users' phones. Never change one.
        assertEquals(
            listOf("model_missing", "audio_unreadable", "out_of_memory", "engine_unavailable", "storage_full", "timed_out", "cancelled", "unknown"),
            all.map { it.code },
        )
    }

    @Test
    fun `an unknown code decodes to Unknown instead of crashing`() {
        val decoded = ProcessingFailureCodec.decode("gpu_lost", "detail")
        assertIs<ProcessingFailure.Unknown>(decoded)
        assertTrue("gpu_lost" in decoded.detail)
    }

    @Test
    fun `missing detail decodes safely`() {
        assertEquals(ProcessingFailure.ModelMissing(ModelId("")), ProcessingFailureCodec.decode("model_missing", null))
        assertEquals(ProcessingFailure.EngineUnavailable(EngineId(""), ""), ProcessingFailureCodec.decode("engine_unavailable", null))
    }
}
