package dev.notesmaker.core.domain.engine

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.testing.FakeNoteGenerator
import dev.notesmaker.core.domain.testing.FakeTranscriptionEngine
import dev.notesmaker.core.domain.testing.engineInfo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EngineRegistryTest {

    @Test
    fun `distinct IDs pass`() {
        val registry = EngineRegistry(setOf(FakeTranscriptionEngine()), setOf(FakeNoteGenerator()))
        registry.verifyUniqueIds()
        assertTrue(registry.duplicateIds().isEmpty())
    }

    @Test
    fun `a duplicate ID across engine kinds fails fast and names the ID`() {
        val clash = EngineId("whisper.cpp")
        val registry = EngineRegistry(
            setOf(FakeTranscriptionEngine(info = engineInfo(clash, EngineKind.Transcription))),
            setOf(FakeNoteGenerator(info = engineInfo(clash, EngineKind.Notes))),
        )
        assertEquals(setOf(clash), registry.duplicateIds())
        val error = assertFailsWith<IllegalStateException> { registry.verifyUniqueIds() }
        assertTrue("whisper.cpp" in error.message.orEmpty())
    }
}
