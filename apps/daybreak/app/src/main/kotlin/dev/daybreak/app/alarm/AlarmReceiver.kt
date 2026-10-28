package dev.daybreak.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.daybreak.app.graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("alarmId") ?: return
        val token = AlarmStartupWakeLocks.acquire(context)
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, RingingService::class.java).putExtra("alarmId", id)
                    .putExtra(AlarmStartupWakeLocks.EXTRA_TOKEN, token)
                    .putExtra("expectedTrigger", intent.getLongExtra("expectedTrigger", -1)))
        } catch (error: RuntimeException) {
            AlarmStartupWakeLocks.release(token)
            Log.e("DaybreakDelivery", "Alarm foreground service could not start", error)
        }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        context.graph.scope.launch {
            try {
                context.graph.refreshSchedules(skipRinging = intent.action == Intent.ACTION_BOOT_COMPLETED,
                    preserveDueGrace = false)
            } finally { pending.finish() }
        }
    }
}
