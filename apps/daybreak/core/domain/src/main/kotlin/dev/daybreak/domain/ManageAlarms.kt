package dev.daybreak.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Shared create/edit validation; no persistence or Android dependency. */
internal fun prepareAlarm(
    label: String, time: String, challengeId: String, question: String, answer: String,
    challenges: ChallengeRegistry, clock: Clock, original: Alarm? = null,
): Alarm {
    if (label.isBlank()) throw AlarmFailure(AlarmFailureReason.BLANK_LABEL)
    if (challenges.find(challengeId) == null) throw AlarmFailure(AlarmFailureReason.UNSUPPORTED_CHALLENGE)
    val localTime = runCatching { LocalTime.parse(time) }.getOrNull()
        ?: throw AlarmFailure(AlarmFailureReason.INVALID_TIME)
    val keepAnswer = original?.challengeId == "question.v1" && challengeId == "question.v1" &&
        original.question == question.trim() && answer.isBlank()
    if (challengeId == "question.v1" && (question.isBlank() || (answer.isBlank() && !keepAnswer))) {
        throw AlarmFailure(AlarmFailureReason.MISSING_QUESTION_ANSWER)
    }
    val now = ZonedDateTime.now(clock)
    var target = now.withHour(localTime.hour).withMinute(localTime.minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    val preserveTrigger = original != null && original.enabled && original.triggerAtMillis > clock.millis() &&
        Instant.ofEpochMilli(original.triggerAtMillis).atZone(clock.zone).toLocalTime()
            .withSecond(0).withNano(0) == localTime.withSecond(0).withNano(0)
    val draft = Alarm(id = original?.id ?: java.util.UUID.randomUUID().toString(), label = label.trim(),
        triggerAtMillis = if (preserveTrigger) original!!.triggerAtMillis else target.toInstant().toEpochMilli(),
        challengeId = challengeId, question = if (challengeId == "question.v1") question.trim() else "")
    return draft.copy(answerDigest = when {
        challengeId != "question.v1" -> ""
        keepAnswer -> original!!.answerDigest
        else -> answerDigest(answer, draft.id)
    })
}

/** Persists before delivery can read the row; compensates for scheduling failure. */
class CreateAlarm(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val challenges: ChallengeRegistry,
    private val clock: Clock? = null,
) {
    suspend fun execute(label: String, time: String, challengeId: String, question: String, answer: String) {
        val alarm = prepareAlarm(label, time, challengeId, question, answer, challenges, clock ?: Clock.systemDefaultZone())
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
