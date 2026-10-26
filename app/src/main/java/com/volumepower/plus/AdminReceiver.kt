package com.volumepower.plus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: Context, intent: Intent) {
        Prefs.setUseAdminLock(context, false)
        super.onDisabled(context, intent)
    }
}
