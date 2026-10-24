package com.volumepower.plus

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class VppApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode(this))
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
