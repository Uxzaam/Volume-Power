package com.volumepower.plus

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

object ScreenWaker {
    @Volatile private var last = 0L

    private fun debounced(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 600) return true
        last = now
        return false
    }

    /** Turn the display on (like pressing Power while the screen is off). */
    fun wake(c: Context) {
        if (debounced()) return
        if (ShizukuBridge.granted() && ShizukuBridge.run("input", "keyevent", "KEYCODE_WAKEUP")) return
        wakeWithLock(c)
    }

    /** Turn the display off / lock (used only when the accessibility service is not running). */
    fun sleep(c: Context) {
        if (debounced()) return
        ShizukuBridge.run("input", "keyevent", "KEYCODE_SLEEP")
    }

    @Suppress("DEPRECATION")
    private fun wakeWithLock(c: Context) {
        val pm = c.getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "VolumePowerPlus:wake"
        )
        wl.acquire(3000)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!pm.isInteractive) {
                try {
                    c.startActivity(Intent(c, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }, 400)
    }
}
