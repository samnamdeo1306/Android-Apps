package dev.notesmaker.core.domain.model

public data class UserPreferences(
    val sortOrder: SortOrder = SortOrder.NewestFirst,
    /** Null means the device language when supported. */
    val defaultLanguage: LanguageTag? = null,
    val preferredTranscriptionEngine: EngineId? = null,
    val preferredNoteGenerator: EngineId? = null,
    val transcriptionQuality: QualityTier = QualityTier.Fast,
    val recordingQuality: RecordingQuality = RecordingQuality.Standard,
    val consentReminderDismissed: Boolean = false,
    val deferOnLowBattery: Boolean = false,
    /** Cloud engines, 0.3+ (ADR-05). */
    val optedInEngines: Set<EngineId> = emptySet(),
)

public enum class SortOrder { NewestFirst, OldestFirst, TitleAscending, TitleDescending }

public enum class QualityTier { Fast, Accurate }

/** 16 kHz / 64 kbps or 24 kHz / 128 kbps AAC (ADR-08). */
public enum class RecordingQuality { Standard, High }
