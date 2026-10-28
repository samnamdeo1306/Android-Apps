package dev.daybreak.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.daybreak.app.graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("alarmId") ?: return
        ContextCompat.startForegroundService(context,
            Intent(context, RingingService::class.java).putExtra("alarmId", id))
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        context.graph.scope.launch {
            try {
                // Deliberately skip alarms missed while powered off; never ring a stale alarm on boot.
                val graph = context.graph
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) graph.markActive(null)
                graph.repository.observe().first().filter { it.enabled }.forEach { alarm ->
                    if (alarm.triggerAtMillis > System.currentTimeMillis() && graph.scheduler.canSchedule()) {
                        graph.scheduler.schedule(alarm)
                    } else if (alarm.triggerAtMillis <= System.currentTimeMillis()) {
                        graph.repository.save(alarm.copy(enabled = false))
                    }
                }
            } finally { pending.finish() }
        }
    }
}
