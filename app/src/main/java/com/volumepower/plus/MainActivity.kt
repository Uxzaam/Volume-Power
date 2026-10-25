package com.volumepower.plus

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var refreshing = false
    private lateinit var statusRow: LinearLayout
    private lateinit var swAdmin: MaterialSwitch
    private lateinit var swAdminLock: MaterialSwitch

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(col(R.color.ios_bg))
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(16), dp(6), dp(16), dp(14))
        scroll.addView(page, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        // Targeting SDK 35 draws edge to edge, so keep content clear of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        // ---- Title + status ----
        val title = TextView(this)
        title.text = "Volume Power"
        title.textSize = 30f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(col(R.color.ios_label))
        page.addView(title, lp(MATCH, WRAP).apply { topMargin = dp(4) })

        statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        page.addView(statusRow, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        // ---- Card 1: main ----
        val c1 = card()
        page.addView(c1, lp(MATCH, WRAP).apply { topMargin = dp(14) })

        c1.addSwitch("Volume key as power", Prefs.featureEnabled(this)) { v ->
            Prefs.setFeatureEnabled(this, v)
            if (v) {
                SelfHeal.healIfWanted(this)
                VolumePowerForegroundService.start(this)
            } else {
                VolumePowerForegroundService.stop(this)
            }
            refresh()
        }
        c1.addSegmented(
            "Trigger key", listOf("Vol up", "Vol down"),
            if (Prefs.triggerKey(this) == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else 0, 170
        ) { i ->
            Prefs.setTriggerKey(this, if (i == 1) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
        }
        c1.addSwitch("Start on boot", Prefs.startOnBoot(this)) { Prefs.setStartOnBoot(this, it) }
        c1.addSwitch("Re-enable accessibility", Prefs.autoHeal(this)) { Prefs.setAutoHeal(this, it) }
        c1.addSwitch("Silent audio keep-alive", Prefs.keepAlive(this)) { v ->
            Prefs.setKeepAlive(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }
        c1.addSwitch("Use Shizuku for key", Prefs.shizukuKeys(this)) { v ->
            Prefs.setShizukuKeys(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Card 2: admin, look, notification ----
        val admin = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val c2 = card()
        page.addView(c2, lp(MATCH, WRAP).apply { topMargin = dp(14) })

        swAdmin = c2.addSwitch("Device admin", dpm.isAdminActive(admin)) { on ->
            if (on) {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                )
            } else {
                dpm.removeActiveAdmin(admin)
                refresh()
            }
        }
        swAdminLock = c2.addSwitch("Lock with admin (PIN after)", Prefs.useAdminLock(this)) {
            Prefs.setUseAdminLock(this, it)
        }
        c2.addSegmented(
            "Appearance", listOf("Auto", "Day", "Night"),
            when (Prefs.themeMode(this)) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }, 200
        ) { i ->
            val mode = when (i) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            Prefs.setThemeMode(this, mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        c2.addSegmented(
            "Notification", listOf("Hidden", "Low", "Normal"), Prefs.notifPriority(this), 200
        ) { i ->
            Prefs.setNotifPriority(this, i)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Setup buttons ----
        val cap = TextView(this)
        cap.text = "SETUP"
        cap.textSize = 12f
        cap.setTextColor(col(R.color.ios_secondary))
        page.addView(cap, lp(WRAP, WRAP).apply { topMargin = dp(16); marginStart = dp(16) })

        val c3 = card()
        c3.setPadding(dp(6), dp(6), dp(6), dp(6))
        page.addView(c3, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        c3.addButtons(
            pill("Get Shizuku") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            },
            pill("Open") {
                val i = packageManager.getLaunchIntentForPackage(ShizukuBridge.PKG)
                if (i != null) startActivity(i)
                else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
            },
            pill("Allow") {
                if (!ShizukuBridge.running()) {
                    Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show()
                } else {
                    ShizukuBridge.requestPermission()
                }
            },
            pill("Grant all") { grantAll() }
        )
        c3.addButtons(
            pill("Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            pill("Battery") { openBattery() },
            pill("ADB cmd") { showAdbDialog() }
        )
    }

    override fun onResume() {
        super.onResume()
        SelfHeal.healIfWanted(this)
        if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        refresh()
        showLastCrash()
    }

    // ------------------------------------------------------------------ actions

    private fun refresh() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val adminActive = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        val battOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        statusRow.removeAllViews()
        addStatus("Access", SelfHeal.isServiceEnabled(this))
        addStatus("Secure", SelfHeal.hasSecurePermission(this))
        addStatus("Shizuku", ShizukuBridge.granted())
        addStatus("Admin", adminActive)
        addStatus("Battery", battOk)

        refreshing = true
        swAdmin.isChecked = adminActive
        swAdminLock.isEnabled = adminActive
        (swAdminLock.parent as View).alpha = if (adminActive) 1f else 0.4f
        refreshing = false
    }

    private fun openBattery() {
        val ok = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (ok) {
            Toast.makeText(this, "Battery already unrestricted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun grantAll() {
        if (!ShizukuBridge.granted()) {
            Toast.makeText(this, "Start Shizuku and tap Allow first", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Working...", Toast.LENGTH_SHORT).show()
        Thread {
            val pkg = packageName
            val steps = mutableListOf(
                "Secure settings" to arrayOf("pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS"),
                "Restricted settings" to arrayOf("cmd", "appops", "set", pkg, "ACCESS_RESTRICTED_SETTINGS", "allow"),
                "Run in background" to arrayOf("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "allow"),
                "Battery whitelist" to arrayOf("cmd", "deviceidle", "whitelist", "+$pkg")
            )
            if (Build.VERSION.SDK_INT >= 33) {
                steps.add(1, "Notifications" to arrayOf("pm", "grant", pkg, "android.permission.POST_NOTIFICATIONS"))
            }
            val sb = StringBuilder()
            for ((name, cmd) in steps) {
                sb.append(name).append(": ").append(ShizukuBridge.runForOutput(*cmd)).append('\n')
            }
            SelfHeal.ensureEnabled(this)
            runOnUiThread {
                refresh()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permissions")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private fun showAdbDialog() {
        val cmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        MaterialAlertDialogBuilder(this)
            .setTitle("Manual ADB")
            .setMessage(
                "Only needed without Shizuku. Turn on Wireless debugging, connect from a PC or Termux " +
                    "(pkg install android-tools), then run:\n\n$cmd"
            )
            .setPositiveButton("Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("command", cmd))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Developer options") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLastCrash() {
        val f = java.io.File(filesDir, "last_crash.txt")
        if (!f.exists()) return
        val t = try { f.readText() } catch (_: Exception) { return }
        f.delete()
        MaterialAlertDialogBuilder(this)
            .setTitle("Last crash")
            .setMessage(t.take(2500))
            .setPositiveButton("Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("crash", t))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ------------------------------------------------------------------ UI helpers

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = GradientDrawable().apply {
            setColor(col(R.color.ios_card))
            cornerRadius = dp(12).toFloat()
        }
        return c
    }

    private fun rowContainer(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(46)
        r.setPadding(dp(16), dp(2), dp(12), dp(2))
        return r
    }

    private fun labelView(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 16f
        t.setTextColor(col(R.color.ios_label))
        return t
    }

    private fun LinearLayout.addRow(row: View) {
        if (childCount > 0) {
            val line = View(this@MainActivity)
            line.setBackgroundColor(col(R.color.ios_sep))
            addView(line, lp(MATCH, 1).apply { marginStart = dp(16) })
        }
        addView(row, lp(MATCH, WRAP))
    }

    private fun LinearLayout.addSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val row = rowContainer()
        row.addView(labelView(label), lp(0, WRAP, 1f))
        val sw = MaterialSwitch(this@MainActivity)
        sw.thumbIconDrawable = null
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.ios_switch_track)
        sw.trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> if (!refreshing) onChange(v) }
        row.addView(sw, lp(WRAP, WRAP))
        addRow(row)
        return sw
    }

    private fun LinearLayout.addSegmented(
        label: String, items: List<String>, selected: Int, widthDp: Int, onSelect: (Int) -> Unit
    ) {
        val row = rowContainer()
        row.addView(labelView(label), lp(0, WRAP, 1f))
        row.addView(Segmented(this@MainActivity, items, selected, onSelect), lp(dp(widthDp), dp(32)))
        addRow(row)
    }

    private fun pill(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setTextColor(col(R.color.ios_blue))
        val shape = GradientDrawable().apply {
            setColor(col(R.color.ios_btn))
            cornerRadius = dp(10).toFloat()
        }
        t.background = RippleDrawable(ColorStateList.valueOf(0x22808080), shape, null)
        t.isClickable = true
        t.isFocusable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun LinearLayout.addButtons(vararg buttons: View) {
        val row = LinearLayout(this@MainActivity)
        row.orientation = LinearLayout.HORIZONTAL
        for (b in buttons) {
            row.addView(b, lp(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        addView(row, lp(MATCH, WRAP))
    }

    private fun addStatus(label: String, ok: Boolean) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.gravity = Gravity.CENTER_VERTICAL
        val dot = View(this)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(if (ok) R.color.ios_green else R.color.ios_red))
        }
        item.addView(dot, lp(dp(7), dp(7)).apply { marginEnd = dp(4) })
        val t = TextView(this)
        t.text = label
        t.textSize = 11f
        t.maxLines = 1
        t.setTextColor(col(R.color.ios_secondary))
        item.addView(t, lp(WRAP, WRAP))
        statusRow.addView(item, lp(0, WRAP, 1f))
    }
}
