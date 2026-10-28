package dev.daybreak.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Shared create/edit validation; no persistence or Android dependency. */
internal fun prepareAlarm(
    label: String, time: String, challengeId: String, question: String, answer: String,
    challenges: ChallengeRegistry, clock: Clock, original: Alarm? = null, repeatDays: Set<java.time.DayOfWeek> = emptySet(),
): Alarm {
    if (label.isBlank()) throw AlarmFailure(AlarmFailureReason.BLANK_LABEL)
    if (challenges.find(challengeId) == null) throw AlarmFailure(AlarmFailureReason.UNSUPPORTED_CHALLENGE)
    val localTime = runCatching { LocalTime.parse(time).withSecond(0).withNano(0) }.getOrNull()
        ?: throw AlarmFailure(AlarmFailureReason.INVALID_TIME)
    val keepAnswer = original?.challengeId == "question.v1" && challengeId == "question.v1" &&
        original.question == question.trim() && answer.isBlank()
    if (challengeId == "question.v1" && (question.isBlank() || (answer.isBlank() && !keepAnswer))) {
        throw AlarmFailure(AlarmFailureReason.MISSING_QUESTION_ANSWER)
    }
    val now = ZonedDateTime.now(clock)
    var target = now.withHour(localTime.hour).withMinute(localTime.minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    val rule = repeatDays.takeIf { it.isNotEmpty() }?.let { RepeatRule(localTime, it.toSet()) }
    val preserveTrigger = rule == original?.repeat && original != null && original.enabled && original.triggerAtMillis > clock.millis() &&
        (rule != null || Instant.ofEpochMilli(original.triggerAtMillis).atZone(clock.zone).toLocalTime()
            .withSecond(0).withNano(0) == localTime.withSecond(0).withNano(0))
    val next = if (preserveTrigger) original!!.triggerAtMillis else rule?.nextAfter(now.toInstant(), clock.zone) ?: target.toInstant().toEpochMilli()
    val draft = Alarm(id = original?.id ?: java.util.UUID.randomUUID().toString(), label = label.trim(),
        triggerAtMillis = next, repeat = rule, occurrenceAtMillis = if (preserveTrigger) original!!.occurrenceAtMillis else next,
        phase = if (preserveTrigger) original!!.phase else AlarmPhase.SCHEDULED,
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
    suspend fun execute(label: String, time: String, challengeId: String, question: String, answer: String,
        repeatDays: Set<java.time.DayOfWeek> = emptySet()) {
        val alarm = prepareAlarm(label, time, challengeId, question, answer, challenges, clock ?: Clock.systemDefaultZone(), repeatDays = repeatDays)
        repository.save(alarm)
        try { scheduler.schedule(alarm) }
        catch (error: Exception) { repository.delete(alarm.id); throw error }
    }
}

class DeleteAlarm(private val repository: AlarmRepository, private val scheduler: AlarmScheduler) {
    suspend fun execute(alarm: Alarm, activeId: String?) {
        if (activeId == alarm.id || alarm.phase == AlarmPhase.RINGING) throw AlarmFailure(AlarmFailureReason.ACTIVE_ALARM)
        if (!repository.deleteIfUnchanged(alarm)) throw AlarmFailure(AlarmFailureReason.STALE_ALARM)
        scheduler.cancel(alarm.id)
    }
}
