package dev.daybreak.domain

import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val triggerAtMillis: Long,
    val challengeId: String,
    val question: String = "",
    val answerDigest: String = "",
    val enabled: Boolean = true,
)

interface AlarmRepository {
    fun observe(): Flow<List<Alarm>>
    suspend fun get(id: String): Alarm?
    suspend fun save(alarm: Alarm)
    suspend fun delete(id: String)
}

interface AlarmScheduler {
    fun schedule(alarm: Alarm)
    fun cancel(id: String)
}

/** A challenge defines content and validation; it never schedules or stops an alarm. */
interface Challenge {
    val id: String
    fun create(alarm: Alarm): ChallengePrompt
    fun accepts(prompt: ChallengePrompt, input: String): Boolean
}

sealed interface PromptContent {
    data class UserText(val text: String) : PromptContent
    data class Addition(val left: Int, val right: Int) : PromptContent
}

data class ChallengePrompt(val content: PromptContent, val expectedDigest: String, val salt: String)

fun answerDigest(answer: String, salt: String): String = MessageDigest.getInstance("SHA-256")
    .digest((salt + ":" + answer.trim().lowercase(Locale.ROOT)).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

class QuestionChallenge : Challenge {
    override val id = "question.v1"
    override fun create(alarm: Alarm) = ChallengePrompt(PromptContent.UserText(alarm.question), alarm.answerDigest, alarm.id)
    override fun accepts(prompt: ChallengePrompt, input: String) =
        input.isNotBlank() && answerDigest(input, prompt.salt) == prompt.expectedDigest
}

/** Randomness is injectable so puzzle generation can be tested deterministically. */
class SumChallenge(private val random: Random = Random.Default) : Challenge {
    override val id = "sum.v1"
    override fun create(alarm: Alarm): ChallengePrompt {
        val left = random.nextInt(2, 20)
        val right = random.nextInt(2, 20)
        return ChallengePrompt(PromptContent.Addition(left, right), answerDigest("${left + right}", alarm.id), alarm.id)
    }
    override fun accepts(prompt: ChallengePrompt, input: String) =
        input.trim().toIntOrNull()?.let { answerDigest(it.toString(), prompt.salt) == prompt.expectedDigest } ?: false
}

class ChallengeRegistry(val options: List<Challenge> = listOf(QuestionChallenge(), SumChallenge())) {
    init { require(options.map { it.id }.distinct().size == options.size) { "Duplicate challenge ID" } }
    fun find(id: String): Challenge? = options.firstOrNull { it.id == id }
}

enum class AlarmAction { SNOOZE, DISMISS }

/** Both actions pass through this same gate. Selecting an action never stops playback. */
class ChallengeSession(val challenge: Challenge, val prompt: ChallengePrompt) {
    fun verify(input: String): Boolean = challenge.accepts(prompt, input)
}
