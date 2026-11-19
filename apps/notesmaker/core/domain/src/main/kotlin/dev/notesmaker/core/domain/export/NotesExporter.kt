package dev.notesmaker.core.domain.export

import dev.notesmaker.core.domain.model.ExporterId
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.UiLabel
import java.io.OutputStream

/**
 * Writes notes in one file format (FR-67, 0.2). **Plug-in API**, checked by `NotesExporterContract`.
 * Writing to an [OutputStream] lets binary (PDF) and text exporters share one interface (NFR-62).
 */
public interface NotesExporter {
    public val id: ExporterId
    public val label: UiLabel
    public val mimeType: String

    /** Without the leading dot, for example "md". */
    public val fileExtension: String

    public suspend fun export(session: Session, notes: Notes, transcript: Transcript?, options: ExportOptions, out: OutputStream)
}

public data class ExportOptions(val includeTranscript: Boolean = false, val includeTimestamps: Boolean = true)
