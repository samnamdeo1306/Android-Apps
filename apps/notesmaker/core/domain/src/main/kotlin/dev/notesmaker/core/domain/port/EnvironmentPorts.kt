package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.InstallState
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.QualityTier
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.SpeechModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** **Port.** */
public interface ProcessingScheduler {
    /** Makes sure the single processing worker is running or scheduled (ADR-13). Idempotent. */
    public fun ensureRunning()

    /** Cancels the in-flight work for this session if it is the one running. */
    public fun cancelCurrent(id: SessionId)
}

/** The one owner of "is the app in the foreground" (API design Section 5.1). **Port.** */
public interface ForegroundState {
    public val isForeground: StateFlow<Boolean>
}

/** **Port.** */
public interface ModelCatalog {
    /** Bundled catalog joined with install state. */
    public fun observe(): Flow<List<SpeechModel>>

    public suspend fun get(id: ModelId): SpeechModel?

    public suspend fun defaultFor(engine: EngineId, language: LanguageTag, quality: QualityTier): SpeechModel?

    /** Verified file or null. */
    public suspend fun installedFile(id: ModelId): File?
}

/** **Port.** */
public interface ModelInstaller {
    public fun install(id: ModelId, allowMetered: Boolean = false)

    public fun cancel(id: ModelId)

    public suspend fun delete(id: ModelId)

    public fun observe(id: ModelId): Flow<InstallState>
}

/** Wall-clock time, so tests control "now". **Port.** */
public fun interface Clock {
    public fun nowEpochMs(): Long
}

/** **Port.** */
public fun interface IdGenerator {
    public fun newSessionId(): SessionId
}
