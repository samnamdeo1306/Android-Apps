package dev.notesmaker.core.data.db

import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.AudioRef
import dev.notesmaker.core.domain.model.HighlightKind
import dev.notesmaker.core.domain.model.SessionOrigin
import dev.notesmaker.core.domain.model.SessionStatus
import dev.notesmaker.core.domain.model.TitleSource
import dev.notesmaker.core.domain.pipeline.PipelineTrigger
import dev.notesmaker.core.domain.testing.TestData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MappersTest {

    @Test
    fun `stored names are stable`() {
        // These strings live in users' databases; changing one needs a migration.
        assertEquals(listOf("Recording", "Waiting", "Processing", "Ready", "Failed", "Cancelled"), SessionStatus.entries.map(StoredNames::status))
        assertEquals(listOf("fallback", "generated", "user"), TitleSource.entries.map(StoredNames::titleSource))
        assertEquals(listOf("KeyPoint", "Definition", "Formula", "Date", "Instruction"), HighlightKind.entries.map(StoredNames::highlightKind))
    }

    @Test
    fun `a new session maps to an entity and back`() {
        val draft = TestData.newSession(3, origin = SessionOrigin.Imported("Week 3.mp3"), durationMs = 90_000)
        val session = draft.toEntity(now = 42).toDomain()

        assertEquals(draft.id, session.id)
        assertEquals(draft.title, session.title)
        assertEquals(draft.origin, session.origin)
        assertEquals(draft.durationMs, session.durationMs)
        assertEquals(SessionStatus.Waiting, session.status)
        assertEquals(draft.queuedAtEpochMs, session.processing.queuedAtEpochMs)
        assertEquals(PipelineTrigger.Initial, session.processing.trigger)
        assertNull(session.processing.failure)
    }

    @Test
    fun `recorded origin keeps no display name`() {
        val entity = TestData.newSession(1, status = SessionStatus.Recording, origin = SessionOrigin.Recorded).toEntity(0)
        assertEquals("recorded", entity.origin)
        assertNull(entity.originName)
        assertEquals(SessionOrigin.Recorded, entity.toDomain().origin)
    }

    @Test
    fun `stored failure and audio map back`() {
        val entity = TestData.newSession(1).toEntity(0).copy(
            status = "Failed",
            failureCode = "model_missing",
            failureDetail = "whisper-base.en-q5_1",
            audioPath = "sessions/x/audio.m4a",
        )
        val session = entity.toDomain()
        assertEquals(ProcessingFailure.ModelMissing(dev.notesmaker.core.domain.model.ModelId("whisper-base.en-q5_1")), session.processing.failure)
        assertEquals(AudioRef("sessions/x/audio.m4a"), session.audio)
    }

    @Test
    fun `an unknown status becomes a retryable failure instead of a crash`() {
        val session = TestData.newSession(1).toEntity(0).copy(status = "Archived").toDomain()
        assertEquals(SessionStatus.Failed, session.status)
        assertIs<ProcessingFailure.Unknown>(session.processing.failure)
    }

    @Test
    fun `unknown title source and highlight kind fall back safely`() {
        assertEquals(TitleSource.Generated, StoredNames.titleSource("ai"))
        assertEquals(HighlightKind.KeyPoint, StoredNames.highlightKind("Quote"))
    }

    @Test
    fun `out of range progress is clamped`() {
        assertEquals(1f, TestData.newSession(1).toEntity(0).copy(progress = 1.7f).toDomain().processing.progress)
    }

    @Test
    fun `notes map to entities with gapless indexes and back`() {
        val notes = TestData.notes(sectionCount = 3, highlightCount = 4)
        val (entity, sections, highlights) = notes.toEntities()

        assertEquals(listOf(0, 1, 2), sections.map { it.idx })
        assertEquals(listOf(0, 1, 2, 3), highlights.map { it.idx })
        // Room may return relation rows in any order; the mapper sorts by idx.
        val back = NotesWithChildren(entity, sections.reversed(), highlights.shuffled(java.util.Random(1))).toDomain()
        assertEquals(notes, back)
    }

    @Test
    fun `transcript segments are ordered by index`() {
        val id = TestData.sessionId(1)
        val segments = TestData.segments(4).map { it.toEntity(id) }
        val header = TranscriptEntity(id.value, "whisper.cpp", null, "en", 20_000, 4, complete = true, revision = 2)
        val transcript = TranscriptWithSegments(header, segments.reversed()).toDomain()

        assertEquals(TestData.segments(4), transcript.segments)
        assertEquals(2, transcript.revision)
        assertEquals(20_000, header.toCheckpoint().committedUntilMs)
    }
}
