package dev.notesmaker.core.domain.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {

    private val ok: Outcome<Int, String> = Outcome.Success(2)
    private val bad: Outcome<Int, String> = Outcome.Failure("boom")

    @Test
    fun `map transforms only success`() {
        assertEquals(Outcome.Success(4), ok.map { it * 2 })
        assertEquals(bad, bad.map { it * 2 })
    }

    @Test
    fun `flatMap chains and short-circuits`() {
        assertEquals(Outcome.Success("2"), ok.flatMap { Outcome.Success(it.toString()) })
        assertEquals(Outcome.Failure("next"), ok.flatMap { Outcome.Failure("next") })
        var called = false
        bad.flatMap<Int, String, Int> { called = true; Outcome.Success(it) }
        assertTrue(!called)
    }

    @Test
    fun `mapFailure transforms only failure`() {
        assertEquals<Outcome<Int, Int>>(Outcome.Failure(4), bad.mapFailure { it.length })
        assertEquals<Outcome<Int, Int>>(Outcome.Success(2), ok.mapFailure { it.length })
    }

    @Test
    fun `side effects run on the matching branch`() {
        val seen = mutableListOf<String>()
        ok.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" }
        bad.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" }
        assertEquals(listOf("s2", "fboom"), seen)
    }

    @Test
    fun `fold and accessors`() {
        assertEquals("2", ok.fold({ it.toString() }, { it }))
        assertEquals("boom", bad.fold({ it.toString() }, { it }))
        assertEquals(2, ok.getOrNull())
        assertNull(bad.getOrNull())
        assertEquals("boom", bad.failureOrNull())
        assertEquals(Outcome.Success(1), 1.asSuccess())
        assertEquals(Outcome.Failure("x"), "x".asFailure())
    }

    @Test
    fun `runCatchingCancellable captures ordinary exceptions`() {
        val result = runCatchingCancellable { error("broken") }
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(Result.success(3), runCatchingCancellable { 3 })
    }

    @Test
    fun `runCatchingCancellable rethrows cancellation`() {
        assertFailsWith<CancellationException> { runCatchingCancellable { throw CancellationException("stop") } }
    }

    @Test
    fun `a cancelled coroutine stays cancelled through runCatchingCancellable`() = runTest {
        val job = async {
            runCatchingCancellable { awaitCancellation() }
            "kept running"
        }
        yield()
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
    }
}
