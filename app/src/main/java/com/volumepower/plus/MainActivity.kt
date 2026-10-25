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
import android.widget.FrameLayout
import android.widget.ImageView
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
    private fun dpF(v: Float) = v * resources.displayMetrics.density
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(col(R.color.ios_bg))
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }
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

        // ---- Minimal Apple Style Header + Status Bar ----
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appBadge = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
        }
        headerRow.addView(appBadge, lp(dp(44), dp(44)).apply { marginEnd = dp(12) })

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "Volume Power"
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(col(R.color.ios_label))
            letterSpacing = -0.02f
        }
        val subtitle = TextView(this).apply {
            text = "Minimal hardware key control"
            textSize = 13f
            setTextColor(col(R.color.ios_secondary))
        }
        titleCol.addView(title, lp(WRAP, WRAP))
        titleCol.addView(subtitle, lp(WRAP, WRAP).apply { topMargin = dp(1) })
        headerRow.addView(titleCol, lp(0, WRAP, 1f))
        page.addView(headerRow, lp(MATCH, WRAP).apply { topMargin = dp(4) })

        statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(statusRow, lp(MATCH, WRAP).apply { topMargin = dp(12) })

        // ---- Section 1: Controls ----
        page.addView(sectionHeader("CONTROLS"), lp(WRAP, WRAP).apply { topMargin = dp(16); marginStart = dp(14) })
        val c1 = card()
        page.addView(c1, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        c1.addSwitch(
            R.drawable.ic_power, R.color.ios_blue,
            "Volume key as power", Prefs.featureEnabled(this)
        ) { v ->
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
            R.drawable.ic_volume, R.color.ios_indigo,
            "Trigger key", listOf("Vol up", "Vol down"),
            if (Prefs.triggerKey(this) == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else 0, 156
        ) { i ->
            Prefs.setTriggerKey(this, if (i == 1) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
        }
        c1.addSwitch(
            R.drawable.ic_boot, R.color.ios_orange,
            "Start on boot", Prefs.startOnBoot(this)
        ) { Prefs.setStartOnBoot(this, it) }
        c1.addSwitch(
            R.drawable.ic_heal, R.color.ios_green,
            "Re-enable accessibility", Prefs.autoHeal(this)
        ) { Prefs.setAutoHeal(this, it) }
        c1.addSwitch(
            R.drawable.ic_wave, R.color.ios_purple,
            "Silent audio keep-alive", Prefs.keepAlive(this)
        ) { v ->
            Prefs.setKeepAlive(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }
        c1.addSwitch(
            R.drawable.ic_terminal, R.color.ios_teal,
            "Use Shizuku for key", Prefs.shizukuKeys(this)
        ) { v ->
            Prefs.setShizukuKeys(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Section 2: Security & Appearance ----
        page.addView(sectionHeader("SECURITY & APPEARANCE"), lp(WRAP, WRAP).apply { topMargin = dp(18); marginStart = dp(14) })
        val admin = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val c2 = card()
        page.addView(c2, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        swAdmin = c2.addSwitch(
            R.drawable.ic_lock, R.color.ios_red,
            "Device admin", dpm.isAdminActive(admin)
        ) { on ->
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
        swAdminLock = c2.addSwitch(
            R.drawable.ic_key, R.color.ios_orange,
            "Lock with admin (PIN after)", Prefs.useAdminLock(this)
        ) {
            Prefs.setUseAdminLock(this, it)
        }
        c2.addSegmented(
            R.drawable.ic_theme, R.color.ios_indigo,
            "Appearance", listOf("Auto", "Day", "Night"),
            when (Prefs.themeMode(this)) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }, 182
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
            R.drawable.ic_bell, R.color.ios_blue,
            "Notification", listOf("Hidden", "Low", "Normal"), Prefs.notifPriority(this), 182
        ) { i ->
            Prefs.setNotifPriority(this, i)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Section 3: Quick Setup ----
        page.addView(sectionHeader("QUICK SETUP"), lp(WRAP, WRAP).apply { topMargin = dp(18); marginStart = dp(14) })
        val c3 = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        page.addView(c3, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        c3.addButtons(
            pill(R.drawable.ic_download, "Get Shizuku") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            },
            pill(R.drawable.ic_open, "Open") {
                val i = packageManager.getLaunchIntentForPackage(ShizukuBridge.PKG)
                if (i != null) startActivity(i)
                else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
            },
            pill(R.drawable.ic_check, "Allow") {
                if (!ShizukuBridge.running()) {
                    Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show()
                } else {
                    ShizukuBridge.requestPermission()
                }
            }
        )
        c3.addButtons(
            pill(R.drawable.ic_heal, "Grant all") { grantAll() },
            pill(R.drawable.ic_accessibility, "Accessibility") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            pill(R.drawable.ic_battery, "Battery") { openBattery() }
        )

        // ---- Section 4: Creator Details ----
        page.addView(sectionHeader("CREATOR"), lp(WRAP, WRAP).apply { topMargin = dp(18); marginStart = dp(14) })
        val creatorCard = card()
        page.addView(creatorCard, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        creatorCard.addInfoRow(
            iconRes = R.drawable.ic_person,
            badgeColorRes = R.color.ios_indigo,
            title = "Gulam Azam Faridi 🇧🇩",
            subtitle = "Developer & Creator"
        )
        creatorCard.addActionRow(
            iconRes = R.drawable.ic_telegram,
            badgeColorRes = R.color.ios_telegram,
            title = "Telegram",
            valueText = "t.me/GulamAzamFaridi",
            onClick = {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GulamAzamFaridi")))
            },
            onLongClick = {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("telegram", "https://t.me/GulamAzamFaridi"))
                Toast.makeText(this, "Telegram link copied", Toast.LENGTH_SHORT).show()
            }
        )

        // ---- Section 5: Bottom Advanced Card (ADB Code & Instructions) ----
        page.addView(sectionHeader("ADVANCED"), lp(WRAP, WRAP).apply { topMargin = dp(18); marginStart = dp(14) })
        val advCard = card().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        page.addView(advCard, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        val advHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        advHeader.addView(iconBadge(R.drawable.ic_terminal, R.color.ios_gray), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        val advTitleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advTitleCol.addView(TextView(this).apply {
            text = "Manual ADB Setup"
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(col(R.color.ios_label))
        })
        advTitleCol.addView(TextView(this).apply {
            text = "Only needed without Shizuku"
            textSize = 12f
            setTextColor(col(R.color.ios_secondary))
        })
        advHeader.addView(advTitleCol, lp(0, WRAP, 1f))
        advCard.addView(advHeader, lp(MATCH, WRAP))

        val adbCmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

        val advInstructions = TextView(this).apply {
            text = "1. Open Developer options and enable USB or Wireless debugging.\n" +
                "2. Connect from a PC or Termux (pkg install android-tools).\n" +
                "3. Run the command below once to enable self-healing permissions:"
            textSize = 13f
            setLineSpacing(dpF(2f), 1f)
            setTextColor(col(R.color.ios_secondary))
        }
        advCard.addView(advInstructions, lp(MATCH, WRAP).apply { topMargin = dp(12) })

        val codeBox = TextView(this).apply {
            text = adbCmd
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(col(R.color.ios_label))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(col(R.color.ios_code_bg))
                cornerRadius = dpF(10f)
                setStroke(1, col(R.color.ios_sep))
            }
        }
        advCard.addView(codeBox, lp(MATCH, WRAP).apply { topMargin = dp(10) })

        val advBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val copyBtn = pill(R.drawable.ic_copy, "Copy Command") {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("command", adbCmd))
            Toast.makeText(this, "ADB command copied", Toast.LENGTH_SHORT).show()
        }
        val devBtn = pill(R.drawable.ic_code, "Developer Options") {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }
        advBtnRow.addView(copyBtn, lp(0, dp(40), 1f).apply { marginEnd = dp(4) })
        advBtnRow.addView(devBtn, lp(0, dp(40), 1f).apply { marginStart = dp(4) })
        advCard.addView(advBtnRow, lp(MATCH, WRAP).apply { topMargin = dp(12) })
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

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
            setTextColor(col(R.color.ios_secondary))
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(col(R.color.ios_card))
                cornerRadius = dpF(14f)
                setStroke(1, col(R.color.ios_card_stroke))
            }
        }
    }

    private fun iconBadge(iconRes: Int, colorRes: Int): FrameLayout {
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(col(colorRes))
                cornerRadius = dpF(7.5f)
            }
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        box.addView(iv, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        return box
    }

    private fun rowContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
            setPadding(dp(14), dp(4), dp(12), dp(4))
        }
    }

    private fun labelView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15.5f
            setTextColor(col(R.color.ios_label))
        }
    }

    private fun LinearLayout.addRow(row: View) {
        if (childCount > 0) {
            val line = View(this@MainActivity)
            line.setBackgroundColor(col(R.color.ios_sep))
            addView(line, lp(MATCH, 1).apply { marginStart = dp(56) })
        }
        addView(row, lp(MATCH, WRAP))
    }

    private fun LinearLayout.addSwitch(
        iconRes: Int,
        badgeColorRes: Int,
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): MaterialSwitch {
        val row = rowContainer()
        row.addView(iconBadge(iconRes, badgeColorRes), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        val sw = MaterialSwitch(this@MainActivity).apply {
            thumbIconDrawable = null
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.ios_switch_track)
            trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            isChecked = checked
            setOnCheckedChangeListener { _, v -> if (!refreshing) onChange(v) }
        }
        row.addView(sw, lp(WRAP, WRAP))
        addRow(row)
        return sw
    }

    private fun LinearLayout.addSegmented(
        iconRes: Int,
        badgeColorRes: Int,
        label: String,
        items: List<String>,
        selected: Int,
        widthDp: Int,
        onSelect: (Int) -> Unit
    ) {
        val row = rowContainer()
        row.addView(iconBadge(iconRes, badgeColorRes), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        row.addView(Segmented(this@MainActivity, items, selected, onSelect), lp(dp(widthDp), dp(32)))
        addRow(row)
    }

    private fun LinearLayout.addInfoRow(
        iconRes: Int,
        badgeColorRes: Int,
        title: String,
        subtitle: String
    ) {
        val row = rowContainer()
        row.addView(iconBadge(iconRes, badgeColorRes), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        val textCol = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(col(R.color.ios_label))
        })
        textCol.addView(TextView(this@MainActivity).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(col(R.color.ios_secondary))
        })
        row.addView(textCol, lp(0, WRAP, 1f))
        addRow(row)
    }

    private fun LinearLayout.addActionRow(
        iconRes: Int,
        badgeColorRes: Int,
        title: String,
        valueText: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ) {
        val row = rowContainer().apply {
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpF(14f)
            }
            background = RippleDrawable(ColorStateList.valueOf(0x1F808080), null, mask)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        }
        row.addView(iconBadge(iconRes, badgeColorRes), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(title), lp(0, WRAP, 1f))
        row.addView(TextView(this@MainActivity).apply {
            text = valueText
            textSize = 14f
            setTextColor(col(R.color.ios_blue))
        }, lp(WRAP, WRAP).apply { marginEnd = dp(4) })
        row.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_chevron)
        }, lp(dp(18), dp(18)))
        addRow(row)
    }

    private fun pill(iconRes: Int, label: String, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(0), dp(8), dp(0))
            val shape = GradientDrawable().apply {
                setColor(col(R.color.ios_btn))
                cornerRadius = dpF(10f)
            }
            background = RippleDrawable(ColorStateList.valueOf(0x22808080), shape, null)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(col(R.color.ios_blue))
        }
        box.addView(iv, lp(dp(15), dp(15)).apply { marginEnd = dp(5) })
        val t = TextView(this).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            setTextColor(col(R.color.ios_blue))
        }
        box.addView(t, lp(WRAP, WRAP))
        return box
    }

    private fun LinearLayout.addButtons(vararg buttons: View) {
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (b in buttons) {
            row.addView(b, lp(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        addView(row, lp(MATCH, WRAP))
    }

    private fun addStatus(label: String, ok: Boolean) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                setColor(col(if (ok) R.color.ios_pill_ok_bg else R.color.ios_pill_err_bg))
                cornerRadius = dpF(99f)
            }
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col(if (ok) R.color.ios_green else R.color.ios_red))
            }
        }
        item.addView(dot, lp(dp(6), dp(6)).apply { marginEnd = dp(4) })
        val t = TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            setTextColor(col(R.color.ios_label))
        }
        item.addView(t, lp(WRAP, WRAP))
        statusRow.addView(item, lp(0, WRAP, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
    }
}
