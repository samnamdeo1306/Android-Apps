package dev.notesmaker.core.domain.contract

import app.cash.turbine.test
import dev.notesmaker.core.domain.model.NotesEdit
import dev.notesmaker.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviour every `NotesRepository` must have (ADR-16, ADR-23, FR-65, NFR-21). */
public abstract class NotesRepositoryContract : PersistenceContract() {

    private val id = TestData.sessionId(1)

    private suspend fun givenSession() {
        sessions.create(TestData.newSession(1))
    }

    @Test
    public fun replaceThenGetRoundTripsEveryField(): Unit = runTest {
        givenSession()
        val written = TestData.notes(id, sectionCount = 3, highlightCount = 5).let {
            it.copy(provenance = it.provenance.copy(modelId = TestData.MODEL, promptVersion = "notes-v1"))
        }
        notes.replace(written)
        assertEquals(written, notes.get(id))
    }

    @Test
    public fun replaceIsIdempotentAndLeavesOnlyTheNewNotes(): Unit = runTest {
        givenSession()
        notes.replace(TestData.notes(id, sectionCount = 5, highlightCount = 4))
        val second = TestData.notes(id, sectionCount = 2, highlightCount = 1, title = "Second run")
        notes.replace(second)
        notes.replace(second)
        assertEquals(second, notes.get(id))
    }

    @Test
    public fun replaceForAnUnknownSessionFails(): Unit = runTest {
        assertFails { notes.replace(TestData.notes(id)) }
    }

    @Test
    public fun getUnknownIsNull(): Unit = runTest {
        givenSession()
        assertNull(notes.get(id))
    }

    @Test
    public fun editingTheSummaryFlagsIt(): Unit = runTest {
        givenSession()
        notes.replace(TestData.notes(id))
        notes.applyEdit(id, NotesEdit.Summary(listOf("My own summary.")))

        val n = assertNotNull(notes.get(id))
        assertEquals(listOf("My own summary."), n.summary)
        assertTrue(n.edits.summaryEdited)
    }

    @Test
    public fun editingASectionFlagsOnlyThatSection(): Unit = runTest {
        givenSession()
        notes.replace(TestData.notes(id, sectionCount = 3))
        notes.applyEdit(id, NotesEdit.Section(1, "Light reactions", listOf("ATP", "NADPH")))

        val sections = assertNotNull(notes.get(id)).sections
        assertEquals("Light reactions", sections[1].heading)
        assertEquals(listOf("ATP", "NADPH"), sections[1].points)
        assertEquals(listOf(false, true, false), sections.map { it.edited })
        assertEquals(60_000, sections[1].startMs, "an edit keeps the section's times")
    }

    @Test
    public fun editingAndRemovingHighlights(): Unit = runTest {
        givenSession()
        notes.replace(TestData.notes(id, highlightCount = 4))
        notes.applyEdit(id, NotesEdit.HighlightText(2, "Exam on Friday"))
        notes.applyEdit(id, NotesEdit.RemoveHighlight(0))

        val highlights = assertNotNull(notes.get(id)).highlights
        assertEquals(listOf("Highlight 2", "Exam on Friday", "Highlight 4"), highlights.map { it.text })
        assertEquals(listOf(false, true, false), highlights.map { it.edited })
    }

    @Test
    public fun outOfRangeEditsAreIgnored(): Unit = runTest {
        givenSession()
        val original = TestData.notes(id, sectionCount = 1, highlightCount = 1)
        notes.replace(original)
        notes.applyEdit(id, NotesEdit.Section(7, "x", emptyList()))
        notes.applyEdit(id, NotesEdit.HighlightText(7, "x"))
        notes.applyEdit(id, NotesEdit.RemoveHighlight(7))
        assertEquals(original, notes.get(id))
    }

    @Test
    public fun editsOfMissingNotesDoNothing(): Unit = runTest {
        givenSession()
        notes.applyEdit(id, NotesEdit.Summary(listOf("x")))
        assertNull(notes.get(id))
    }

    @Test
    public fun observeEmitsOnReplaceAndEdit(): Unit = runTest {
        givenSession()
        notes.observe(id).test {
            assertNull(awaitItem())
            notes.replace(TestData.notes(id))
            assertFalse(assertNotNull(awaitItem()).edits.summaryEdited)
            notes.applyEdit(id, NotesEdit.Summary(listOf("Edited")))
            assertTrue(assertNotNull(awaitItem()).edits.summaryEdited)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun deletingTheSessionDeletesItsNotes(): Unit = runTest {
        givenSession()
        notes.replace(TestData.notes(id))
        sessions.delete(id)
        assertNull(notes.get(id))
    }
}
