package dev.daybreak.app.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.daybreak.app.MainActivity
import dev.daybreak.app.R
import dev.daybreak.app.graph
import dev.daybreak.domain.Alarm
import dev.daybreak.domain.AlarmAction
import dev.daybreak.domain.ChallengeSession
import dev.daybreak.domain.CompleteAlarmChallenge
import dev.daybreak.domain.QuestionChallenge
import dev.daybreak.domain.answerDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = AlarmNotifications(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test fun channelCreationIsSilentHighAndDoesNotResetUserImportance() {
        notifications.createChannel()
        val existing = manager.getNotificationChannel(AlarmNotifications.CHANNEL_ID)
        assertNull(existing.sound)
        val importance = existing.importance
        notifications.createChannel()
        assertEquals(importance, manager.getNotificationChannel(AlarmNotifications.CHANNEL_ID).importance)
    }

    @Test fun missingNotificationChannelOrFullScreenAccessKeepsTheContentTap() {
        listOf(
            AlarmNotifications.Access(false, true, true),
            AlarmNotifications.Access(true, false, true),
            AlarmNotifications.Access(true, true, false),
        ).forEach { access ->
            val notification = notifications.build(true, access = access)
            assertNull(notification.fullScreenIntent)
            assertNotNull(notification.contentIntent)
            assertEquals(Notification.CATEGORY_ALARM, notification.category)
            assertEquals(0, notification.actions?.size ?: 0)
        }
    }

    @Test fun loadingNeverRequestsFullScreenAndValidatedAlarmHasAnImmutableIntent() {
        val allowed = AlarmNotifications.Access(true, true, true)
        val loading = notifications.build(false, R.string.notification_loading, allowed)
        assertNull(loading.fullScreenIntent)
        val ringing = notifications.build(true, access = allowed)
        assertNotNull(ringing.fullScreenIntent)
        assertNotEquals(ringing.contentIntent, ringing.fullScreenIntent)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(ringing.fullScreenIntent.isImmutable)
        assertEquals(Notification.FLAG_ONGOING_EVENT, ringing.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test fun startupLocksHaveIndependentOwnershipAndReleaseIsIdempotent() {
        val first = AlarmStartupWakeLocks.acquire(context)
        val second = AlarmStartupWakeLocks.acquire(context)
        try {
            AlarmStartupWakeLocks.release(first)
            AlarmStartupWakeLocks.release(first)
            assertFalse(AlarmStartupWakeLocks.isHeld(first))
            assertTrue(AlarmStartupWakeLocks.isHeld(second))
        } finally { AlarmStartupWakeLocks.release(first); AlarmStartupWakeLocks.release(second) }
    }

    @Test fun startupLockExpiresWhenTheServiceNeverArrives() {
        val token = AlarmStartupWakeLocks.acquire(context, 100)
        try {
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while (AlarmStartupWakeLocks.isHeld(token) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertFalse(AlarmStartupWakeLocks.isHeld(token))
        } finally { AlarmStartupWakeLocks.release(token) }
    }

    /** Real scheduler/receiver/service/Room path under forced Doze. Never run on a physical device. */
    @Test fun alarmClockDeliveryInForcedIdleKeepsBothActionsGated() = runBlocking {
        assumeTrue(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        val graph = context.graph
        assumeTrue("Do not interrupt an already ringing alarm", graph.activeId.value == null)
        assumeTrue("Exact alarm access required", graph.scheduler.canSchedule())
        assumeTrue("Notifications required for this end-to-end check", notifications.access().notifications)
        val id = "delivery-test-${java.util.UUID.randomUUID()}"
        var alarm = Alarm(id = id, label = "Delivery test", triggerAtMillis = 0,
            challengeId = "question.v1", question = "Type ready", answerDigest = answerDigest("ready", id))
        var preparedIdle = false
        try {
            shell("dumpsys battery unplug")
            preparedIdle = true
            shell("input keyevent 223")
            val idleResult = shell("dumpsys deviceidle force-idle")
            assertTrue("Could not enter deep idle: $idleResult", idleResult.contains("Now forced in to deep idle mode"))
            assertEquals("IDLE", shell("dumpsys deviceidle get deep").trim())
            // Imminent alarm-clock alarms prevent idle entry. Register after verifying IDLE;
            // Android is expected to leave idle to prepare for this user-visible alarm.
            alarm = alarm.copy(triggerAtMillis = System.currentTimeMillis() + 5_000)
            graph.repository.save(alarm)
            graph.scheduler.schedule(alarm)
            withTimeout(30_000) { graph.activeId.first { it == id } }
            withTimeout(10_000) {
                while (!manager.activeNotifications.any { it.id == AlarmNotifications.RINGING_ID } ||
                    manager.activeNotifications.any { it.id == AlarmNotifications.LOADING_ID }) delay(50)
            }
            val posted = manager.activeNotifications.firstOrNull { it.id == AlarmNotifications.RINGING_ID }
            assertNotNull("Ringing foreground notification posted", posted)
            assertEquals(Notification.CATEGORY_ALARM, posted!!.notification.category)
            assertNotNull(posted.notification.contentIntent)
            assertEquals(notifications.access().canPresentFullScreen, posted.notification.fullScreenIntent != null)
            assertFalse(manager.activeNotifications.any { it.id == AlarmNotifications.LOADING_ID })
            if (notifications.access().canPresentFullScreen) {
                withTimeout(10_000) {
                    while (true) {
                        var resumed = false
                        instrumentation.runOnMainSync {
                            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                                .any { it is MainActivity }
                        }
                        if (resumed && context.getSystemService(PowerManager::class.java).isInteractive) break
                        delay(50)
                    }
                }
            }
            val ringingAlarm = graph.repository.get(id)!!
            assertEquals(dev.daybreak.domain.AlarmPhase.RINGING, ringingAlarm.phase)
            val challenge = QuestionChallenge()
            val session = ChallengeSession(challenge, challenge.create(alarm))
            for (action in AlarmAction.entries) {
                try {
                    CompleteAlarmChallenge(graph.repository, graph.scheduler).execute(ringingAlarm, session, action, "wrong")
                    fail("Wrong answers must never finish the alarm")
                } catch (_: IllegalArgumentException) { }
                assertEquals(id, graph.activeId.value)
                assertEquals(ringingAlarm, graph.repository.get(id))
            }
            CompleteAlarmChallenge(graph.repository, graph.scheduler).execute(ringingAlarm, session, AlarmAction.DISMISS, "ready")
            assertEquals(false, graph.repository.get(id)?.enabled)
        } finally {
            // Test-only cleanup; production UI still has no direct playback stop control.
            if (graph.activeId.value == id) {
                withContext(Dispatchers.Main) {
                    context.stopService(Intent(context, RingingService::class.java))
                    graph.markActive(null)
                }
            }
            graph.scheduler.cancel(id)
            graph.repository.delete(id)
            if (preparedIdle) {
                shell("dumpsys deviceidle unforce")
                shell("dumpsys battery reset")
                shell("input keyevent 224")
            }
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
