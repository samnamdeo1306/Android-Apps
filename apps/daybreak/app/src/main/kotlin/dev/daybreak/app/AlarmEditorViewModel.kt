package dev.daybreak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.domain.*
import java.time.Instant
import java.time.ZoneId
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

data class AlarmEditorUiState(
    val draft: AlarmDraft = AlarmDraft(),
    val original: Alarm? = null,
    val visible: Boolean = false,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val message: Int? = null,
)

class AlarmEditorViewModel(
    private val createAlarm: CreateAlarm,
    private val updateAlarm: UpdateAlarm,
    private val activeId: StateFlow<String?>,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmEditorUiState())
    val state = _state.asStateFlow()

    // Drafts survive rotation in memory; personal answers never enter SavedStateHandle.
    fun begin(defaultLabel: String) {
        if (!state.value.busy) _state.value = AlarmEditorUiState(visible = true, draft = AlarmDraft(label = defaultLabel))
    }
    fun edit(alarm: Alarm) {
        if (state.value.busy) return
        if (activeId.value == alarm.id) {
            _state.update { it.copy(message = R.string.error_active_alarm) }
            return
        }
        val time = Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(ZoneId.systemDefault())
            .toLocalTime().withSecond(0).withNano(0)
        _state.value = AlarmEditorUiState(visible = true, original = alarm,
            draft = AlarmDraft(alarm.label, time.toString(), alarm.challengeId, alarm.question))
    }
    fun updateDraft(draft: AlarmDraft) {
        if (!state.value.busy) _state.update { it.copy(draft = draft) }
    }
    fun close() {
        if (!state.value.busy) _state.value = AlarmEditorUiState()
    }
    fun save() {
        if (state.value.busy || state.value.saved) return
        val draft = state.value.draft
        val original = state.value.original
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                if (original == null) createAlarm.execute(draft.label, draft.time, draft.challengeId, draft.question, draft.answer)
                else updateAlarm.execute(original, draft.label, draft.time, draft.challengeId, draft.question, draft.answer)
                _state.update { it.copy(draft = AlarmDraft(), visible = false, original = null, saved = true,
                    message = if (original == null) R.string.alarm_created else R.string.alarm_updated) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(message = error.messageResource(if (original == null) R.string.error_create_alarm else R.string.error_update_alarm)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun saveHandled() { _state.update { it.copy(saved = false) } }
    fun messageShown(expected: Int) { _state.update { if (it.message == expected) it.copy(message = null) else it } }
}
