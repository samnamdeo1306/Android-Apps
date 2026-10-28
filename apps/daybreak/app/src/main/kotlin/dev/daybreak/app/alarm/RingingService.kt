package dev.daybreak.app.alarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import dev.daybreak.app.R
import dev.daybreak.app.graph
import dev.daybreak.domain.AlarmPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val deliveries = Mutex()
    private val notifications by lazy { AlarmNotifications(this) }
    private var player: MediaPlayer? = null
    private var ringingId: String? = null
    private var startupToken: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiverToken = intent?.getIntExtra(AlarmStartupWakeLocks.EXTRA_TOKEN, 0) ?: 0
        try {
            // Take over wakefulness synchronously before releasing the receiver's bounded lock.
            if (player == null && startupToken == null) startupToken = AlarmStartupWakeLocks.acquire(this)
            notifications.createChannel()
            if (ringingId == null) startForeground(AlarmNotifications.LOADING_ID,
                notifications.build(false, R.string.notification_loading))
        } catch (error: RuntimeException) {
            releaseStartup()
            Log.e(TAG, "Could not enter alarm foreground mode", error)
            stopSelfResult(startId)
            return START_NOT_STICKY
        } finally { AlarmStartupWakeLocks.release(receiverToken) }

        val requestedId = intent?.getStringExtra("alarmId") ?: graph.activeId.value
        val expectedTrigger = intent?.getLongExtra("expectedTrigger", -1) ?: -1
        val recovering = intent == null || intent.getBooleanExtra("resumeRinging", false)
        scope.launch {
            deliveries.withLock {
                val id = try { requestedId ?: graph.repository.observe().first()
                    .firstOrNull { it.enabled && it.phase == AlarmPhase.RINGING }?.id }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    releaseStartup()
                    Log.e(TAG, "Could not load persisted ringing occurrence", error)
                    stopSelfResult(startId)
                    return@withLock
                }
                if (id == null) { stopSelfResult(startId); return@withLock }
                try {
                    val alarm = graph.repository.get(id)
                    if (alarm == null || !alarm.enabled) {
                        if (ringingId == null) { graph.markActive(null); stopSelfResult(startId) }
                        return@withLock
                    }
                    if (ringingId == id && player != null) return@withLock
                    if ((expectedTrigger >= 0 && expectedTrigger != alarm.triggerAtMillis) ||
                        (recovering && alarm.phase != AlarmPhase.RINGING) ||
                        (alarm.phase != AlarmPhase.RINGING && alarm.triggerAtMillis > System.currentTimeMillis() + 1_000)) {
                        if (ringingId == null) stopSelfResult(startId)
                        return@withLock
                    }
                    if (ringingId != null && ringingId != id) {
                        // Retain the existing one-minute best-effort overlap policy, never replace the first alarm.
                        val deferred = alarm.copy(triggerAtMillis = System.currentTimeMillis() + 60_000,
                            phase = AlarmPhase.DEFERRED)
                        if (graph.repository.replace(alarm, deferred)) graph.scheduler.schedule(deferred)
                        return@withLock
                    }
                    if (alarm.phase != AlarmPhase.RINGING &&
                        !graph.repository.replace(alarm, alarm.copy(phase = AlarmPhase.RINGING))) {
                        if (ringingId == null) stopSelfResult(startId)
                        return@withLock
                    }
                    val firstDelivery = ringingId == null
                    ringingId = id
                    graph.markActive(id)
                    if (firstDelivery) {
                        // A separate notification ID makes this the first alerting post, after validation.
                        startForeground(AlarmNotifications.RINGING_ID, notifications.build(true))
                        notifications.removeLoading()
                    }
                    if (player == null) prepareAudio()
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    Log.e(TAG, "Alarm delivery could not complete", error)
                    if (ringingId == null) { releaseStartup(); stopSelfResult(startId) }
                    else if (ringingId == id && player == null) {
                        releaseStartup()
                        notifications.reportPlaybackFailure()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun prepareAudio() {
        val pending = MediaPlayer()
        player = pending
        try {
            pending.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            pending.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            pending.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            pending.isLooping = true
            pending.setOnPreparedListener { prepared ->
                try { if (player === prepared) prepared.start() }
                catch (error: RuntimeException) { audioFailed(prepared, error) }
                finally { releaseStartup() }
            }
            pending.setOnErrorListener { failed, _, _ -> audioFailed(failed, null); true }
            pending.prepareAsync()
        } catch (error: Exception) { audioFailed(pending, error) }
    }

    private fun audioFailed(failed: MediaPlayer, error: Exception?) {
        if (player !== failed) return
        Log.e(TAG, "Alarm audio unavailable", error)
        failed.release()
        player = null
        releaseStartup()
        // Keep the challenge and foreground notification accessible; never pretend completion succeeded.
        notifications.reportPlaybackFailure()
    }

    private fun releaseStartup() { startupToken?.let(AlarmStartupWakeLocks::release); startupToken = null }

    override fun onDestroy() {
        scope.cancel()
        player?.release(); player = null
        releaseStartup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.removeAll()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private companion object { const val TAG = "DaybreakDelivery" }
}
