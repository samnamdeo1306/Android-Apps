package dev.notesmaker.core.domain.pipeline

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.testing.FakePipelineStage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PipelineTypesTest {

    @Test
    fun `triggers round-trip through their persisted form`() {
        listOf(
            PipelineTrigger.Initial,
            PipelineTrigger.Retry,
            PipelineTrigger.RegenerateNotes(null),
            PipelineTrigger.RegenerateNotes(EngineId("llm.gemini-nano")),
        ).forEach { assertEquals(it, PipelineTriggerCodec.decode(PipelineTriggerCodec.encode(it))) }
    }

    @Test
    fun `persisted trigger strings are stable`() {
        assertEquals("initial", PipelineTriggerCodec.encode(PipelineTrigger.Initial))
        assertEquals("regenerate:notes.basic", PipelineTriggerCodec.encode(PipelineTrigger.RegenerateNotes(EngineId("notes.basic"))))
    }

    @Test
    fun `an unknown trigger decodes to Retry`() {
        assertEquals(PipelineTrigger.Retry, PipelineTriggerCodec.decode("something-new"))
    }

    @Test
    fun `a stage plan rejects duplicate stage IDs`() {
        assertFailsWith<IllegalArgumentException> {
            StagePlan(listOf(FakePipelineStage(StageId("transcribe")), FakePipelineStage(StageId("transcribe"))))
        }
    }
}
