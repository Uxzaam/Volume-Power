package com.volumepower.plus

import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** Thin wrapper around Shizuku (ADB-level "shell" access, no root needed). */
object ShizukuBridge {
    const val PKG = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4711
    private val pool = Executors.newSingleThreadExecutor()

    fun installed(c: Context) = try {
        c.packageManager.getPackageInfo(PKG, 0); true
    } catch (_: Exception) {
        false
    }

    fun running() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun granted() = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        try { if (running()) Shizuku.requestPermission(REQUEST_CODE) } catch (_: Throwable) {}
    }

    /** Starts a command in the Shizuku (shell) process. Shizuku keeps this method non-public, so use reflection. */
    fun newProcess(cmd: Array<String>): Process? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        m.invoke(null, cmd, null, null) as? Process
    } catch (_: Throwable) {
        null
    }

    /** Fire and forget. Returns false if the command could not be started. */
    fun run(vararg cmd: String): Boolean {
        if (!granted()) return false
        val p = newProcess(arrayOf(*cmd)) ?: return false
        pool.execute { try { p.waitFor() } catch (_: Exception) {} }
        return true
    }

    /** Blocking, so call it off the main thread. Returns "ok" or a short error. */
    fun runForOutput(vararg cmd: String): String {
        val p = newProcess(arrayOf(*cmd)) ?: return "could not start"
        return try {
            val out = p.inputStream.bufferedReader().readText().trim()
            val err = p.errorStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            if (code == 0) "ok" else "failed ($code) ${err.ifBlank { out }}".take(120)
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}

/**
 * Watches the real hardware volume buttons through `getevent` (runs as shell via Shizuku).
 * Works with the screen off and whatever app is playing media.
 */
class ShizukuKeyReader(private val onKey: (Int) -> Unit) {
    @Volatile private var running = false
    private var gen = 0
    private var proc: Process? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val g = ++gen
        Thread { loop(g) }.apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        running = false
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
    }

    private fun active(g: Int) = running && g == gen

    private fun loop(g: Int) {
        while (active(g)) {
            if (!ShizukuBridge.granted()) { sleep(4000); continue }
            val p = ShizukuBridge.newProcess(arrayOf("getevent", "-l"))
            if (p == null) { sleep(4000); continue }
            proc = p
            try {
                p.inputStream.bufferedReader().use { r ->
                    while (active(g)) {
                        val line = r.readLine() ?: break
                        if (!line.contains("DOWN")) continue
                        if (line.contains("KEY_VOLUMEUP")) onKey(KeyEvent.KEYCODE_VOLUME_UP)
                        else if (line.contains("KEY_VOLUMEDOWN")) onKey(KeyEvent.KEYCODE_VOLUME_DOWN)
                    }
                }
            } catch (_: Exception) {
            }
            try { p.destroy() } catch (_: Exception) {}
            sleep(2000)
        }
    }

    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}
}
