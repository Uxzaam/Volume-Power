package com.volumepower.plus

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }
}
