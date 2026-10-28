package dev.daybreak.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock

/** Updating the same ID replaces its pending delivery; rollback is best effort, not a transaction. */
class UpdateAlarm(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val challenges: ChallengeRegistry,
    private val activeId: () -> String?,
    private val clock: Clock? = null,
) {
    suspend fun execute(original: Alarm, label: String, time: String, challengeId: String, question: String, answer: String) {
        fun checkNotRinging() {
            if (activeId() == original.id) throw AlarmFailure(AlarmFailureReason.ACTIVE_ALARM)
        }
        checkNotRinging()
        val operationClock = clock ?: Clock.systemDefaultZone()
        val current = repository.get(original.id) ?: throw AlarmFailure(AlarmFailureReason.MISSING_ALARM)
        if (current != original) throw AlarmFailure(AlarmFailureReason.STALE_ALARM)
        val changed = prepareAlarm(label, time, challengeId, question, answer, challenges, operationClock, original)
        checkNotRinging()
        var schedulingAttempted = false
        try {
            repository.save(changed)
            checkNotRinging()
            schedulingAttempted = true
            scheduler.schedule(changed)
        } catch (error: Exception) {
            // Also compensate coroutine cancellation after persistence; do not abandon the old row.
            val rollbackErrors = withContext(NonCancellable) {
                val errors = mutableListOf<Exception>()
                try { repository.save(original) } catch (failure: Exception) { errors += failure }
                if (schedulingAttempted) {
                    try {
                        if (original.enabled && original.triggerAtMillis > operationClock.millis()) scheduler.schedule(original)
                        else scheduler.cancel(original.id)
                    } catch (failure: Exception) { errors += failure }
                }
                errors
            }
            if (error is kotlinx.coroutines.CancellationException) {
                rollbackErrors.forEach(error::addSuppressed)
                throw error
            }
            if (rollbackErrors.isNotEmpty()) {
                throw AlarmFailure(AlarmFailureReason.EDIT_RESTORE_FAILED).apply {
                    addSuppressed(error)
                    rollbackErrors.forEach(::addSuppressed)
                }
            }
            throw error
        }
    }
}
