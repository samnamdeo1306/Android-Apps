package dev.daybreak.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.app.alarm.RingingService
import dev.daybreak.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.time.ZonedDateTime

data class RingingUi(val alarm: Alarm, val session: ChallengeSession)

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.graph
    val alarms = graph.repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ringing = MutableStateFlow<RingingUi?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val options = graph.challenges.options

    init {
        viewModelScope.launch {
            graph.activeId.collect { id ->
                val alarm = id?.let { graph.repository.get(it) }
                val challenge = alarm?.let { graph.challenges.find(it.challengeId) }
                ringing.value = if (alarm != null && challenge != null)
                    RingingUi(alarm, ChallengeSession(challenge, challenge.create(alarm))) else null
                if (alarm != null && challenge == null) message.value = "Unknown challenge. Restore a supported app version."
            }
        }
    }

    fun create(label: String, time: String, challengeId: String, question: String, answer: String, onCreated: () -> Unit) = work {
        require(label.isNotBlank()) { "Add an alarm name." }
        requireNotNull(graph.challenges.find(challengeId)) { "Choose a supported challenge." }
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull()
        require(localTime != null) { "Enter time in 24-hour HH:mm format, for example 07:30." }
        if (challengeId == "question.v1") {
            require(question.isNotBlank() && answer.isNotBlank()) { "Add your question and agreed answer." }
        }
        val now = ZonedDateTime.now()
        var target = now.withHour(localTime.hour).withMinute(localTime.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val draft = Alarm(label = label.trim(), triggerAtMillis = target.toInstant().toEpochMilli(),
            challengeId = challengeId, question = question.trim())
        val alarm = draft.copy(answerDigest = if (challengeId == "question.v1") answerDigest(answer, draft.id) else "")
        // Persist before scheduling so a delivery always has a record to read. Roll back on denial.
        graph.repository.save(alarm)
        try { graph.scheduler.schedule(alarm) }
        catch (error: Exception) { graph.repository.delete(alarm.id); throw error }
        message.value = "Alarm scheduled. Your next morning starts here."
        onCreated()
    }

    fun delete(alarm: Alarm) = work {
        check(graph.activeId.value != alarm.id) { "Complete the ringing challenge first." }
        graph.scheduler.cancel(alarm.id)
        graph.repository.delete(alarm.id)
    }

    fun submit(action: AlarmAction, answer: String) = work {
        val current = requireNotNull(ringing.value)
        CompleteAlarmChallenge(graph.repository, graph.scheduler).execute(
            current.alarm, current.session, action, answer)
        graph.markActive(null)
        getApplication<Application>().stopService(Intent(getApplication(), RingingService::class.java))
        message.value = if (action == AlarmAction.SNOOZE) "Snoozed for 5 minutes." else "You're up. Make it a good day."
    }

    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message.value = error.message ?: "Something went wrong. Please try again." }
            finally { busy.value = false }
        }
    }
}
