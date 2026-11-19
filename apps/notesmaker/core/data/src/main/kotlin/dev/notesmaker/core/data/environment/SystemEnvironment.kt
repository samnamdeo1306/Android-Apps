package dev.notesmaker.core.data.environment

import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.port.Clock
import dev.notesmaker.core.domain.port.IdGenerator
import java.util.UUID
import javax.inject.Inject

internal class SystemClock @Inject constructor() : Clock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

/** Random UUIDs: they never collide across a future export or import of sessions (ADR-23). */
internal class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newSessionId(): SessionId = SessionId(UUID.randomUUID().toString())
}
