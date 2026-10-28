package dev.daybreak.domain

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The only snooze/dismiss gate; persist desired state before applying OS effects. */
class CompleteAlarmChallenge(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock? = null,
) {
    suspend fun execute(alarm: Alarm, session: ChallengeSession, action: AlarmAction, answer: String) {
        if (!session.verify(answer)) throw AlarmFailure(AlarmFailureReason.WRONG_ANSWER)
        val now = clock ?: Clock.systemDefaultZone()
        val next = if (action == AlarmAction.DISMISS) alarm.repeat?.nextAfter(
            Instant.ofEpochMilli(maxOf(now.millis(), alarm.occurrenceAtMillis)), now.zone) else null
        val changed = when (action) {
            AlarmAction.SNOOZE -> alarm.copy(triggerAtMillis = now.millis() + 5 * 60_000,
                enabled = true, phase = AlarmPhase.SNOOZED)
            AlarmAction.DISMISS -> if (next == null) alarm.copy(enabled = false, phase = AlarmPhase.COMPLETED)
                else alarm.copy(triggerAtMillis = next, occurrenceAtMillis = next, enabled = true, phase = AlarmPhase.SCHEDULED)
        }
        if (!repository.replace(alarm, changed)) throw AlarmFailure(AlarmFailureReason.STALE_ALARM)
        try {
            if (changed.enabled) scheduler.schedule(changed) else scheduler.cancel(alarm.id)
        } catch (error: Exception) {
            // Room writes enqueue desired work atomically. Even a failed restoration remains replayable.
            withContext(NonCancellable) {
                if (!repository.replace(changed, alarm)) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    throw AlarmFailure(AlarmFailureReason.EDIT_RESTORE_FAILED).apply { addSuppressed(error) }
                }
            }
            throw error
        }
    }
}
