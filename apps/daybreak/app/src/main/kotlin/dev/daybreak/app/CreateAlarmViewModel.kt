package dev.daybreak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.domain.CreateAlarm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlarmDraft(
    val label: String = "",
    val time: String = "07:00",
    val challengeId: String = "sum.v1",
    val question: String = "",
    val answer: String = "",
)

data class CreateAlarmUiState(
    val draft: AlarmDraft = AlarmDraft(),
    val busy: Boolean = false,
    val created: Boolean = false,
    val message: Int? = null,
)

class CreateAlarmViewModel(private val createAlarm: CreateAlarm) : ViewModel() {
    private val _state = MutableStateFlow(CreateAlarmUiState())
    val state = _state.asStateFlow()

    // Drafts survive rotation in memory; personal answers never enter SavedStateHandle.
    fun begin(defaultLabel: String) {
        if (!state.value.busy) _state.value = CreateAlarmUiState(draft = AlarmDraft(label = defaultLabel))
    }
    fun updateDraft(draft: AlarmDraft) {
        if (!state.value.busy) _state.update { it.copy(draft = draft) }
    }
    fun close() {
        if (!state.value.busy) _state.value = CreateAlarmUiState()
    }
    fun save() {
        if (state.value.busy || state.value.created) return
        val draft = state.value.draft
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                createAlarm.execute(draft.label, draft.time, draft.challengeId, draft.question, draft.answer)
                _state.update { it.copy(draft = AlarmDraft(), created = true,
                    message = R.string.alarm_created) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(message = error.messageResource(R.string.error_create_alarm)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun creationHandled() { _state.update { it.copy(created = false) } }
    fun messageShown(expected: Int) { _state.update { if (it.message == expected) it.copy(message = null) else it } }
}
