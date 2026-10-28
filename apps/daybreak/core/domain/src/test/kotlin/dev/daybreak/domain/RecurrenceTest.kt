package dev.daybreak.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurrenceTest {
    private fun instant(value: String) = Instant.parse(value)
    private val utc = ZoneOffset.UTC
    private val wed = setOf(DayOfWeek.WEDNESDAY)
    private fun rule(time: String = "07:00", days: Set<DayOfWeek> = wed) = RepeatRule(LocalTime.parse(time), days)

    @Test fun weeklyEqualityIsStrictlyNextWeek() {
        assertEquals(instant("2026-11-04T07:00:00Z").toEpochMilli(), rule().nextAfter(instant("2026-10-28T07:00:00Z"), utc))
    }
    @Test fun multipleWeekdaysSelectTheNearest() {
        assertEquals(instant("2026-10-29T07:00:00Z").toEpochMilli(),
            rule(days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)).nextAfter(instant("2026-10-28T08:00:00Z"), utc))
    }
    @Test fun gapShiftsForwardByTheGap() {
        assertEquals(instant("2026-03-08T07:30:00Z").toEpochMilli(), rule("02:30", setOf(DayOfWeek.SUNDAY))
            .nextAfter(instant("2026-03-08T06:00:00Z"), ZoneId.of("America/New_York")))
    }
    @Test fun overlapUsesEarlierOffsetAndDoesNotRingTwice() {
        val sunday = rule("01:30", setOf(DayOfWeek.SUNDAY))
        val zone = ZoneId.of("America/New_York")
        assertEquals(instant("2026-11-01T05:30:00Z").toEpochMilli(), sunday.nextAfter(instant("2026-11-01T04:00:00Z"), zone))
        assertEquals(instant("2026-11-08T06:30:00Z").toEpochMilli(), sunday.nextAfter(instant("2026-11-01T05:45:00Z"), zone))
    }
    @Test fun travelFollowsDeviceLocalTime() {
        val now = instant("2026-10-28T00:00:00Z")
        assertEquals(instant("2026-10-28T01:30:00Z").toEpochMilli(), rule().nextAfter(now, ZoneId.of("Asia/Kolkata")))
        assertEquals(instant("2026-10-28T11:00:00Z").toEpochMilli(), rule().nextAfter(now, ZoneId.of("America/New_York")))
    }
    @Test fun midnightCrossesWeekAndYearBoundaries() {
        assertEquals(instant("2027-01-01T00:00:00Z").toEpochMilli(), rule("00:00", setOf(DayOfWeek.FRIDAY))
            .nextAfter(instant("2026-12-31T23:59:59Z"), utc))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyWeeklyRuleIsRejected() { rule(days = emptySet()) }

    @Test fun creatingAWeeklyAlarmKeepsOneIdAndRule() = runBlocking {
        val repo = JournalRepository(); val scheduler = Scheduler()
        CreateAlarm(repo, scheduler, ChallengeRegistry(), Clock.fixed(instant("2026-10-28T08:00:00Z"), utc))
            .execute("Weekday", "07:00", "sum.v1", "", "", wed)
        val created = repo.rows.values.single()
        assertEquals(rule(), created.repeat)
        assertEquals(instant("2026-11-04T07:00:00Z").toEpochMilli(), created.triggerAtMillis)
        assertEquals(created.triggerAtMillis, created.occurrenceAtMillis)
        assertEquals(created, scheduler.registrations[created.id])
    }

    @Test fun snoozeAcrossNextRegularSlotCoalescesItAndDismissRearmsTomorrow() = runBlocking {
        val base = instant("2026-10-28T23:59:00Z").toEpochMilli()
        val alarm = Alarm(id = "repeat", label = "Wake", triggerAtMillis = base, challengeId = "question.v1",
            question = "Ready?", answerDigest = answerDigest("yes", "repeat"), phase = AlarmPhase.RINGING,
            repeat = rule("00:00", DayOfWeek.entries.toSet()), occurrenceAtMillis = base)
        val repo = JournalRepository().apply { save(alarm) }; val scheduler = Scheduler()
        val session = ChallengeSession(QuestionChallenge(), QuestionChallenge().create(alarm))
        CompleteAlarmChallenge(repo, scheduler, Clock.fixed(instant("2026-10-28T23:59:00Z"), utc))
            .execute(alarm, session, AlarmAction.SNOOZE, "yes")
        val snoozed = repo.get(alarm.id)!!
        assertEquals(instant("2026-10-29T00:04:00Z").toEpochMilli(), snoozed.triggerAtMillis)
        assertEquals(base, snoozed.occurrenceAtMillis)
        assertEquals(alarm.repeat, snoozed.repeat)
        CompleteAlarmChallenge(repo, scheduler, Clock.fixed(instant("2026-10-29T00:05:00Z"), utc))
            .execute(snoozed, session, AlarmAction.DISMISS, "yes")
        val next = repo.get(alarm.id)!!
        assertTrue(next.enabled)
        assertEquals(instant("2026-10-30T00:00:00Z").toEpochMilli(), next.triggerAtMillis)
        assertEquals(AlarmPhase.SCHEDULED, next.phase)
    }

    @Test fun staleCompletionDoesNotOverwriteEditedAlarm() = runBlocking {
        val alarm = alarm(); val repo = JournalRepository().apply { save(alarm.copy(label = "New")) }
        try { CompleteAlarmChallenge(repo, Scheduler()).execute(alarm,
            ChallengeSession(QuestionChallenge(), QuestionChallenge().create(alarm)), AlarmAction.DISMISS, "yes"); fail("Must reject") }
        catch (error: AlarmFailure) { assertEquals(AlarmFailureReason.STALE_ALARM, error.reason) }
        assertEquals("New", repo.get(alarm.id)?.label)
    }

    @Test fun replayAfterApplyBeforeAckCrashIsIdempotent() = runBlocking {
        val repo = JournalRepository().apply { save(alarm()) }; val scheduler = Scheduler().apply { failAfterApply = true }
        try { ReconcileSchedules(repo, scheduler).drain(); fail("Must fail") } catch (_: IllegalStateException) { }
        assertEquals(1, repo.pendingSchedules().size)
        scheduler.failAfterApply = false
        ReconcileSchedules(repo, scheduler).drain()
        assertTrue(repo.pendingSchedules().isEmpty())
        assertEquals(1, scheduler.registrations.size)
        assertEquals(2, scheduler.calls)
    }

    @Test fun conditionalAckKeepsANewerDesiredSchedule() = runBlocking {
        val old = alarm(); val changed = old.copy(triggerAtMillis = old.triggerAtMillis + 60_000)
        val repo = JournalRepository().apply { save(old) }; val scheduler = Scheduler()
        scheduler.onSchedule = { scheduler.onSchedule = null; repo.save(changed) }
        ReconcileSchedules(repo, scheduler).drain()
        assertEquals(changed, scheduler.registrations[old.id])
        assertTrue(repo.pendingSchedules().isEmpty())
        assertEquals(2, scheduler.calls)
    }

    @Test fun deletedRowsLeaveReplayableCancellationTombstones() = runBlocking {
        val alarm = alarm(); val repo = JournalRepository().apply { save(alarm) }; val scheduler = Scheduler()
        ReconcileSchedules(repo, scheduler).drain()
        repo.delete(alarm.id)
        assertNull(repo.pendingSchedules().single().alarm)
        ReconcileSchedules(repo, scheduler).drain()
        assertFalse(scheduler.registrations.containsKey(alarm.id))
        assertTrue(repo.pendingSchedules().isEmpty())
    }

    @Test fun permissionFailureDoesNotBlockOtherCancellationWork() = runBlocking {
        val repo = JournalRepository().apply { save(alarm()); delete("other") }
        val scheduler = Scheduler().apply { denied = true }
        try { ReconcileSchedules(repo, scheduler).drain(); fail("Must fail") } catch (_: SecurityException) { }
        assertTrue("other" in scheduler.canceled)
        assertEquals(listOf("a"), repo.pendingSchedules().map { it.alarmId })
        scheduler.denied = false
        ReconcileSchedules(repo, scheduler).drain()
        assertTrue(repo.pendingSchedules().isEmpty())
    }

    @Test fun rebootSkipsMissedOnceAndAdvancesRepeatWithoutReplayingBacklog() = runBlocking {
        val old = alarm().copy(triggerAtMillis = 1)
        val repeated = old.copy(id = "weekly", repeat = rule(), phase = AlarmPhase.RINGING)
        val repo = JournalRepository().apply { save(old); save(repeated) }; val scheduler = Scheduler()
        ReconcileSchedules(repo, scheduler, Clock.fixed(instant("2026-10-28T08:00:00Z"), utc))
            .refresh(skipRinging = true, preserveDueGrace = false)
        assertFalse(repo.get(old.id)!!.enabled)
        assertEquals(instant("2026-11-04T07:00:00Z").toEpochMilli(), repo.get("weekly")?.triggerAtMillis)
        assertEquals(setOf("weekly"), scheduler.registrations.keys)
    }

    @Test fun processRestoreRetainsRingingAndCancelsItsOsRegistration() = runBlocking {
        val alarm = alarm().copy(phase = AlarmPhase.RINGING)
        val repo = JournalRepository().apply { save(alarm) }; val scheduler = Scheduler()
        ReconcileSchedules(repo, scheduler, Clock.fixed(instant("2026-10-28T08:00:00Z"), utc)).refresh()
        assertEquals(alarm, repo.get(alarm.id))
        assertTrue(alarm.id in scheduler.canceled)
    }

    @Test fun timezoneRefreshRebasesOnlyScheduledWeeklyRules() = runBlocking {
        val now = instant("2026-10-28T00:00:00Z")
        val weekly = alarm().copy(repeat = rule(), triggerAtMillis = now.plusSeconds(7 * 3600).toEpochMilli())
        val snoozed = weekly.copy(id = "snoozed", phase = AlarmPhase.SNOOZED)
        val once = weekly.copy(id = "once", repeat = null)
        val repo = JournalRepository().apply { save(weekly); save(snoozed); save(once) }
        ReconcileSchedules(repo, Scheduler(), Clock.fixed(now, ZoneId.of("Asia/Kolkata"))).refresh(preserveDueGrace = false)
        assertEquals(instant("2026-10-28T01:30:00Z").toEpochMilli(), repo.get("a")?.triggerAtMillis)
        assertEquals(snoozed, repo.get("snoozed"))
        assertEquals(once, repo.get("once"))
    }

    @Test fun recentDueDeliveryGetsGraceExceptOnBoot() = runBlocking {
        val now = instant("2026-10-28T08:00:00Z")
        val due = alarm().copy(triggerAtMillis = now.toEpochMilli() - 1_000)
        val repo = JournalRepository().apply { save(due) }
        val reconciler = ReconcileSchedules(repo, Scheduler(), Clock.fixed(now, utc))
        reconciler.refresh(); assertEquals(due, repo.get(due.id))
        reconciler.refresh(skipRinging = true, preserveDueGrace = false); assertFalse(repo.get(due.id)!!.enabled)
    }

    private fun alarm() = Alarm(id = "a", label = "Wake", triggerAtMillis = instant("2026-10-29T07:00:00Z").toEpochMilli(),
        challengeId = "question.v1", question = "Ready?", answerDigest = answerDigest("yes", "a"))

    private class JournalRepository : AlarmRepository {
        val rows = linkedMapOf<String, Alarm>()
        private val commands = linkedMapOf<String, String>()
        private var version = 0
        override fun observe() = flowOf(rows.values.toList())
        override suspend fun get(id: String) = rows[id]
        override suspend fun save(alarm: Alarm) { rows[alarm.id] = alarm; commands[alarm.id] = (++version).toString() }
        override suspend fun delete(id: String) { rows.remove(id); commands[id] = (++version).toString() }
        override suspend fun pendingSchedules() = commands.map { PendingSchedule(it.key, it.value, rows[it.key]) }
        override suspend fun acknowledgeSchedule(alarmId: String, token: String) { if (commands[alarmId] == token) commands.remove(alarmId) }
    }
    private class Scheduler : AlarmScheduler {
        val registrations = linkedMapOf<String, Alarm>()
        val canceled = mutableSetOf<String>()
        var calls = 0; var denied = false; var failAfterApply = false
        var onSchedule: (suspend (Alarm) -> Unit)? = null
        override fun schedule(alarm: Alarm) {
            if (denied) throw SecurityException("Denied")
            calls++; registrations[alarm.id] = alarm
            onSchedule?.let { runBlocking { it(alarm) } }
            check(!failAfterApply) { "Simulated failure before acknowledgement" }
        }
        override fun cancel(id: String) { registrations.remove(id); canceled += id }
    }
}
