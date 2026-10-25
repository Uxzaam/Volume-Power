package com.volumepower.plus

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var statusList: LinearLayout
    private lateinit var btnAdmin: Button
    private lateinit var btnBattery: Button
    private lateinit var btnShizukuInstall: Button
    private lateinit var swAdminLock: MaterialSwitch

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Targeting SDK 35 draws edge to edge, so keep content clear of the system bars.
        val scroll = findViewById<View>(R.id.scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        statusList = findViewById(R.id.statusList)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnBattery = findViewById(R.id.btnBattery)
        btnShizukuInstall = findViewById(R.id.btnShizukuInstall)
        swAdminLock = findViewById(R.id.swAdminLock)

        // --- General ---
        findViewById<MaterialSwitch>(R.id.swEnable).apply {
            isChecked = Prefs.featureEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.setFeatureEnabled(this@MainActivity, v)
                if (v) {
                    SelfHeal.healIfWanted(this@MainActivity)
                    VolumePowerForegroundService.start(this@MainActivity)
                } else {
                    VolumePowerForegroundService.stop(this@MainActivity)
                }
                refresh()
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.toggleKey).apply {
            check(if (Prefs.triggerKey(this@MainActivity) == KeyEvent.KEYCODE_VOLUME_DOWN) R.id.btnKeyDown else R.id.btnKeyUp)
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) Prefs.setTriggerKey(
                    this@MainActivity,
                    if (id == R.id.btnKeyDown) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP
                )
            }
        }

        // --- Stability ---
        findViewById<MaterialSwitch>(R.id.swBoot).apply {
            isChecked = Prefs.startOnBoot(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setStartOnBoot(this@MainActivity, v) }
        }
        findViewById<MaterialSwitch>(R.id.swHeal).apply {
            isChecked = Prefs.autoHeal(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setAutoHeal(this@MainActivity, v) }
        }
        findViewById<MaterialSwitch>(R.id.swKeepAlive).apply {
            isChecked = Prefs.keepAlive(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.setKeepAlive(this@MainActivity, v)
                if (Prefs.featureEnabled(this@MainActivity)) VolumePowerForegroundService.start(this@MainActivity)
            }
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnBattery.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        // --- Appearance (day / night / system) ---
        findViewById<MaterialButtonToggleGroup>(R.id.toggleTheme).apply {
            check(
                when (Prefs.themeMode(this@MainActivity)) {
                    AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
                    AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
                    else -> R.id.btnThemeSystem
                }
            )
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                val mode = when (id) {
                    R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (mode != Prefs.themeMode(this@MainActivity)) {
                    Prefs.setThemeMode(this@MainActivity, mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }

        // --- Notification priority ---
        findViewById<MaterialButtonToggleGroup>(R.id.toggleNotif).apply {
            check(
                when (Prefs.notifPriority(this@MainActivity)) {
                    1 -> R.id.btnNotifLow
                    2 -> R.id.btnNotifNormal
                    else -> R.id.btnNotifHidden
                }
            )
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                val level = when (id) {
                    R.id.btnNotifLow -> 1
                    R.id.btnNotifNormal -> 2
                    else -> 0
                }
                if (level != Prefs.notifPriority(this@MainActivity)) {
                    Prefs.setNotifPriority(this@MainActivity, level)
                    if (Prefs.featureEnabled(this@MainActivity)) VolumePowerForegroundService.start(this@MainActivity)
                }
            }
        }

        // --- Device admin ---
        val admin = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        btnAdmin.setOnClickListener {
            if (dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin)
                refresh()
            } else {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                )
            }
        }
        swAdminLock.isChecked = Prefs.useAdminLock(this)
        swAdminLock.setOnCheckedChangeListener { _, v -> Prefs.setUseAdminLock(this, v) }

        // --- Shizuku ---
        findViewById<MaterialSwitch>(R.id.swShizuku).apply {
            isChecked = Prefs.shizukuKeys(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.setShizukuKeys(this@MainActivity, v)
                if (Prefs.featureEnabled(this@MainActivity)) VolumePowerForegroundService.start(this@MainActivity)
            }
        }
        btnShizukuInstall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
        findViewById<Button>(R.id.btnShizukuOpen).setOnClickListener {
            val i = packageManager.getLaunchIntentForPackage(ShizukuBridge.PKG)
            if (i != null) startActivity(i)
            else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnShizukuAllow).setOnClickListener {
            if (!ShizukuBridge.running()) {
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show()
            } else {
                ShizukuBridge.requestPermission()
            }
        }
        val txtGrant = findViewById<TextView>(R.id.txtGrant)
        findViewById<Button>(R.id.btnGrantAll).setOnClickListener {
            if (!ShizukuBridge.granted()) {
                Toast.makeText(this, "Start Shizuku and tap Allow first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            txtGrant.text = "Working..."
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
                    txtGrant.text = sb.toString().trim()
                    refresh()
                }
            }.start()
        }

        // --- Wireless ADB ---
        val cmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        findViewById<TextView>(R.id.txtCommand).text = cmd
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("command", cmd))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnDev).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SelfHeal.healIfWanted(this)
        if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        refresh()
        showLastCrash()
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

    private fun refresh() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val adminActive = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        val battOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        statusList.removeAllViews()
        addStatus("Accessibility service", SelfHeal.isServiceEnabled(this))
        addStatus("Special permission", SelfHeal.hasSecurePermission(this))
        addStatus("Shizuku access", ShizukuBridge.granted())
        addStatus("Device admin", adminActive)
        addStatus("Battery unrestricted", battOk)

        btnAdmin.text = if (adminActive) "Remove device admin" else "Make device admin"
        swAdminLock.visibility = if (adminActive) View.VISIBLE else View.GONE
        btnBattery.visibility = if (battOk) View.GONE else View.VISIBLE
        btnShizukuInstall.visibility = if (ShizukuBridge.installed(this)) View.GONE else View.VISIBLE
    }

    private fun addStatus(label: String, ok: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = if (ok) "On" else "Off"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(
                MaterialColors.getColor(
                    statusList,
                    if (ok) androidx.appcompat.R.attr.colorPrimary else androidx.appcompat.R.attr.colorError
                )
            )
        })
        statusList.addView(row)
    }
}
