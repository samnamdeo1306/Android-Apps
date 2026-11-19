package dev.notesmaker.core.domain.contract

import dev.notesmaker.core.domain.export.ExportOptions
import dev.notesmaker.core.domain.export.NotesExporter
import dev.notesmaker.core.domain.model.ProcessingState
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.SessionOrigin
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.model.TranscriptSegment
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of `NotesExporter` (FR-67, NFR-62). */
public abstract class NotesExporterContract {

    protected abstract fun subject(): NotesExporter

    /** Text exporters can be searched for transcript text; binary exporters (PDF) override this. */
    protected open val isText: Boolean = true

    private val marker = "TRANSCRIPT-ONLY-MARKER"
    private val session = Session(
        id = TestData.sessionId(1), title = "Photosynthesis", titleSource = TitleSource.Generated, createdAtEpochMs = TestData.T0,
        origin = SessionOrigin.Recorded, language = TestData.ENGLISH, durationMs = 180_000, audio = null,
        status = SessionStatus.Ready, processing = ProcessingState(), notesUpgradeAvailable = false,
    )
    private val notes = TestData.notes()
    private val transcript = TestData.transcript().let {
        it.copy(segments = it.segments + TranscriptSegment(it.segments.size, 170_000, 175_000, marker))
    }

    private suspend fun export(options: ExportOptions): ByteArray =
        ByteArrayOutputStream().also { subject().export(session, notes, transcript, options, it) }.toByteArray()

    @Test
    public fun outputIsNotEmpty(): Unit = runTest {
        assertTrue(export(ExportOptions()).isNotEmpty())
    }

    @Test
    public fun mimeTypeAndExtensionAreWellFormed() {
        val exporter = subject()
        assertTrue(Regex("^[a-z]+/[a-z0-9.+-]+$").matches(exporter.mimeType), "bad MIME type ${exporter.mimeType}")
        assertTrue(exporter.fileExtension.isNotBlank() && !exporter.fileExtension.startsWith("."), "bad extension ${exporter.fileExtension}")
        assertTrue(exporter.id.value.isNotBlank())
    }

    @Test
    public fun transcriptIsLeftOutUnlessAskedFor(): Unit = runTest {
        if (!isText) return@runTest
        assertFalse(marker in export(ExportOptions(includeTranscript = false)).decodeToString())
        assertTrue(marker in export(ExportOptions(includeTranscript = true)).decodeToString())
    }
}
