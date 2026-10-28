package dev.daybreak.app.alarm

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dev.daybreak.app.MainActivity
import dev.daybreak.app.R

/** Android-only notification policy; no snooze/dismiss actions can bypass the challenge. */
class AlarmNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    data class Access(val notifications: Boolean, val highImportance: Boolean, val fullScreen: Boolean) {
        val canPresentFullScreen: Boolean get() = notifications && highImportance && fullScreen
    }

    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_description)
            setSound(null, null) // The service owns alarm audio, independently of notification sound.
        })
    }

    fun access() = Access(manager.areNotificationsEnabled(),
        (manager.getNotificationChannel(CHANNEL_ID)?.importance ?: NotificationManager.IMPORTANCE_NONE) >= NotificationManager.IMPORTANCE_HIGH,
        Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent())

    fun build(requestFullScreen: Boolean, @StringRes body: Int = R.string.notification_body,
        access: Access = access()): Notification {
        val open = PendingIntent.getActivity(context, 1, openIntent("open"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(body)).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false).setContentIntent(open)
        if (requestFullScreen && access.canPresentFullScreen) {
            val options = if (Build.VERSION.SDK_INT >= 35) ActivityOptions.makeBasic().apply {
                // Only this explicit alarm UI intent receives creator-side BAL privileges.
                @Suppress("DEPRECATION")
                setPendingIntentCreatorBackgroundActivityStartMode(if (Build.VERSION.SDK_INT >= 36)
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle() else null
            val fullScreen = PendingIntent.getActivity(context, 2, openIntent("ringing"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options)
            builder.setFullScreenIntent(fullScreen, true)
        }
        if (!requestFullScreen && body == R.string.notification_loading) builder.setSilent(true)
        return builder.build()
    }

    private fun openIntent(path: String) = Intent(context, MainActivity::class.java)
        .setData("daybreak://$path".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun reportPlaybackFailure() { manager.notify(RINGING_ID, build(false, R.string.notification_playback_failure)) }
    fun removeLoading() { manager.cancel(LOADING_ID) }
    fun removeAll() { manager.cancel(LOADING_ID); manager.cancel(RINGING_ID) }

    companion object {
        const val CHANNEL_ID = "ringing"
        const val LOADING_ID = 6
        const val RINGING_ID = 7
    }
}
