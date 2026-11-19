package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.port.Clock
import dev.notesmaker.core.domain.port.ForegroundState
import dev.notesmaker.core.domain.port.IdGenerator
import dev.notesmaker.core.domain.port.Logger
import dev.notesmaker.core.domain.port.ProcessingScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A clock that only moves when the test moves it. */
public class FakeClock(startEpochMs: Long = TestData.T0) : Clock {
    private val now = AtomicLong(startEpochMs)

    override fun nowEpochMs(): Long = now.get()

    public fun advanceBy(ms: Long) {
        now.addAndGet(ms)
    }

    public fun set(epochMs: Long) {
        now.set(epochMs)
    }
}

/** Hands out [TestData.sessionId] 1, 2, 3, ... */
public class FakeIdGenerator(start: Int = 1) : IdGenerator {
    private val next = AtomicInteger(start)

    override fun newSessionId(): SessionId = TestData.sessionId(next.getAndIncrement())
}

public class FakeForegroundState(foreground: Boolean = true) : ForegroundState {
    override val isForeground: MutableStateFlow<Boolean> = MutableStateFlow(foreground)
}

/** Records calls so tests can assert the worker was (not) started or cancelled. */
public class FakeProcessingScheduler : ProcessingScheduler {
    private val ensureCalls = AtomicInteger()
    public val ensureRunningCalls: Int get() = ensureCalls.get()
    public val cancelled: MutableList<SessionId> = CopyOnWriteArrayList()

    override fun ensureRunning() {
        ensureCalls.incrementAndGet()
    }

    override fun cancelCurrent(id: SessionId) {
        cancelled += id
    }
}

/** Keeps log lines in memory; [lines] are "LEVEL tag: message". */
public class FakeLogger : Logger {
    public val lines: MutableList<String> = CopyOnWriteArrayList()

    override fun debug(tag: String, message: String) {
        lines += "D $tag: $message"
    }

    override fun info(tag: String, message: String) {
        lines += "I $tag: $message"
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        lines += "W $tag: $message"
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        lines += "E $tag: $message"
    }
}
