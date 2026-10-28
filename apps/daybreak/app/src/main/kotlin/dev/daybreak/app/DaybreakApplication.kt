package dev.daybreak.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.daybreak.domain.AlarmPhase
import dev.daybreak.domain.ReconcileSchedules
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
import kotlinx.coroutines.flow.first

/** Small explicit composition root; replace with DI tooling only when scale warrants it. */
class DaybreakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.daybreak.app.alarm.AlarmNotifications(this).createChannel()
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy {
        RoomAlarmRepository(Room.databaseBuilder(this, AlarmDatabase::class.java, "daybreak.db")
            .addMigrations(AlarmDatabase.MIGRATION_1_2).build().alarms())
    }
    val scheduler by lazy { AndroidAlarmScheduler(this) }
    val challenges = ChallengeRegistry()
    // Room owns occurrence state; this is only the fast UI/window cache for this process.
    private val activeState = MutableStateFlow<String?>(null)
    val activeId = activeState.asStateFlow()
    private val syncIssue = MutableStateFlow(false)
    val scheduleSyncIssue = syncIssue.asStateFlow()
    val reconciler by lazy { ReconcileSchedules(repository, scheduler) }
    private var syncJob: Job? = null
    fun markActive(id: String?) { activeState.value = id }

    suspend fun refreshSchedules(skipRinging: Boolean = false, preserveDueGrace: Boolean = true) {
        try {
            reconciler.refresh(skipRinging, preserveDueGrace)
            syncIssue.value = false
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            syncIssue.value = true
            Log.e("DaybreakSchedules", "Schedule reconciliation pending", error)
        }
        if (skipRinging) markActive(null)
        else if (activeId.value == null) {
            try { markActive(repository.observe().first().firstOrNull { it.enabled && it.phase == AlarmPhase.RINGING }?.id) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { syncIssue.value = true; Log.e("DaybreakSchedules", "Could not restore active occurrence", error) }
        }
    }

    fun startScheduleSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            try {
                repository.scheduleChanges().collect {
                    try { reconciler.drain(); syncIssue.value = false }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        syncIssue.value = true
                        Log.e("DaybreakSchedules", "Desired schedule remains pending", error)
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                syncIssue.value = true
                Log.e("DaybreakSchedules", "Schedule journal unavailable", error)
            }
        }
    }
    fun retrySchedules() { scope.launch { refreshSchedules() } }

    val screenViewModelFactory by lazy {
        viewModelFactory {
            initializer { AlarmListViewModel(repository, DeleteAlarm(repository, scheduler), activeId) }
            initializer {
                AlarmEditorViewModel(CreateAlarm(repository, scheduler, challenges),
                    dev.daybreak.domain.UpdateAlarm(repository, scheduler, challenges, { activeId.value }), activeId)
            }
            initializer {
                RingingViewModel(repository, challenges, CompleteAlarmChallenge(repository, scheduler), activeId,
                    AlarmPlayback { id ->
                        if (activeId.value != null && activeId.value != id) throw dev.daybreak.domain.AlarmFailure(dev.daybreak.domain.AlarmFailureReason.ACTIVE_CHANGED)
                        // Service owns the player; the adapter only requests its shutdown.
                        stopService(Intent(this@DaybreakApplication, RingingService::class.java))
                        markActive(null)
                    })
            }
        }
    }
}

val Context.graph: DaybreakApplication get() = applicationContext as DaybreakApplication
