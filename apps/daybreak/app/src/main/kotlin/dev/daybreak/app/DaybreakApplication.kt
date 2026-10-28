package dev.daybreak.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.room.Room
import dev.daybreak.app.alarm.AndroidAlarmScheduler
import dev.daybreak.data.AlarmDatabase
import dev.daybreak.data.RoomAlarmRepository
import dev.daybreak.domain.ChallengeRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

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
    val activeId by lazy { MutableStateFlow(preferences.getString("activeId", null)) }
    fun markActive(id: String?) {
        preferences.edit { if (id == null) remove("activeId") else putString("activeId", id) }
        activeId.value = id
    }
}

val Context.graph: DaybreakApplication get() = applicationContext as DaybreakApplication
