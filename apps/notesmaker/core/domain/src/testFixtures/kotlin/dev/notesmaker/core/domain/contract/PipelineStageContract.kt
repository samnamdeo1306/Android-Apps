package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.pipeline.PipelineStage
import dev.notesmaker.core.domain.pipeline.StageContext
import dev.notesmaker.core.domain.pipeline.StageOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of `PipelineStage` (ADR-22, NFR-21): idempotent, cheap completion check, resumable after cancellation. */
public abstract class PipelineStageContract {

    /** A stage with its inputs prepared for one session, and a way to count its outputs. */
    public interface Harness {
        public val stage: PipelineStage

        /** The context for the prepared session, reporting progress to [progress]. */
        public suspend fun context(progress: ProgressSink = ProgressSink.None): StageContext

        /** How many copies of the stage's output exist for the session (segments, notes rows, ...). 1 when done once. */
        public suspend fun outputCopies(): Int
    }

    /** A fresh harness whose stage has not run. */
    protected abstract fun harness(): Harness

    @Test
    public fun notCompleteBeforeRunAndCompleteAfter(): Unit = runTest {
        val h = harness()
        assertFalse(h.stage.isComplete(h.context()))
        assertEquals(StageOutcome.Completed, h.stage.run(h.context()))
        assertTrue(h.stage.isComplete(h.context()))
    }

    @Test
    public fun runningTwiceLeavesOneCopyOfTheOutput(): Unit = runTest {
        val h = harness()
        h.stage.run(h.context())
        val once = h.outputCopies()
        h.stage.run(h.context())
        assertEquals(once, h.outputCopies(), "running twice changed the output (not idempotent)")
    }

    @Test
    public fun cancellationLeavesAResumableState(): Unit = runTest {
        val h = harness()
        // Cancel at the stage's first progress report, as a user cancel or WorkManager stop would.
        assertFails { h.stage.run(h.context { throw CancellationException("cancelled by test") }) }
        assertEquals(StageOutcome.Completed, h.stage.run(h.context()))
        assertTrue(h.stage.isComplete(h.context()))
        val afterResume = h.outputCopies()
        h.stage.run(h.context())
        assertEquals(afterResume, h.outputCopies(), "a cancelled run left output that a later run duplicated")
    }

    @Test
    public fun progressStaysWithinZeroAndOne(): Unit = runTest {
        val h = harness()
        val reported = mutableListOf<Float>()
        h.stage.run(h.context { reported += it })
        reported.forEach { assertTrue(it in 0f..1f, "progress $it out of range") }
    }
}
