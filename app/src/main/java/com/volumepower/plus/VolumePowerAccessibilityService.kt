package com.volumepower.plus

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumePowerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        VolumePowerForegroundService.start(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.featureEnabled(this)) return false
        if (event.keyCode != Prefs.triggerKey(this)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handlePress()
        return true // consume key down and key up so the volume does not change
    }

    private fun handlePress() {
        if (ScreenWaker.screenOff(this)) ScreenWaker.wake(this) else lockScreen()
    }

    private fun lockScreen() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, AdminReceiver::class.java)
        val adminActive = dpm.isAdminActive(admin)
        if (Prefs.useAdminLock(this) && adminActive) {
            dpm.lockNow()
            return
        }
        // Behaves like a real power-button press (keeps fingerprint unlock).
        if (!performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) && adminActive) dpm.lockNow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
