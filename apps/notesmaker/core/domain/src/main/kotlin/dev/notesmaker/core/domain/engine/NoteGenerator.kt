package dev.notesmaker.core.domain.engine

import dev.notesmaker.core.domain.error.LanguageModelFailure
import dev.notesmaker.core.domain.error.Outcome
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.DraftNotes
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.Transcript

/** Builds notes from a finished transcript (ADR-04, ADR-16). **Plug-in API.** */
public interface NoteGenerator : Engine {
    /**
     * Output is validated by the pipeline before it is saved, so a generator may return slightly
     * out-of-range times; it must not return an empty title or zero sections for a non-empty
     * transcript. Checked by `NoteGeneratorContract`.
     */
    public suspend fun generate(input: NotesInput, progress: ProgressSink): Outcome<DraftNotes, ProcessingFailure>
}

public data class NotesInput(
    val transcript: Transcript,
    val durationMs: Long,
    val language: LanguageTag,
    val sessionCreatedAtEpochMs: Long,
)

/**
 * A text model that `LlmNoteGenerator` (domain) drives with chunked prompts (ADR-16, ADR-27).
 * **Plug-in API**, checked by `LanguageModelContract`.
 */
public interface LanguageModel : Engine {
    /** Input budget in tokens for one request, prompt included. */
    public val inputTokenBudget: Int

    /** Rough token count for budgeting. Adapters with a real tokenizer override it. */
    public fun estimateTokens(text: String): Int = (text.length + 3) / 4

    public suspend fun complete(request: CompletionRequest): Outcome<String, LanguageModelFailure>
}

public data class CompletionRequest(
    val system: String,
    val prompt: String,
    /** Used when the backend supports structured output. */
    val responseSchema: JsonSchema?,
    val maxOutputTokens: Int,
    val temperature: Float = 0.2f,
)

/** A JSON Schema document, kept as text so the domain needs no JSON library in its API. */
@JvmInline
public value class JsonSchema(public val json: String)
