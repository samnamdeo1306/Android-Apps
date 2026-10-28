package dev.daybreak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Android adapter stops playback only after the shared completion gate succeeds. */
fun interface AlarmPlayback { fun stop(alarmId: String) }

data class RingingUi(val alarm: Alarm, val prompt: ChallengePrompt)
data class RingingUiState(
    val active: Boolean = false,
    val ringing: RingingUi? = null,
    val busy: Boolean = false,
    val message: Int? = null,
)

class RingingViewModel(
    private val repository: AlarmRepository,
    private val challenges: ChallengeRegistry,
    private val completeAlarm: CompleteAlarmChallenge,
    private val activeId: StateFlow<String?>,
    private val playback: AlarmPlayback,
) : ViewModel() {
    private val _state = MutableStateFlow(RingingUiState(active = activeId.value != null))
    val state = _state.asStateFlow()
    private var session: ChallengeSession? = null

    init {
        viewModelScope.launch {
            activeId.collectLatest { id ->
                session = null
                _state.update { it.copy(active = id != null, ringing = null) }
                if (id != null) {
                    try {
                        val alarm = repository.get(id) ?: throw AlarmFailure(AlarmFailureReason.MISSING_ALARM)
                        val challenge = challenges.find(alarm.challengeId)
                            ?: throw AlarmFailure(AlarmFailureReason.UNSUPPORTED_CHALLENGE)
                        val current = ChallengeSession(challenge, challenge.create(alarm))
                        session = current
                        _state.update { it.copy(ringing = RingingUi(alarm, current.prompt)) }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { _state.update { it.copy(message = error.messageResource(R.string.error_load_challenge)) } }
                }
            }
        }
    }

    fun submit(action: AlarmAction, answer: String) {
        if (state.value.busy) return
        val current = state.value.ringing ?: return
        val currentSession = session ?: return
        if (activeId.value != current.alarm.id) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                completeAlarm.execute(current.alarm, currentSession, action, answer)
                playback.stop(current.alarm.id)
                _state.update { it.copy(message = if (action == AlarmAction.SNOOZE)
                    R.string.alarm_snoozed else R.string.alarm_dismissed) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(message = error.messageResource(R.string.error_complete_alarm)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun messageShown(expected: Int) { _state.update { if (it.message == expected) it.copy(message = null) else it } }
}
