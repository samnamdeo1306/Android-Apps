package dev.daybreak.app.alarm

import android.content.Context
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicInteger

/** Bounded, process-local receiver -> service -> prepared-player handoff, not a session ledger. */
internal object AlarmStartupWakeLocks {
    const val EXTRA_TOKEN = "startupWakeToken"
    private val nextToken = AtomicInteger()
    private val locks = mutableMapOf<Int, PowerManager.WakeLock>()

    @Synchronized
    fun acquire(context: Context, timeoutMillis: Long = 30_000): Int {
        locks.entries.removeAll { !it.value.isHeld }
        val token = nextToken.incrementAndGet()
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daybreak:alarm-startup").apply {
                setReferenceCounted(false)
                acquire(timeoutMillis)
            }
        locks[token] = lock
        return token
    }

    @Synchronized
    fun release(token: Int) { locks.remove(token)?.let { if (it.isHeld) it.release() } }

    @Synchronized
    internal fun isHeld(token: Int): Boolean = locks[token]?.isHeld == true
}
