package dev.daybreak.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateAlarmTest {
    private val now = Instant.parse("2026-10-28T07:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val original = Alarm(id = "stable", label = "Wake", triggerAtMillis = now.plusSeconds(3600).toEpochMilli(),
        challengeId = "question.v1", question = "Ready?", answerDigest = answerDigest("yes", "stable"))
    private var row: Alarm? = original
    private var active: String? = null
    private var saves = 0
    private var saveFailure = false
    private var rollbackFailure = false
    private var cancelAfterSave = false
    private var activateAfterSave = false
    private var scheduleFailures = 0
    private val registrations = mutableListOf<Alarm>()
    private var cancels = 0
    private val repository = object : AlarmRepository {
        override fun observe() = flowOf(listOfNotNull(row))
        override suspend fun get(id: String) = row
        override suspend fun save(alarm: Alarm) {
            if (saveFailure || (rollbackFailure && alarm == original)) error("Storage failure")
            row = alarm; saves++
            if (alarm != original) {
                if (activateAfterSave) active = original.id
                if (cancelAfterSave) throw CancellationException("Canceled after write")
            }
        }
        override suspend fun delete(id: String) { row = null }
    }
    private val scheduler = object : AlarmScheduler {
        override fun schedule(alarm: Alarm) {
            if (scheduleFailures > 0) { scheduleFailures--; throw AlarmFailure(AlarmFailureReason.EXACT_ACCESS_DENIED) }
            registrations += alarm
        }
        override fun cancel(id: String) { cancels++ }
    }
    private val update = UpdateAlarm(repository, scheduler, ChallengeRegistry(), { active }, clock)
    private suspend fun edit(base: Alarm = original, time: String = "08:00", question: String = "Ready?",
        answer: String = "", type: String = "question.v1") = update.execute(base, "Edited", time, type, question, answer)
    private suspend fun expect(reason: AlarmFailureReason, block: suspend () -> Unit) {
        try { block(); fail("Expected $reason") } catch (error: AlarmFailure) { assertEquals(reason, error.reason) }
    }

    @Test fun unchangedQuestionKeepsDigestIdAndFutureInstant() = runBlocking {
        edit()
        assertEquals(original.id, row?.id)
        assertEquals(original.answerDigest, row?.answerDigest)
        assertEquals(original.triggerAtMillis, row?.triggerAtMillis)
        assertEquals("Edited", row?.label)
        assertEquals(listOf(row), registrations)
    }
    @Test fun changedQuestionNeedsAnswerWithoutSideEffects() = runBlocking {
        expect(AlarmFailureReason.MISSING_QUESTION_ANSWER) { edit(question = "New?") }
        assertEquals(original, row); assertEquals(0, saves); assertTrue(registrations.isEmpty())
    }
    @Test fun newAnswerUsesOriginalSalt() = runBlocking {
        edit(question = "New?", answer = " NEW ")
        assertEquals(answerDigest("new", original.id), row?.answerDigest)
    }
    @Test fun switchingToPuzzleClearsPersonalConfiguration() = runBlocking {
        edit(type = "sum.v1")
        assertEquals("", row?.question); assertEquals("", row?.answerDigest)
    }
    @Test fun switchingFromPuzzleRequiresPersonalAnswer() = runBlocking {
        val puzzle = original.copy(challengeId = "sum.v1", question = "", answerDigest = "")
        row = puzzle
        expect(AlarmFailureReason.MISSING_QUESTION_ANSWER) { edit(base = puzzle) }
        assertEquals(puzzle, row); assertEquals(0, saves)
    }
    @Test fun changedPastTimeSchedulesTomorrow() = runBlocking {
        edit(time = "06:30")
        assertEquals(Instant.parse("2026-10-29T06:30:00Z").toEpochMilli(), row?.triggerAtMillis)
    }
    @Test fun completedAlarmIsExplicitlyRescheduled() = runBlocking {
        val completed = original.copy(enabled = false, triggerAtMillis = now.minusSeconds(86400).toEpochMilli())
        row = completed; edit(base = completed, time = "07:00")
        assertEquals(true, row?.enabled)
        assertEquals(Instant.parse("2026-10-29T07:00:00Z").toEpochMilli(), row?.triggerAtMillis)
    }
    @Test fun activeStaleAndMissingRecordsCannotBeEdited() = runBlocking {
        active = original.id
        expect(AlarmFailureReason.ACTIVE_ALARM) { edit() }
        active = null; row = original.copy(enabled = false)
        expect(AlarmFailureReason.STALE_ALARM) { edit() }
        row = null
        expect(AlarmFailureReason.MISSING_ALARM) { edit() }
        assertEquals(0, saves); assertTrue(registrations.isEmpty())
    }
    @Test fun schedulingFailureRestoresOldRowAndRegistration() = runBlocking {
        scheduleFailures = 1
        expect(AlarmFailureReason.EXACT_ACCESS_DENIED) { edit(time = "09:00") }
        assertEquals(original, row); assertEquals(listOf(original), registrations)
    }
    @Test fun failedRestoreIsReportedWithoutClaimingSuccess() = runBlocking {
        scheduleFailures = 2
        expect(AlarmFailureReason.EDIT_RESTORE_FAILED) { edit() }
        assertEquals(original, row); assertTrue(registrations.isEmpty())
    }
    @Test fun disabledOriginalHasNoPendingDeliveryAfterCompensation() = runBlocking {
        val completed = original.copy(enabled = false); row = completed; scheduleFailures = 1
        expect(AlarmFailureReason.EXACT_ACCESS_DENIED) { edit(base = completed) }
        assertEquals(completed, row); assertEquals(1, cancels)
    }
    @Test fun persistenceFailureDoesNotScheduleAnything() = runBlocking {
        saveFailure = true
        expect(AlarmFailureReason.EDIT_RESTORE_FAILED) { edit() }
        assertEquals(original, row); assertTrue(registrations.isEmpty())
    }
    @Test fun activationDuringPersistenceRollsBackWithoutReschedulingRingingAlarm() = runBlocking {
        activateAfterSave = true
        expect(AlarmFailureReason.ACTIVE_ALARM) { edit() }
        assertEquals(original, row); assertTrue(registrations.isEmpty())
    }
    @Test fun cancellationAfterWriteRestoresTheOldRow() = runBlocking {
        cancelAfterSave = true
        try { edit(); fail("Must cancel") } catch (_: CancellationException) { }
        assertEquals(original, row); assertTrue(registrations.isEmpty())
    }
    @Test fun rollbackPersistenceFailureReportsIncompleteRestoration() = runBlocking {
        rollbackFailure = true; scheduleFailures = 1
        expect(AlarmFailureReason.EDIT_RESTORE_FAILED) { edit() }
        assertNotEquals(original, row)
        assertEquals(listOf(original), registrations)
    }
}
