package com.volumepower.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.startOnBoot(context) || !Prefs.featureEnabled(context)) return
        SelfHeal.healIfWanted(context)
        VolumePowerForegroundService.start(context)
    }
}
