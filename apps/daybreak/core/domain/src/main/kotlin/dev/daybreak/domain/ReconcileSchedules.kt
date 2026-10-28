package dev.daybreak.domain

import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Replays durable desired state. Conditional acknowledgement cannot erase a newer command. */
class ReconcileSchedules(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock? = null,
) {
    private val mutex = Mutex()

    suspend fun refresh(skipRinging: Boolean = false, preserveDueGrace: Boolean = true) = mutex.withLock {
        val now = clock ?: Clock.systemDefaultZone()
        for (alarm in repository.observe().first()) {
            val changed = when {
                !alarm.enabled -> alarm.copy(phase = AlarmPhase.COMPLETED)
                alarm.phase == AlarmPhase.RINGING && !skipRinging -> alarm
                preserveDueGrace && !skipRinging && alarm.triggerAtMillis <= now.millis() &&
                    now.millis() - alarm.triggerAtMillis < 60_000 -> alarm
                alarm.repeat != null && (alarm.phase == AlarmPhase.SCHEDULED ||
                    alarm.triggerAtMillis <= now.millis() || alarm.phase == AlarmPhase.RINGING) -> {
                    val next = alarm.repeat.nextAfter(now.instant(), now.zone)
                    alarm.copy(triggerAtMillis = next, occurrenceAtMillis = next, phase = AlarmPhase.SCHEDULED)
                }
                alarm.triggerAtMillis <= now.millis() || alarm.phase == AlarmPhase.RINGING ->
                    alarm.copy(enabled = false, phase = AlarmPhase.COMPLETED)
                else -> alarm
            }
            // Requeue unchanged future rows too: OS registrations are lost at reboot/revocation.
            repository.replace(alarm, changed)
        }
        drainLocked()
    }

    suspend fun drain() = mutex.withLock { drainLocked() }

    private suspend fun drainLocked() {
        while (true) {
            val pending = repository.pendingSchedules()
            if (pending.isEmpty()) return
            var failure: Exception? = null
            for (command in pending) {
                try {
                    val alarm = command.alarm
                    if (alarm != null && alarm.enabled && alarm.phase != AlarmPhase.RINGING) scheduler.schedule(alarm)
                    else scheduler.cancel(command.alarmId)
                    repository.acknowledgeSchedule(command.alarmId, command.token)
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { failure = error }
            }
            if (failure != null) throw failure
        }
    }
}
