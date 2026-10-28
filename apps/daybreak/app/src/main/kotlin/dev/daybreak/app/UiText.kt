package dev.daybreak.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.daybreak.domain.*
import java.text.NumberFormat

/** Resource IDs stay in app presentation; never display raw exception messages. */
@StringRes
fun Throwable.messageResource(@StringRes fallback: Int): Int = when (this) {
    is AlarmFailure -> when (reason) {
        AlarmFailureReason.BLANK_LABEL -> R.string.error_blank_label
        AlarmFailureReason.INVALID_TIME -> R.string.error_invalid_time
        AlarmFailureReason.UNSUPPORTED_CHALLENGE -> R.string.error_unsupported_challenge
        AlarmFailureReason.MISSING_QUESTION_ANSWER -> R.string.error_question_answer
        AlarmFailureReason.ACTIVE_ALARM -> R.string.error_active_alarm
        AlarmFailureReason.WRONG_ANSWER -> R.string.error_wrong_answer
        AlarmFailureReason.EXACT_ACCESS_DENIED -> R.string.error_exact_access
        AlarmFailureReason.ACTIVE_CHANGED -> R.string.error_active_changed
        AlarmFailureReason.MISSING_ALARM -> R.string.error_missing_alarm
    }
    else -> fallback
}

@StringRes
fun challengeTitle(id: String): Int = when (id) {
    "question.v1" -> R.string.challenge_question
    "sum.v1" -> R.string.challenge_sum
    else -> R.string.challenge_unsupported
}

@Composable
fun promptText(content: PromptContent): String = when (content) {
    is PromptContent.UserText -> content.text
    is PromptContent.Addition -> {
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val numbers = NumberFormat.getIntegerInstance(locale)
        stringResource(R.string.addition_prompt, numbers.format(content.left), numbers.format(content.right))
    }
}
