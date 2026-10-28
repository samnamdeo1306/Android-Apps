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
        val numbers = Regex("\\d+").findAll(prompt.text).map { it.value.toInt() }.toList()
        val answer = numbers.sum()
        assertTrue(challenge.accepts(prompt, answer.toString()))
        assertFalse(challenge.accepts(prompt, (answer + 1).toString()))
        assertFalse(challenge.accepts(prompt, "not a number"))
    }
    @Test fun registryCanAddStrategyWithoutChangingSession() {
        val custom = object : Challenge {
            override val id = "custom.v1"
            override val title = "Custom"
            override fun create(alarm: Alarm) = ChallengePrompt("Type ready", "", "")
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
}
