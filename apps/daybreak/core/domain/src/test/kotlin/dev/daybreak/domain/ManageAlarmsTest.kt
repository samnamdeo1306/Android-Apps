package dev.daybreak.domain

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ManageAlarmsTest {
    private class Repository : AlarmRepository {
        var alarm: Alarm? = null
        override fun observe() = flowOf(listOfNotNull(alarm))
        override suspend fun get(id: String) = alarm
        override suspend fun save(alarm: Alarm) { this.alarm = alarm }
        override suspend fun delete(id: String) { alarm = null }
    }
    private val repository = Repository()
    private var denied = false
    private var scheduled: Alarm? = null
    private var canceled: String? = null
    private val scheduler = object : AlarmScheduler {
        override fun schedule(alarm: Alarm) {
            check(repository.alarm == alarm)
            if (denied) throw SecurityException("Denied")
            scheduled = alarm
        }
        override fun cancel(id: String) { canceled = id }
    }
    private val create = CreateAlarm(repository, scheduler, ChallengeRegistry(),
        Clock.fixed(Instant.parse("2026-10-28T07:00:00Z"), ZoneOffset.UTC))

    @Test fun equalTimeSchedulesTomorrowAndNormalizesQuestion() = runBlocking {
        create.execute(" Wake ", "07:00", "question.v1", " Question? ", " YES ")
        val alarm = requireNotNull(repository.alarm)
        assertEquals("Wake", alarm.label)
        assertEquals("Question?", alarm.question)
        assertEquals(Instant.parse("2026-10-29T07:00:00Z").toEpochMilli(), alarm.triggerAtMillis)
        assertEquals(answerDigest("yes", alarm.id), alarm.answerDigest)
        assertEquals(alarm, scheduled)
    }

    @Test fun futureTimeSchedulesToday() = runBlocking {
        create.execute("Wake", "07:01", "sum.v1", "", "")
        assertEquals(Instant.parse("2026-10-28T07:01:00Z").toEpochMilli(), scheduled?.triggerAtMillis)
    }

    @Test fun invalidInputHasNoSideEffects() = runBlocking {
        val inputs = listOf(
            listOf("", "07:30", "sum.v1", "", ""),
            listOf("Wake", "bad", "sum.v1", "", ""),
            listOf("Wake", "07:30", "unknown", "", ""),
            listOf("Wake", "07:30", "question.v1", "Question?", ""))
        inputs.forEach { (label, time, type, question, answer) ->
            try { create.execute(label, time, type, question, answer); fail("Must reject") }
            catch (_: IllegalArgumentException) { }
        }
        assertNull(repository.alarm)
        assertNull(scheduled)
    }

    @Test fun schedulingDenialRemovesSavedAlarm() = runBlocking {
        denied = true
        try { create.execute("Wake", "07:30", "sum.v1", "", ""); fail("Must reject") }
        catch (_: SecurityException) { }
        assertNull(repository.alarm)
    }

    @Test fun activeAlarmCannotBeDeleted() = runBlocking {
        create.execute("Wake", "07:30", "sum.v1", "", "")
        val alarm = requireNotNull(repository.alarm)
        try { DeleteAlarm(repository, scheduler).execute(alarm, alarm.id); fail("Must reject") }
        catch (_: AlarmFailure) { }
        assertEquals(alarm, repository.alarm)
        assertNull(canceled)
        DeleteAlarm(repository, scheduler).execute(alarm, null)
        assertEquals(alarm.id, canceled)
        assertNull(repository.alarm)
    }
}
