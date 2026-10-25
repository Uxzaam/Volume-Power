package com.volumepower.plus

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display

object ScreenWaker {
    @Volatile private var last = 0L
    private val main = Handler(Looper.getMainLooper())

    private fun debounced(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 600) return true
        last = now
        return false
    }

    /** True while a phone or VoIP call is active. */
    fun inCall(c: Context): Boolean {
        val m = c.getSystemService(AudioManager::class.java).mode
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun displayOff(c: Context): Boolean {
        val d = c.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return d != null && d.state == Display.STATE_OFF
    }

    /**
     * True when the screen needs waking. Normally that is "not interactive". During a call the
     * proximity sensor can switch the display off while Android still reports the phone as awake
     * (isInteractive stays true), so there the real display state decides.
     */
    fun screenOff(c: Context): Boolean {
        if (!c.getSystemService(PowerManager::class.java).isInteractive) return true
        return Prefs.callFix(c) && inCall(c) && displayOff(c)
    }

    /** Turn the display on (like pressing Power while the screen is off). */
    fun wake(c: Context) {
        if (debounced()) return
        if (Prefs.callFix(c) && inCall(c) && ShizukuBridge.granted()) {
            callWake(c)
            return
        }
        if (ShizukuBridge.granted() && ShizukuBridge.run("input", "keyevent", "KEYCODE_WAKEUP")) return
        wakeWithLock(c)
    }

    /**
     * In a call, KEYCODE_WAKEUP is not enough: the proximity sensor switches the display straight
     * off again. A real Power key press wakes the screen and makes Android ignore the proximity
     * sensor until it changes, which is what we need when it is stuck on "near".
     * If the phone is still "awake" (display off only because of proximity) it is put to sleep
     * first, so that the Power press counts as a wake-up and never as a hang-up.
     */
    private fun callWake(c: Context) {
        val pm = c.getSystemService(PowerManager::class.java)
        if (pm.isInteractive) {
            ShizukuBridge.run("input", "keyevent", "KEYCODE_SLEEP")
            main.postDelayed({
                if (!pm.isInteractive) ShizukuBridge.run("input", "keyevent", "KEYCODE_POWER")
            }, 450)
        } else {
            ShizukuBridge.run("input", "keyevent", "KEYCODE_POWER")
        }
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
