package dev.notesmaker.core.domain.model

/**
 * Structured notes for a session (ADR-16). Shape rules (title length, 3..8 summary items, ordered
 * sections, at most 10 highlights) are enforced by one validator that every generator's output
 * passes through, not by these classes; they only check what can never be wrong.
 */
public data class Notes(
    val sessionId: SessionId,
    val title: String,
    val summary: List<String>,
    val sections: List<NoteSection>,
    val highlights: List<Highlight>,
    val provenance: NotesProvenance,
    val edits: EditFlags = EditFlags(),
)

public data class NoteSection(
    val heading: String,
    val startMs: Long,
    val endMs: Long,
    val points: List<String>,
    val edited: Boolean = false,
) {
    init {
        require(startMs in 0..endMs) { "section times must satisfy 0 <= startMs <= endMs, were $startMs..$endMs" }
    }
}

public data class Highlight(val kind: HighlightKind, val text: String, val atMs: Long, val edited: Boolean = false) {
    init {
        require(atMs >= 0) { "atMs must be >= 0" }
    }
}

/** Persisted by name; never rename one. */
public enum class HighlightKind { KeyPoint, Definition, Formula, Date, Instruction }

public data class NotesProvenance(
    /** Shown as "Notes by ..." (FR-44). */
    val generatorId: EngineId,
    val modelId: ModelId?,
    /** LLM generators only (ADR-27). */
    val promptVersion: String?,
    val transcriptRevision: Int,
    val generatedAtEpochMs: Long,
)

public data class EditFlags(val summaryEdited: Boolean = false)

/** A user edit to generated notes (FR-65). The edited item is flagged so regeneration can keep it. */
public sealed interface NotesEdit {
    public data class Summary(val summary: List<String>) : NotesEdit

    public data class Section(val index: Int, val heading: String, val points: List<String>) : NotesEdit

    public data class HighlightText(val index: Int, val text: String) : NotesEdit

    public data class RemoveHighlight(val index: Int) : NotesEdit
}

/** Notes before provenance, edit flags and validation are attached by the pipeline. */
public data class DraftNotes(
    val title: String,
    val summary: List<String>,
    val sections: List<DraftSection>,
    val highlights: List<DraftHighlight>,
    val modelId: ModelId? = null,
    val promptVersion: String? = null,
)

/** Times may be slightly out of range; the validator clamps them. [endMs] null means "until the next section". */
public data class DraftSection(val heading: String, val startMs: Long, val endMs: Long?, val points: List<String>)

public data class DraftHighlight(val kind: HighlightKind, val text: String, val atMs: Long)
