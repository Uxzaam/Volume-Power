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
import android.graphics.drawable.ColorDrawable
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

    companion object {
        private const val TELEGRAM_URL = "https://t.me/GulamAzamFaridi"
        private const val CREATOR_NAME = "Gulam Azam Faridi\uD83C\uDDE7\uD83C\uDDE9" // + Bangladesh flag
    }

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
        page.setPadding(dp(16), dp(8), dp(16), dp(28))
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

        // ---- Header: app icon, title, version ----
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_app_tile)
        header.addView(logo, lp(dp(56), dp(56)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "Volume Power"
        title.textSize = 30f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(col(R.color.ios_label))
        titles.addView(title, lp(WRAP, WRAP))
        val sub = TextView(this)
        sub.text = if (version.isEmpty()) "Plus" else "Plus  \u00B7  v$version"
        sub.textSize = 14f
        sub.setTextColor(col(R.color.ios_secondary))
        titles.addView(sub, lp(WRAP, WRAP))
        header.addView(titles, lp(0, WRAP, 1f).apply { marginStart = dp(14) })
        page.addView(header, lp(MATCH, WRAP).apply { topMargin = dp(8) })

        // ---- Status ----
        val statusCard = card()
        statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusRow.setPadding(dp(8), dp(14), dp(8), dp(14))
        statusCard.addView(statusRow, lp(MATCH, WRAP))
        page.addView(statusCard, lp(MATCH, WRAP).apply { topMargin = dp(20) })

        // ---- Card 1: main ----
        val c1 = card()
        page.addView(c1, lp(MATCH, WRAP).apply { topMargin = dp(18) })

        c1.addSwitch("Volume key as power", R.drawable.ic_power, R.color.tile_red, Prefs.featureEnabled(this)) { v ->
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
            "Trigger key", R.drawable.ic_volume, R.color.tile_blue,
            listOf("Vol up", "Vol down"),
            if (Prefs.triggerKey(this) == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else 0, 150
        ) { i ->
            Prefs.setTriggerKey(this, if (i == 1) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
        }
        c1.addSwitch("Start on boot", R.drawable.ic_play, R.color.tile_green, Prefs.startOnBoot(this)) {
            Prefs.setStartOnBoot(this, it)
        }
        c1.addSwitch("Re-enable accessibility", R.drawable.ic_refresh, R.color.tile_indigo, Prefs.autoHeal(this)) {
            Prefs.setAutoHeal(this, it)
        }
        c1.addSwitch("Silent audio keep-alive", R.drawable.ic_wave, R.color.tile_pink, Prefs.keepAlive(this)) { v ->
            Prefs.setKeepAlive(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }
        c1.addSwitch("Use Shizuku for key", R.drawable.ic_terminal, R.color.tile_teal, Prefs.shizukuKeys(this)) { v ->
            Prefs.setShizukuKeys(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Card 2: admin, look, notification ----
        val admin = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val c2 = card()
        page.addView(c2, lp(MATCH, WRAP).apply { topMargin = dp(18) })

        swAdmin = c2.addSwitch("Device admin", R.drawable.ic_shield, R.color.tile_orange, dpm.isAdminActive(admin)) { on ->
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
            "Lock with admin (PIN after)", R.drawable.ic_lock, R.color.tile_gray, Prefs.useAdminLock(this)
        ) {
            Prefs.setUseAdminLock(this, it)
        }
        c2.addSegmented(
            "Appearance", R.drawable.ic_contrast, R.color.tile_purple,
            listOf("Auto", "Day", "Night"),
            when (Prefs.themeMode(this)) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }, 164
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
            "Notification", R.drawable.ic_bell, R.color.tile_red,
            listOf("Hidden", "Low", "Normal"), Prefs.notifPriority(this), 164
        ) { i ->
            Prefs.setNotifPriority(this, i)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }

        // ---- Setup ----
        page.addView(caption("SETUP"), lp(WRAP, WRAP).apply { topMargin = dp(22); marginStart = dp(16) })
        val c3 = card()
        page.addView(c3, lp(MATCH, WRAP).apply { topMargin = dp(7) })

        c3.addNav("Get Shizuku", R.drawable.ic_download, R.color.tile_blue) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
        c3.addNav("Open Shizuku", R.drawable.ic_open, R.color.tile_teal) {
            val i = packageManager.getLaunchIntentForPackage(ShizukuBridge.PKG)
            if (i != null) startActivity(i)
            else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
        }
        c3.addNav("Allow this app", R.drawable.ic_check, R.color.tile_green) {
            if (!ShizukuBridge.running()) {
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show()
            } else {
                ShizukuBridge.requestPermission()
            }
        }
        c3.addNav("Grant all permissions", R.drawable.ic_shield_check, R.color.tile_orange) { grantAll() }
        c3.addNav("Accessibility settings", R.drawable.ic_accessibility, R.color.tile_indigo) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c3.addNav("Battery", R.drawable.ic_battery, R.color.tile_green) { openBattery() }

        // ---- Advanced (manual ADB) ----
        page.addView(caption("ADVANCED"), lp(WRAP, WRAP).apply { topMargin = dp(22); marginStart = dp(16) })
        page.addView(advancedCard(), lp(MATCH, WRAP).apply { topMargin = dp(7) })

        // ---- Creator ----
        page.addView(caption("CREATOR"), lp(WRAP, WRAP).apply { topMargin = dp(22); marginStart = dp(16) })
        page.addView(creatorCard(), lp(MATCH, WRAP).apply { topMargin = dp(7) })
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

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open $TELEGRAM_URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyText(label: String, text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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

    // ------------------------------------------------------------------ cards

    /** Manual ADB: instructions and copyable commands, for use without Shizuku. */
    private fun advancedCard(): LinearLayout {
        val pair = "adb pair localhost:<pairing-port>"
        val connect = "adb connect localhost:<port>"
        val grant = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

        val c = card()
        c.setPadding(dp(16), dp(14), dp(16), dp(16))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(iconTile(R.drawable.ic_terminal, R.color.tile_gray), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        val ht = LinearLayout(this)
        ht.orientation = LinearLayout.VERTICAL
        val h1 = TextView(this)
        h1.text = "Manual ADB"
        h1.textSize = 16f
        h1.typeface = Typeface.DEFAULT_BOLD
        h1.setTextColor(col(R.color.ios_label))
        ht.addView(h1, lp(WRAP, WRAP))
        val h2 = TextView(this)
        h2.text = "Only needed without Shizuku"
        h2.textSize = 12f
        h2.setTextColor(col(R.color.ios_secondary))
        ht.addView(h2, lp(WRAP, WRAP))
        head.addView(ht, lp(0, WRAP, 1f))
        c.addView(head, lp(MATCH, WRAP))

        c.addView(step(1, "Turn on Developer options \u203A Wireless debugging (Wi-Fi required)."),
            lp(MATCH, WRAP).apply { topMargin = dp(16) })
        c.addView(step(2, "On a PC, or in Termux (pkg install android-tools), pair with the code from " +
            "\u201CPair device with pairing code\u201D, then connect:"),
            lp(MATCH, WRAP).apply { topMargin = dp(12) })
        c.addView(codeBlock(pair), lp(MATCH, WRAP).apply { topMargin = dp(8) })
        c.addView(codeBlock(connect), lp(MATCH, WRAP).apply { topMargin = dp(6) })
        c.addView(step(3, "Run this once to grant the secure permission:"),
            lp(MATCH, WRAP).apply { topMargin = dp(12) })
        c.addView(codeBlock(grant), lp(MATCH, WRAP).apply { topMargin = dp(8) })
        c.addView(step(4, "Come back here. The Secure status turns green."),
            lp(MATCH, WRAP).apply { topMargin = dp(12) })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill("Copy command") { copyText("command", grant) },
            lp(0, dp(42), 1f).apply { marginEnd = dp(4) })
        row.addView(pill("Developer options") { openDeveloperOptions() },
            lp(0, dp(42), 1f).apply { marginStart = dp(4) })
        c.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(16) })
        return c
    }

    private fun creatorCard(): LinearLayout {
        val c = card()

        val who = LinearLayout(this)
        who.orientation = LinearLayout.HORIZONTAL
        who.gravity = Gravity.CENTER_VERTICAL
        who.setPadding(dp(14), dp(14), dp(14), dp(14))
        val avatar = TextView(this)
        avatar.text = "GA"
        avatar.textSize = 16f
        avatar.typeface = Typeface.DEFAULT_BOLD
        avatar.gravity = Gravity.CENTER
        avatar.setTextColor(Color.WHITE)
        avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.ios_blue))
        }
        who.addView(avatar, lp(dp(46), dp(46)).apply { marginEnd = dp(14) })
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val small = TextView(this)
        small.text = "Created by"
        small.textSize = 12f
        small.setTextColor(col(R.color.ios_secondary))
        texts.addView(small, lp(WRAP, WRAP))
        val name = TextView(this)
        name.text = CREATOR_NAME
        name.textSize = 18f
        name.typeface = Typeface.DEFAULT_BOLD
        name.setTextColor(col(R.color.ios_label))
        texts.addView(name, lp(WRAP, WRAP))
        who.addView(texts, lp(0, WRAP, 1f))
        c.addView(who, lp(MATCH, WRAP))

        c.addNav("t.me/GulamAzamFaridi", R.drawable.ic_send, R.color.tile_telegram) { openTelegram() }
        return c
    }

    // ------------------------------------------------------------------ UI helpers

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = GradientDrawable().apply {
            setColor(col(R.color.ios_card))
            cornerRadius = dp(14).toFloat()
        }
        c.clipToOutline = true
        return c
    }

    private fun caption(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.letterSpacing = 0.05f
        t.setTextColor(col(R.color.ios_secondary))
        return t
    }

    /** Coloured rounded square with a white glyph, like the icons in iOS Settings. */
    private fun iconTile(icon: Int, tint: Int): View {
        val tile = FrameLayout(this)
        tile.background = GradientDrawable().apply {
            setColor(col(tint))
            cornerRadius = dp(7).toFloat()
        }
        val iv = ImageView(this)
        iv.setImageResource(icon)
        tile.addView(iv, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        return tile
    }

    private fun rowContainer(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(52)
        r.setPadding(dp(14), dp(4), dp(14), dp(4))
        return r
    }

    private fun labelView(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 16f
        t.setTextColor(col(R.color.ios_label))
        return t
    }

    private fun rowRipple(row: View) {
        row.background = RippleDrawable(ColorStateList.valueOf(0x22808080), null, ColorDrawable(Color.BLACK))
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
        label: String, icon: Int, tint: Int, checked: Boolean, onChange: (Boolean) -> Unit
    ): MaterialSwitch {
        val row = rowContainer()
        row.addView(iconTile(icon, tint), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        val sw = MaterialSwitch(this@MainActivity)
        sw.thumbIconDrawable = null
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.ios_switch_track)
        sw.trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> if (!refreshing) onChange(v) }
        row.addView(sw, lp(WRAP, WRAP))
        rowRipple(row)
        row.setOnClickListener { if (sw.isEnabled) sw.toggle() }
        addRow(row)
        return sw
    }

    private fun LinearLayout.addSegmented(
        label: String, icon: Int, tint: Int, items: List<String>, selected: Int, widthDp: Int,
        onSelect: (Int) -> Unit
    ) {
        val row = rowContainer()
        row.addView(iconTile(icon, tint), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        row.addView(Segmented(this@MainActivity, items, selected, onSelect), lp(dp(widthDp), dp(32)))
        addRow(row)
    }

    /** Tappable list row with a chevron. */
    private fun LinearLayout.addNav(label: String, icon: Int, tint: Int, onClick: () -> Unit) {
        val row = rowContainer()
        row.addView(iconTile(icon, tint), lp(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        val chevron = ImageView(this@MainActivity)
        chevron.setImageResource(R.drawable.ic_chevron)
        chevron.imageTintList = ColorStateList.valueOf(col(R.color.ios_secondary))
        row.addView(chevron, lp(dp(16), dp(16)))
        rowRipple(row)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onClick() }
        addRow(row)
    }

    private fun pill(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setTextColor(col(R.color.ios_blue))
        val shape = GradientDrawable().apply {
            setColor(col(R.color.ios_btn))
            cornerRadius = dp(12).toFloat()
        }
        t.background = RippleDrawable(ColorStateList.valueOf(0x22808080), shape, null)
        t.isClickable = true
        t.isFocusable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun step(n: Int, text: String): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val badge = TextView(this)
        badge.text = n.toString()
        badge.textSize = 11f
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.gravity = Gravity.CENTER
        badge.setTextColor(Color.WHITE)
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.ios_blue))
        }
        r.addView(badge, lp(dp(20), dp(20)).apply { marginEnd = dp(10); topMargin = dp(1) })
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(col(R.color.ios_label))
        r.addView(t, lp(0, WRAP, 1f))
        return r
    }

    /** Monospace box; tapping it copies the text. */
    private fun codeBlock(text: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(12), dp(10), dp(10), dp(10))
        r.background = GradientDrawable().apply {
            setColor(col(R.color.ios_code))
            cornerRadius = dp(10).toFloat()
        }
        val t = TextView(this)
        t.text = text
        t.typeface = Typeface.MONOSPACE
        t.textSize = 12f
        t.setTextColor(col(R.color.ios_label))
        r.addView(t, lp(0, WRAP, 1f))
        val copy = ImageView(this)
        copy.setImageResource(R.drawable.ic_copy)
        copy.imageTintList = ColorStateList.valueOf(col(R.color.ios_blue))
        r.addView(copy, lp(dp(18), dp(18)).apply { marginStart = dp(8) })
        r.isClickable = true
        r.isFocusable = true
        r.setOnClickListener { copyText("command", text) }
        return r
    }

    private fun addStatus(label: String, ok: Boolean) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.gravity = Gravity.CENTER
        val dot = View(this)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(if (ok) R.color.ios_green else R.color.ios_red))
        }
        item.addView(dot, lp(dp(8), dp(8)).apply { marginEnd = dp(5) })
        val t = TextView(this)
        t.text = label
        t.textSize = 12f
        t.maxLines = 1
        t.setTextColor(col(R.color.ios_secondary))
        item.addView(t, lp(WRAP, WRAP))
        statusRow.addView(item, lp(0, WRAP, 1f))
    }
}
