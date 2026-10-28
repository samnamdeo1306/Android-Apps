package dev.daybreak.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChallengeTest {
    private val alarm = Alarm(id = "alarm-1", label = "Morning", triggerAtMillis = 1,
        challengeId = "question.v1", question = "Our meeting place?",
        answerDigest = answerDigest("Library", "alarm-1"))

    @Test fun questionAcceptsNormalizedAnswerAndRejectsWrongAndBlank() {
        val challenge = QuestionChallenge()
        val session = ChallengeSession(challenge, challenge.create(alarm))
        assertTrue(session.verify("  LIBRARY  "))
        assertFalse(session.verify("park"))
        assertFalse(session.verify(""))
    }
    @Test fun puzzleRequiresCorrectNumericAnswer() {
        val challenge = SumChallenge(Random(42))
        val prompt = challenge.create(alarm)
        val operands = prompt.content as PromptContent.Addition
        val answer = operands.left + operands.right
        assertTrue(challenge.accepts(prompt, answer.toString()))
        assertFalse(challenge.accepts(prompt, (answer + 1).toString()))
        assertFalse(challenge.accepts(prompt, "not a number"))
    }
    @Test fun registryCanAddStrategyWithoutChangingSession() {
        val custom = object : Challenge {
            override val id = "custom.v1"
            override fun create(alarm: Alarm) = ChallengePrompt(PromptContent.UserText("Type ready"), "", "")
            override fun accepts(prompt: ChallengePrompt, input: String) = input == "ready"
        }
        val registry = ChallengeRegistry(listOf(QuestionChallenge(), SumChallenge(), custom))
        val strategy = requireNotNull(registry.find("custom.v1"))
        assertTrue(ChallengeSession(strategy, strategy.create(alarm)).verify("ready"))
        assertNull(registry.find("removed.v1"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsFailEarly() {
        ChallengeRegistry(listOf(QuestionChallenge(), QuestionChallenge()))
    }
    @Test fun answersAreSaltedPerAlarm() {
        assertNotEquals(answerDigest("Library", "alarm-1"), answerDigest("Library", "alarm-2"))
    }

    @Test fun localeChangeKeepsPuzzleSessionAndAcceptsLocalizedDigits() {
        val challenge = SumChallenge(Random(42))
        val session = ChallengeSession(challenge, challenge.create(alarm))
        val operands = session.prompt.content as PromptContent.Addition
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            val localized = java.text.NumberFormat.getIntegerInstance().format(operands.left + operands.right)
            assertTrue(session.verify(localized))
            assertTrue(session.verify((operands.left + operands.right).toString()))
            assertEquals(operands, session.prompt.content)
        } finally { java.util.Locale.setDefault(original) }
    }

    @Test fun personalQuestionAndAnswerNormalizationDoNotFollowDisplayLocale() {
        val original = java.util.Locale.getDefault()
        val personal = alarm.copy(question = "İstanbul?", answerDigest = answerDigest("LIBRARY", alarm.id))
        val session = ChallengeSession(QuestionChallenge(), QuestionChallenge().create(personal))
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            assertEquals(PromptContent.UserText("İstanbul?"), session.prompt.content)
            assertTrue(session.verify(" library "))
        } finally { java.util.Locale.setDefault(original) }
    }
}
