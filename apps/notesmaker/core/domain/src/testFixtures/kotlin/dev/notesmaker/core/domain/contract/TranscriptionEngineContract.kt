package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.Capability
import dev.notesmaker.core.domain.engine.CapabilityRequest
import dev.notesmaker.core.domain.engine.TranscriptionEngine
import dev.notesmaker.core.domain.engine.TranscriptionEvent
import dev.notesmaker.core.domain.engine.TranscriptionRequest
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioFile
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.QualityTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The rules of `TranscriptionEngine.transcribe` (ADR-21, NFR-71). Every transcription engine's test
 * class extends this and supplies the engine and two audio fixtures. On-device engines run it as an
 * instrumented test with real audio.
 */
public abstract class TranscriptionEngineContract {

    /** An audio file with its exact duration. */
    public data class ContractAudio(val audio: AudioFile, val durationMs: Long)

    /** A fresh engine whose model is installed. */
    protected abstract fun subject(): TranscriptionEngine

    /** At least two windows long, with speech. */
    protected abstract fun speechAudio(): ContractAudio

    /** Silence or room noise only. */
    protected abstract fun silentAudio(): ContractAudio

    /** The same engine without its model, or null if the engine needs no model. */
    protected open fun subjectWithoutModel(): TranscriptionEngine? = null

    protected open val language: LanguageTag = LanguageTag("en")
    protected open val modelId: ModelId? = null

    private fun request(resumeFromMs: Long = 0) = TranscriptionRequest(language, modelId, resumeFromMs)

    private suspend fun commits(engine: TranscriptionEngine, audio: ContractAudio, resumeFromMs: Long = 0): List<TranscriptionEvent.Committed> {
        val events = engine.transcribe(audio.audio, request(resumeFromMs)).toList()
        events.filterIsInstance<TranscriptionEvent.Failed>().firstOrNull()?.let { fail("Unexpected failure: ${it.failure}") }
        return events.filterIsInstance<TranscriptionEvent.Committed>()
    }

    @Test
    public fun committedUntilIsStrictlyIncreasingAndWithinTheAudio(): Unit = runTest {
        val audio = speechAudio()
        val untils = commits(subject(), audio).map { it.untilMs }
        assertTrue(untils.isNotEmpty(), "expected at least one commit")
        untils.zipWithNext().forEach { (a, b) -> assertTrue(b > a, "untilMs must strictly increase: $untils") }
        assertTrue(untils.last() <= audio.durationMs, "untilMs ${untils.last()} exceeds duration ${audio.durationMs}")
    }

    @Test
    public fun segmentsLieInsideTheirCommitInOrderWithoutOverlap(): Unit = runTest {
        var previousUntil = 0L
        var previousEnd = 0L
        commits(subject(), speechAudio()).forEach { commit ->
            commit.segments.forEach { s ->
                assertTrue(s.startMs >= previousUntil && s.endMs <= commit.untilMs, "segment $s outside [$previousUntil, ${commit.untilMs}]")
                assertTrue(s.startMs <= s.endMs, "segment $s ends before it starts")
                assertTrue(s.startMs >= previousEnd, "segment $s overlaps the previous one ending at $previousEnd")
                previousEnd = s.endMs
            }
            previousUntil = commit.untilMs
        }
    }

    @Test
    public fun speechProducesText(): Unit = runTest {
        val text = commits(subject(), speechAudio()).flatMap { it.segments }.joinToString(" ") { it.text }
        assertTrue(text.isNotBlank(), "speech audio produced no text")
    }

    @Test
    public fun resumingFromTheMiddleProducesNothingBeforeIt(): Unit = runTest {
        val audio = speechAudio()
        val resumeAt = audio.durationMs / 2
        val resumed = commits(subject(), audio, resumeFromMs = resumeAt)
        assertTrue(resumed.isNotEmpty(), "a resumed transcription must commit up to the end")
        resumed.flatMap { it.segments }.forEach { assertTrue(it.startMs >= resumeAt, "segment $it starts before resume point $resumeAt") }
        assertTrue(resumed.first().untilMs > resumeAt)
    }

    @Test
    public fun resumingAtTheEndCompletesWithoutText(): Unit = runTest {
        val audio = speechAudio()
        val resumed = commits(subject(), audio, resumeFromMs = audio.durationMs)
        assertTrue(resumed.flatMap { it.segments }.isEmpty())
    }

    @Test
    public fun silenceProducesNoText(): Unit = runTest {
        val text = commits(subject(), silentAudio()).flatMap { it.segments }.joinToString("") { it.text.trim() }
        assertEquals("", text, "silent audio must not produce invented text")
    }

    @Test
    public fun collectionStopsWhenTheCollectorIsCancelled(): Unit = runTest {
        // `first()` cancels the flow after one event; the engine must stop instead of finishing the file.
        val first = subject().transcribe(speechAudio().audio, request()).first()
        assertTrue(first is TranscriptionEvent.Committed || first is TranscriptionEvent.Progress)
    }

    @Test
    public fun missingModelIsACapabilityAndAFailedEventNotAnException(): Unit = runTest {
        val engine = subjectWithoutModel() ?: return@runTest
        val capability = engine.capability(CapabilityRequest(language, QualityTier.Fast, inForeground = true))
        assertTrue(capability is Capability.NeedsModel || capability is Capability.Unsupported, "capability was $capability")

        val events = engine.transcribe(speechAudio().audio, request()).toList()
        val failed = assertIs<TranscriptionEvent.Failed>(events.single())
        assertIs<ProcessingFailure.ModelMissing>(failed.failure)
    }
}
