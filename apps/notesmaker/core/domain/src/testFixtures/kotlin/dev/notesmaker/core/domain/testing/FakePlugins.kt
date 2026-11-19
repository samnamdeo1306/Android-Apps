package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.export.ExportOptions
import dev.notesmaker.core.domain.export.NotesExporter
import dev.notesmaker.core.domain.model.ExporterId
import dev.notesmaker.core.domain.model.Notes
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionId
import dev.notesmaker.core.domain.model.StageId
import dev.notesmaker.core.domain.model.Transcript
import dev.notesmaker.core.domain.model.UiLabel
import dev.notesmaker.core.domain.pipeline.PipelineStage
import dev.notesmaker.core.domain.pipeline.StageContext
import dev.notesmaker.core.domain.pipeline.StageOutcome
import kotlinx.coroutines.yield
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/** Plain-text exporter. Passes `NotesExporterContract`. */
public class FakeNotesExporter : NotesExporter {
    override val id: ExporterId = ExporterId("fake-text")
    override val label: UiLabel = UiLabel("exporter.fake-text", "Plain text (fake)")
    override val mimeType: String = "text/plain"
    override val fileExtension: String = "txt"

    override suspend fun export(session: Session, notes: Notes, transcript: Transcript?, options: ExportOptions, out: OutputStream) {
        val text = buildString {
            appendLine(session.title)
            notes.summary.forEach { appendLine("- $it") }
            notes.sections.forEach { section ->
                appendLine()
                appendLine(if (options.includeTimestamps) "[${section.startMs / 1000}s] ${section.heading}" else section.heading)
                section.points.forEach { appendLine("  - $it") }
            }
            if (options.includeTranscript && transcript != null) {
                appendLine()
                transcript.segments.forEach { appendLine(it.text) }
            }
        }
        out.write(text.toByteArray(Charsets.UTF_8))
    }
}

/**
 * A stage whose "output" is an entry in [outputs], written with replace semantics so it is
 * idempotent. It reports progress (and yields) before writing, so a cancellation at that point
 * leaves no output. Passes `PipelineStageContract`.
 */
public class FakePipelineStage(
    override val id: StageId = StageId("fake-stage"),
    public val outputs: MutableMap<SessionId, String> = ConcurrentHashMap(),
) : PipelineStage {
    override suspend fun isComplete(ctx: StageContext): Boolean = ctx.session.id in outputs

    override suspend fun run(ctx: StageContext): StageOutcome {
        ctx.progress.report(0.5f)
        yield()
        outputs[ctx.session.id] = "output of ${id.value}"
        ctx.progress.report(1f)
        return StageOutcome.Completed
    }
}
