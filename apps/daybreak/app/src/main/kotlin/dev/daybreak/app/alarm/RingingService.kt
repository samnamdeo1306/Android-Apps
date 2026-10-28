package dev.daybreak.app.alarm

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.daybreak.app.R
import dev.daybreak.app.MainActivity
import dev.daybreak.app.graph
import kotlinx.coroutines.*

class RingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ringingId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("ringing", getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_description); setSound(null, null)
            })
        startForeground(7, NotificationCompat.Builder(this, "ringing")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true).build())
        val id = intent?.getStringExtra("alarmId") ?: graph.activeId.value
        if (id == null) { stopSelf(); return START_NOT_STICKY }
        scope.launch {
            val alarm = graph.repository.get(id)
            if (alarm == null || !alarm.enabled) {
                if (ringingId == null) { graph.markActive(null); stopSelf() }
                return@launch
            }
            // A single challenge is visible at once. Defer overlapping alarms by one minute.
            if (ringingId != null && ringingId != id) {
                val deferred = alarm.copy(triggerAtMillis = System.currentTimeMillis() + 60_000)
                runCatching { graph.scheduler.schedule(deferred); graph.repository.save(deferred) }
                return@launch
            }
            ringingId = id
            graph.markActive(id)
            if (player == null) {
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setWakeMode(this@RingingService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@RingingService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    isLooping = true
                    setOnPreparedListener { it.start() }
                    setOnErrorListener { _, _, _ -> false }
                    prepareAsync()
                }
            }
        }
        return START_STICKY
    }
    override fun onDestroy() {
        player?.release(); player = null; scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
