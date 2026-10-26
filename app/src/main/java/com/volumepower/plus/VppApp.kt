package com.volumepower.plus

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

class VppApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The crash screen runs in its own process; keep it free of any other start-up work.
        if (Application.getProcessName().endsWith(":crash")) return
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            var shown = false
            try {
                val text = "${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                    "Thread: ${t.name}\n\n${Log.getStackTraceString(e)}".take(15000)
                try { File(filesDir, "last_crash.txt").writeText(text) } catch (_: Throwable) {}
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("trace", text)
                )
                shown = true
                Thread.sleep(400)
            } catch (_: Throwable) {
            }
            if (shown) {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } else {
                old?.uncaughtException(t, e)
            }
        }
    }
}
