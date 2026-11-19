package dev.notesmaker.core.domain.model

/** A downloadable speech model from the bundled catalog (API design Section 10), joined with its install state. */
public data class SpeechModel(
    val id: ModelId,
    val engine: EngineId,
    /** Null means any language. */
    val languages: Set<LanguageTag>?,
    val quality: QualityTier,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val minRamMb: Int,
    val installed: Boolean,
)

public sealed interface InstallState {
    public data object NotInstalled : InstallState

    public data class Queued(val waitingForUnmetered: Boolean) : InstallState

    public data class Downloading(val bytes: Long, val total: Long) : InstallState

    public data object Verifying : InstallState

    public data object Installed : InstallState

    public data class Failed(val reason: InstallFailure) : InstallState
}

public enum class InstallFailure { Network, NotFound, ChecksumMismatch, StorageFull }
