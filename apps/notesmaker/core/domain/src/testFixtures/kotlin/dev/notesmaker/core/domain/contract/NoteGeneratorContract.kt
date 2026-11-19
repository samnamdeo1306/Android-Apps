package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.NoteGenerator
import dev.notesmaker.core.domain.engine.NotesInput
import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.DraftNotes
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The rules of `NoteGenerator.generate` (FR-40 to FR-44, NFR-71). When `NotesValidator` lands, the
 * suite also checks that every output passes it after clamping (API design Section 11).
 */
public abstract class NoteGeneratorContract {

    protected abstract fun subject(): NoteGenerator

    /** A non-empty finished transcript. Defaults to a 12-segment, one-minute fixture. */
    protected open fun transcript(): Transcript = TestData.transcript(segmentCount = 12)

    /** The built-in generator must be deterministic; LLM-backed ones need not be. */
    protected open val deterministic: Boolean = true

    private fun input(t: Transcript = transcript()) = NotesInput(
        transcript = t,
        durationMs = t.segments.maxOf { it.endMs },
        language = t.language,
        sessionCreatedAtEpochMs = TestData.T0,
    )

    private suspend fun generate(progress: ProgressSink = ProgressSink.None): DraftNotes =
        assertIs<Outcome.Success<DraftNotes>>(subject().generate(input(), progress), "generator failed on a non-empty transcript").value

    @Test
    public fun titleIsOneNonBlankLine(): Unit = runTest {
        val title = generate().title
        assertTrue(title.isNotBlank(), "empty title")
        assertFalse('\n' in title, "multi-line title: $title")
    }

    @Test
    public fun atLeastOneSectionWithAHeading(): Unit = runTest {
        val sections = generate().sections
        assertTrue(sections.isNotEmpty(), "no sections")
        sections.forEach { assertTrue(it.heading.isNotBlank(), "blank heading in $it") }
    }

    @Test
    public fun sectionsAreInTimeOrderNearTheAudio(): Unit = runTest {
        val duration = input().durationMs
        val starts = generate().sections.map { it.startMs }
        assertEquals(starts.sorted(), starts, "sections out of order")
        // Times may be slightly out of range (the validator clamps them) but not invented.
        starts.forEach { assertTrue(it in -1_000..duration + 1_000, "section start $it far outside 0..$duration") }
    }

    @Test
    public fun summaryIsNotEmpty(): Unit = runTest {
        assertTrue(generate().summary.any { it.isNotBlank() })
    }

    @Test
    public fun progressStaysWithinZeroAndOne(): Unit = runTest {
        val reported = mutableListOf<Float>()
        generate { reported += it }
        reported.forEach { assertTrue(it in 0f..1f, "progress $it out of range") }
    }

    @Test
    public fun sameInputGivesSameNotesWhenDeterministic(): Unit = runTest {
        if (!deterministic) return@runTest
        assertEquals(generate(), generate())
    }

    @Test
    public fun aCancelledCallerGetsCancellationNotAResult(): Unit = runTest {
        var returned = false
        var cancelled = false
        val job = launch {
            cancel()
            try {
                subject().generate(input(), ProgressSink.None)
                returned = true
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        job.join()
        assertTrue(cancelled && !returned, "generate() must check for cancellation (ensureActive or a suspending call)")
    }
}
