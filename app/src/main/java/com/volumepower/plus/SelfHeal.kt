package com.volumepower.plus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Re-enables this app's accessibility service when the system switches it off.
 * Needs WRITE_SECURE_SETTINGS, granted once over (wireless) ADB.
 */
object SelfHeal {
    private fun component(c: Context) =
        ComponentName(c, VolumePowerAccessibilityService::class.java).flattenToString()

    fun hasSecurePermission(c: Context) =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isServiceEnabled(c: Context): Boolean {
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = Settings.Secure.getInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        return on && list.split(':').any { it.equals(component(c), ignoreCase = true) }
    }

    /** Returns true if the service is (now) enabled. */
    fun ensureEnabled(c: Context): Boolean {
        if (isServiceEnabled(c)) return true
        if (!hasSecurePermission(c)) return false
        return try {
            val cr = c.contentResolver
            val comp = component(c)
            val list = (Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').filter { it.isNotBlank() }.toMutableList()
            if (list.none { it.equals(comp, ignoreCase = true) }) {
                list.add(comp)
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun healIfWanted(c: Context) {
        if (Prefs.featureEnabled(c) && Prefs.autoHeal(c)) ensureEnabled(c)
    }
}
