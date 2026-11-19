package dev.notesmaker.core.domain.engine

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.QualityTier
import dev.notesmaker.core.domain.model.UiLabel

/**
 * Shared contract of every engine (API design Section 5.1). **Plug-in API.**
 */
public interface Engine {
    public val info: EngineInfo

    /** Cheap, side-effect free, may be called often (for example on every Settings recomposition). */
    public suspend fun capability(request: CapabilityRequest): Capability
}

public data class EngineInfo(
    val id: EngineId,
    val kind: EngineKind,
    /** Resource-free label, resolved by `:core:ui`. */
    val displayName: UiLabel,
    val location: EngineLocation,
    /** Higher is preferred by default (ADR-15). */
    val qualityRank: Int,
    /** Null means any language. */
    val languages: Set<LanguageTag>?,
)

public enum class EngineKind { Transcription, Notes }

public enum class EngineLocation { OnDevice, Cloud }

/**
 * The engine is told whether the app is in the foreground instead of asking Android itself, so
 * engines stay testable and the foreground signal has one owner (`ForegroundState`).
 */
public data class CapabilityRequest(val language: LanguageTag, val quality: QualityTier, val inForeground: Boolean)

/** Adding a subtype is a Plug-in API change (API design Section 12). */
public sealed interface Capability {
    public data object Ready : Capability

    public data class NeedsModel(val modelId: ModelId, val sizeBytes: Long) : Capability

    public data object NeedsForeground : Capability

    public data object NeedsOptIn : Capability

    /** A system-managed model, for example Gemini Nano, that the system can download. */
    public data class NeedsDownload(val description: String) : Capability

    public data class Unsupported(val reason: String) : Capability
}

/** Receives progress in 0..1. */
public fun interface ProgressSink {
    public suspend fun report(fraction: Float)

    public companion object {
        public val None: ProgressSink = ProgressSink { }
    }
}
