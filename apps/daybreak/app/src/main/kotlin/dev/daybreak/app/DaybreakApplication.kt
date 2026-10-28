package dev.daybreak.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import dev.daybreak.app.alarm.AndroidAlarmScheduler
import dev.daybreak.app.alarm.RingingService
import dev.daybreak.data.AlarmDatabase
import dev.daybreak.data.RoomAlarmRepository
import dev.daybreak.domain.ChallengeRegistry
import dev.daybreak.domain.CreateAlarm
import dev.daybreak.domain.DeleteAlarm
import dev.daybreak.domain.CompleteAlarmChallenge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small explicit composition root; replace with DI tooling only when scale warrants it. */
class DaybreakApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy {
        RoomAlarmRepository(Room.databaseBuilder(this, AlarmDatabase::class.java, "daybreak.db")
            .build().alarms())
    }
    val scheduler by lazy { AndroidAlarmScheduler(this) }
    val challenges = ChallengeRegistry()
    private val preferences by lazy { getSharedPreferences("ringing", MODE_PRIVATE) }
    private val activeState by lazy { MutableStateFlow(preferences.getString("activeId", null)) }
    val activeId by lazy { activeState.asStateFlow() }
    fun markActive(id: String?) {
        preferences.edit { if (id == null) remove("activeId") else putString("activeId", id) }
        activeState.value = id
    }

    val screenViewModelFactory by lazy {
        viewModelFactory {
            initializer { AlarmListViewModel(repository, DeleteAlarm(repository, scheduler), activeId) }
            initializer { CreateAlarmViewModel(CreateAlarm(repository, scheduler, challenges)) }
            initializer {
                RingingViewModel(repository, challenges, CompleteAlarmChallenge(repository, scheduler), activeId,
                    AlarmPlayback { id ->
                        if (activeId.value != id) throw dev.daybreak.domain.AlarmFailure(dev.daybreak.domain.AlarmFailureReason.ACTIVE_CHANGED)
                        // Service owns the player; the adapter only requests its shutdown.
                        stopService(Intent(this@DaybreakApplication, RingingService::class.java))
                        markActive(null)
                    })
            }
        }
    }
}

val Context.graph: DaybreakApplication get() = applicationContext as DaybreakApplication
