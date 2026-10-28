package dev.daybreak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val busy: Boolean = false,
    val message: Int? = null,
)

class AlarmListViewModel(
    repository: AlarmRepository,
    private val deleteAlarm: DeleteAlarm,
    private val activeId: StateFlow<String?>,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmListUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observe().collect { alarms -> _state.update { it.copy(alarms = alarms.toList()) } }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.update { it.copy(message = R.string.error_load_alarms) } }
        }
    }

    fun delete(alarm: Alarm) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { deleteAlarm.execute(alarm, activeId.value) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(message = error.messageResource(R.string.error_delete_alarm)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun messageShown(expected: Int) { _state.update { if (it.message == expected) it.copy(message = null) else it } }
}
