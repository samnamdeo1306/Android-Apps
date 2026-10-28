package dev.daybreak.domain

import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime

/** Validates and schedules the next local occurrence; compensates for scheduling failure. */
class CreateAlarm(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val challenges: ChallengeRegistry,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun execute(label: String, time: String, challengeId: String, question: String, answer: String) {
        if (label.isBlank()) throw AlarmFailure(AlarmFailureReason.BLANK_LABEL)
        if (challenges.find(challengeId) == null) throw AlarmFailure(AlarmFailureReason.UNSUPPORTED_CHALLENGE)
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull()
        if (localTime == null) throw AlarmFailure(AlarmFailureReason.INVALID_TIME)
        if (challengeId == "question.v1") {
            if (question.isBlank() || answer.isBlank()) throw AlarmFailure(AlarmFailureReason.MISSING_QUESTION_ANSWER)
        }
        val now = ZonedDateTime.now(clock)
        var target = now.withHour(localTime.hour).withMinute(localTime.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val draft = Alarm(label = label.trim(), triggerAtMillis = target.toInstant().toEpochMilli(),
            challengeId = challengeId, question = question.trim())
        val alarm = draft.copy(answerDigest = if (challengeId == "question.v1") answerDigest(answer, draft.id) else "")
        repository.save(alarm)
        try { scheduler.schedule(alarm) }
        catch (error: Exception) { repository.delete(alarm.id); throw error }
    }
}

class DeleteAlarm(private val repository: AlarmRepository, private val scheduler: AlarmScheduler) {
    suspend fun execute(alarm: Alarm, activeId: String?) {
        if (activeId == alarm.id) throw AlarmFailure(AlarmFailureReason.ACTIVE_ALARM)
        scheduler.cancel(alarm.id)
        repository.delete(alarm.id)
    }
}
