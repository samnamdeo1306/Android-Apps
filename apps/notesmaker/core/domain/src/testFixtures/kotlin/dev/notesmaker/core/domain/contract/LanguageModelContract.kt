package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.engine.Capability
import dev.notesmaker.core.domain.engine.CapabilityRequest
import dev.notesmaker.core.domain.engine.CompletionRequest
import dev.notesmaker.core.domain.engine.LanguageModel
import dev.notesmaker.core.domain.error.LanguageModelFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.QualityTier
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The rules of `LanguageModel` (ADR-16, ADR-27, NFR-71). Real adapters run it on a supported device. */
public abstract class LanguageModelContract {

    /** A model that is ready while the app is in the foreground. */
    protected abstract fun subject(): LanguageModel

    /** True for models that only run in the foreground, such as Gemini Nano through ML Kit. */
    protected open val foregroundOnly: Boolean = false

    private fun request(prompt: String, maxOutputTokens: Int = 64) =
        CompletionRequest(system = "You write short study notes.", prompt = prompt, responseSchema = null, maxOutputTokens = maxOutputTokens)

    @Test
    public fun oversizedInputIsAFailureValueNotAnException(): Unit = runTest {
        val model = subject()
        val tooLong = "word ".repeat(model.inputTokenBudget * 2)
        val result = model.complete(request(tooLong))
        assertEquals(Outcome.Failure(LanguageModelFailure.InputTooLong), result)
    }

    @Test
    public fun aSmallRequestSucceedsWithinRoughlyTheOutputLimit(): Unit = runTest {
        val model = subject()
        val text = assertIs<Outcome.Success<String>>(model.complete(request("Summarise: plants use light.", maxOutputTokens = 32))).value
        assertTrue(model.estimateTokens(text) <= 32 * 3 / 2, "reply of ${model.estimateTokens(text)} tokens for a limit of 32")
    }

    @Test
    public fun tokenEstimateGrowsWithText(): Unit = runTest {
        val model = subject()
        assertTrue(model.estimateTokens("a") > 0)
        assertTrue(model.estimateTokens("word ".repeat(100)) > model.estimateTokens("word ".repeat(10)))
    }

    @Test
    public fun capabilityReflectsTheForeground(): Unit = runTest {
        val model = subject()
        val foreground = model.capability(CapabilityRequest(LanguageTag("en"), QualityTier.Fast, inForeground = true))
        val background = model.capability(CapabilityRequest(LanguageTag("en"), QualityTier.Fast, inForeground = false))
        assertEquals(Capability.Ready, foreground)
        if (foregroundOnly) assertEquals(Capability.NeedsForeground, background) else assertEquals(Capability.Ready, background)
    }
}
