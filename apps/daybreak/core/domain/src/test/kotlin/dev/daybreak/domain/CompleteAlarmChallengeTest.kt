package dev.daybreak.domain

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CompleteAlarmChallengeTest {
    private val alarm = Alarm(id = "a", label = "Wake", triggerAtMillis = 1,
        challengeId = "question.v1", question = "Answer?", answerDigest = answerDigest("yes", "a"))
    private val question = QuestionChallenge()
    private val session = ChallengeSession(question, question.create(alarm))
    private class Repository : AlarmRepository {
        var saved: Alarm? = null
        override fun observe() = flowOf(listOfNotNull(saved))
        override suspend fun get(id: String) = saved
        override suspend fun save(alarm: Alarm) { saved = alarm }
        override suspend fun delete(id: String) { saved = null }
    }
    private class Scheduler : AlarmScheduler {
        var scheduled: Alarm? = null
        var canceled: String? = null
        var denied = false
        override fun schedule(alarm: Alarm) {
            if (denied) throw SecurityException("Exact alarms denied")
            scheduled = alarm
        }
        override fun cancel(id: String) { canceled = id }
    }
    private val repository = Repository()
    private val scheduler = Scheduler()
    private val useCase = CompleteAlarmChallenge(repository, scheduler,
        Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC))

    @Test fun wrongAnswerCannotSnoozeOrDismiss() = runBlocking {
        AlarmAction.entries.forEach { action ->
            try { useCase.execute(alarm, session, action, "no"); fail("Must reject wrong answer") }
            catch (_: IllegalArgumentException) { /* expected */ }
        }
        assertNull(repository.saved); assertNull(scheduler.scheduled); assertNull(scheduler.canceled)
    }
    @Test fun correctAnswerDismissesAndCancelsPendingDelivery() = runBlocking {
        useCase.execute(alarm, session, AlarmAction.DISMISS, "yes")
        assertEquals(false, repository.saved?.enabled)
        assertEquals("a", scheduler.canceled)
        assertNull(scheduler.scheduled)
    }
    @Test fun correctAnswerSnoozesExactlyFiveMinutesFromNow() = runBlocking {
        useCase.execute(alarm, session, AlarmAction.SNOOZE, "YES")
        assertEquals(1_300_000L, repository.saved?.triggerAtMillis)
        assertEquals(repository.saved, scheduler.scheduled)
        assertEquals(true, repository.saved?.enabled)
        assertNull(scheduler.canceled)
    }
    @Test fun revokedPermissionDoesNotCommitSnooze() = runBlocking {
        scheduler.denied = true
        try { useCase.execute(alarm, session, AlarmAction.SNOOZE, "yes"); fail("Must fail") }
        catch (_: SecurityException) { /* caller must keep sound playing */ }
        assertNull(repository.saved)
    }
}
