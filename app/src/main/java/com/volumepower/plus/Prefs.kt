package com.volumepower.plus

import android.content.Context
import android.view.KeyEvent

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("volume_power_plus", Context.MODE_PRIVATE)

    fun featureEnabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setFeatureEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun triggerKey(c: Context) = sp(c).getInt("trigger", KeyEvent.KEYCODE_VOLUME_UP)
    fun setTriggerKey(c: Context, v: Int) = sp(c).edit().putInt("trigger", v).apply()

    fun startOnBoot(c: Context) = sp(c).getBoolean("boot", true)
    fun setStartOnBoot(c: Context, v: Boolean) = sp(c).edit().putBoolean("boot", v).apply()

    fun autoHeal(c: Context) = sp(c).getBoolean("heal", true)
    fun setAutoHeal(c: Context, v: Boolean) = sp(c).edit().putBoolean("heal", v).apply()

    fun keepAlive(c: Context) = sp(c).getBoolean("keepalive", true)
    fun setKeepAlive(c: Context, v: Boolean) = sp(c).edit().putBoolean("keepalive", v).apply()

    fun useAdminLock(c: Context) = sp(c).getBoolean("adminlock", false)
    fun setUseAdminLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("adminlock", v).apply()

    fun shizukuKeys(c: Context) = sp(c).getBoolean("shizuku", true)
    fun setShizukuKeys(c: Context, v: Boolean) = sp(c).edit().putBoolean("shizuku", v).apply()

    /** Wake the screen during calls even when the proximity sensor is stuck "near". */
    fun callFix(c: Context) = sp(c).getBoolean("callfix", true)
    fun setCallFix(c: Context, v: Boolean) = sp(c).edit().putBoolean("callfix", v).apply()
}
