package dev.notesmaker.core.domain.model

/*
 * Identifiers are string-backed value classes (API design Section 1.4): type-safe at zero runtime
 * cost, and adding a stage, engine or model is a new value rather than an enum change in persisted
 * data. Once released, an ID value is never renamed, because it is stored in the database.
 */

/** A session's UUID string. */
@JvmInline
public value class SessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }

    override fun toString(): String = value
}

/** "whisper.cpp", "notes.basic", "llm.gemini-nano". */
@JvmInline
public value class EngineId(public val value: String) {
    override fun toString(): String = value
}

/** "whisper-base.en-q5_1". */
@JvmInline
public value class ModelId(public val value: String) {
    override fun toString(): String = value
}

/** "prepare-audio", "transcribe", "generate-notes", "finalize". */
@JvmInline
public value class StageId(public val value: String) {
    override fun toString(): String = value
}

/** "markdown", "plain-text". */
@JvmInline
public value class ExporterId(public val value: String) {
    override fun toString(): String = value
}

/** BCP 47 language tag, for example "en". */
@JvmInline
public value class LanguageTag(public val value: String) {
    override fun toString(): String = value
}

/** Path of a session's audio, relative to the audio store's root. Resolved only by `AudioStore`. */
@JvmInline
public value class AudioRef(public val relativePath: String) {
    override fun toString(): String = relativePath
}
