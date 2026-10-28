package dev.daybreak.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dev.daybreak.app.MainActivity
import dev.daybreak.domain.Alarm
import dev.daybreak.domain.AlarmScheduler

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun canSchedule() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun operation(id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, AlarmReceiver::class.java).setData("daybreak://alarm/$id".toUri())
            .putExtra("alarmId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun schedule(alarm: Alarm) {
        check(canSchedule()) { "Allow exact alarms in system settings first." }
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, show), operation(alarm.id))
    }
    override fun cancel(id: String) = manager.cancel(operation(id))
}
