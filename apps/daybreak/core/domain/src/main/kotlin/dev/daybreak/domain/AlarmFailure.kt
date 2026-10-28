package dev.daybreak.domain

/** Stable failure reasons; Android presentation owns wording and translations. */
enum class AlarmFailureReason {
    BLANK_LABEL, INVALID_TIME, UNSUPPORTED_CHALLENGE, MISSING_QUESTION_ANSWER,
    ACTIVE_ALARM, WRONG_ANSWER, EXACT_ACCESS_DENIED, ACTIVE_CHANGED, MISSING_ALARM,
}

class AlarmFailure(val reason: AlarmFailureReason) : IllegalArgumentException(reason.name)
