package dev.daybreak.domain

import java.time.Clock

/** The one exit gate for both actions; Android playback stops only after this succeeds. */
class CompleteAlarmChallenge(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun execute(alarm: Alarm, session: ChallengeSession, action: AlarmAction, answer: String) {
        if (!session.verify(answer)) throw AlarmFailure(AlarmFailureReason.WRONG_ANSWER)
        val changed = when (action) {
            AlarmAction.SNOOZE -> alarm.copy(triggerAtMillis = clock.millis() + 5 * 60_000, enabled = true)
            AlarmAction.DISMISS -> alarm.copy(enabled = false)
        }
        if (action == AlarmAction.SNOOZE) scheduler.schedule(changed)
        repository.save(changed)
        if (action == AlarmAction.DISMISS) scheduler.cancel(alarm.id)
    }
}
