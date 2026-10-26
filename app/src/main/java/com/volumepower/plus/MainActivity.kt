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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
        scroll.setBackgroundColor(col(R.color.md_background))
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

        // Compact Material 3 header
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
        header.addView(logo, lp(dp(48), dp(48)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "Volume Power Plus"
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(col(R.color.md_on_surface))
        titles.addView(title, lp(WRAP, WRAP))
        val sub = TextView(this)
        sub.text = if (version.isEmpty()) "Wake and lock with a volume key" else "Wake and lock with a volume key · v$version"
        sub.textSize = 13f
        sub.setTextColor(col(R.color.md_on_surface_variant))
        titles.addView(sub, lp(WRAP, WRAP))
        header.addView(titles, lp(0, WRAP, 1f).apply { marginStart = dp(14) })
        page.addView(header, lp(MATCH, WRAP).apply { topMargin = dp(8) })

        // System status summary
        val statusCard = card()
        statusCard.setPadding(dp(16), dp(12), dp(16), dp(12))
        val statusTitle = TextView(this)
        statusTitle.text = "System status"
        statusTitle.textSize = 14f
        statusTitle.typeface = Typeface.DEFAULT_BOLD
        statusTitle.setTextColor(col(R.color.md_on_surface))
        statusCard.addView(statusTitle, lp(MATCH, WRAP))
        statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusCard.addView(statusRow, lp(MATCH, WRAP).apply { topMargin = dp(12) })
        page.addView(statusCard, lp(MATCH, WRAP).apply { topMargin = dp(20) })

        // Core controls
        page.addView(caption("CONTROLS"), lp(WRAP, WRAP).apply { topMargin = dp(24); marginStart = dp(4) })
        val c1 = card()
        page.addView(c1, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        c1.addSwitch("Volume key as power", R.drawable.ic_power, Prefs.featureEnabled(this)) { v ->
            Prefs.setFeatureEnabled(this, v)
            if (v) {
                SelfHeal.healIfWanted(this)
                VolumePowerForegroundService.start(this)
            } else {
                VolumePowerForegroundService.stop(this)
            }
            refresh()
        }
        c1.addChoice(
            "Trigger key", R.drawable.ic_volume,
            listOf("Vol up", "Vol down"),
            if (Prefs.triggerKey(this) == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else 0
        ) { i ->
            Prefs.setTriggerKey(this, if (i == 1) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
        }
        c1.addSwitch("Start on boot", R.drawable.ic_play, Prefs.startOnBoot(this)) {
            Prefs.setStartOnBoot(this, it)
        }
        c1.addSwitch("Re-enable accessibility", R.drawable.ic_refresh, Prefs.autoHeal(this)) {
            Prefs.setAutoHeal(this, it)
        }
        c1.addSwitch("Silent audio keep-alive", R.drawable.ic_wave, Prefs.keepAlive(this)) { v ->
            Prefs.setKeepAlive(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }
        c1.addSwitch("Use Shizuku for key", R.drawable.ic_terminal, Prefs.shizukuKeys(this)) { v ->
            Prefs.setShizukuKeys(this, v)
            if (Prefs.featureEnabled(this)) VolumePowerForegroundService.start(this)
        }
        c1.addSwitch("Wake screen in calls", R.drawable.ic_phone, Prefs.callFix(this)) {
            Prefs.setCallFix(this, it)
        }

        // Optional Device Administrator controls
        page.addView(caption("DEVICE ADMIN"), lp(WRAP, WRAP).apply { topMargin = dp(24); marginStart = dp(4) })
        val admin = ComponentName(this, AdminReceiver::class.java)
        val activeAdmin = isDeviceAdminActive(admin)
        val c2 = card()
        page.addView(c2, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        swAdmin = c2.addSwitch("Device admin", R.drawable.ic_shield, activeAdmin) { enabled ->
            setDeviceAdminEnabled(admin, enabled)
        }
        swAdminLock = c2.addSwitch(
            "Lock with admin (PIN after)", R.drawable.ic_lock,
            activeAdmin && Prefs.useAdminLock(this)
        ) { enabled ->
            Prefs.setUseAdminLock(this, enabled)
        }

        // ---- Setup ----
        page.addView(caption("SETUP"), lp(WRAP, WRAP).apply { topMargin = dp(24); marginStart = dp(4) })
        val c3 = card()
        page.addView(c3, lp(MATCH, WRAP).apply { topMargin = dp(7) })

        c3.addNav("Get Shizuku", R.drawable.ic_download) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
        c3.addNav("Open Shizuku", R.drawable.ic_open) {
            val i = packageManager.getLaunchIntentForPackage(ShizukuBridge.PKG)
            if (i != null) startActivity(i)
            else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
        }
        c3.addNav("Allow this app", R.drawable.ic_check) {
            if (!ShizukuBridge.running()) {
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show()
            } else {
                ShizukuBridge.requestPermission()
            }
        }
        c3.addNav("Grant all permissions", R.drawable.ic_shield_check) { grantAll() }
        c3.addNav("Accessibility settings", R.drawable.ic_accessibility) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c3.addNav("Battery", R.drawable.ic_battery) { openBattery() }

        // ---- Advanced (manual ADB) ----
        page.addView(caption("ADVANCED"), lp(WRAP, WRAP).apply { topMargin = dp(24); marginStart = dp(4) })
        page.addView(advancedCard(), lp(MATCH, WRAP).apply { topMargin = dp(7) })

        // ---- Creator ----
        page.addView(caption("ABOUT"), lp(WRAP, WRAP).apply { topMargin = dp(24); marginStart = dp(4) })
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

    private fun isDeviceAdminActive(admin: ComponentName): Boolean = try {
        getSystemService(DevicePolicyManager::class.java).isAdminActive(admin)
    } catch (_: Exception) {
        false
    }

    private fun setDeviceAdminEnabled(admin: ComponentName, enabled: Boolean) {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (enabled) {
            try {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Could not open device admin settings", Toast.LENGTH_SHORT).show()
                refresh()
            }
            return
        }

        if (!isDeviceAdminActive(admin)) {
            refresh()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Disable device admin?")
            .setMessage("Volume Power Plus will stop using Device Admin to lock the screen. You can enable it again any time.")
            .setNegativeButton("Keep enabled") { _, _ -> refresh() }
            .setPositiveButton("Disable") { _, _ ->
                try {
                    dpm.removeActiveAdmin(admin)
                    Prefs.setUseAdminLock(this, false)
                    Toast.makeText(this, "Device admin disabled", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Could not disable device admin. Opening Android settings…", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    } catch (_: Exception) {
                    }
                } finally {
                    refresh()
                    swAdmin.postDelayed({ refresh() }, 500)
                }
            }
            .setOnCancelListener { refresh() }
            .show()
    }

    private fun refresh() {
        if (!::statusRow.isInitialized || !::swAdmin.isInitialized || !::swAdminLock.isInitialized) return

        val admin = ComponentName(this, AdminReceiver::class.java)
        val adminActive = isDeviceAdminActive(admin)
        val battOk = try {
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) {
            false
        }

        if (!adminActive && Prefs.useAdminLock(this)) Prefs.setUseAdminLock(this, false)

        statusRow.removeAllViews()
        addStatus("Access", SelfHeal.isServiceEnabled(this))
        addStatus("Secure", SelfHeal.hasSecurePermission(this))
        addStatus("Shizuku", ShizukuBridge.granted())
        addStatus("Admin", adminActive)
        addStatus("Battery", battOk)

        refreshing = true
        try {
            swAdmin.isChecked = adminActive
            swAdminLock.isChecked = adminActive && Prefs.useAdminLock(this)
            swAdminLock.isEnabled = adminActive
            (swAdminLock.parent as? View)?.alpha = if (adminActive) 1f else 0.55f
        } finally {
            refreshing = false
        }
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
        head.addView(iconTile(R.drawable.ic_terminal), lp(dp(36), dp(36)).apply { marginEnd = dp(12) })
        val ht = LinearLayout(this)
        ht.orientation = LinearLayout.VERTICAL
        val h1 = TextView(this)
        h1.text = "Manual ADB"
        h1.textSize = 16f
        h1.typeface = Typeface.DEFAULT_BOLD
        h1.setTextColor(col(R.color.md_on_surface))
        ht.addView(h1, lp(WRAP, WRAP))
        val h2 = TextView(this)
        h2.text = "Only needed without Shizuku"
        h2.textSize = 12f
        h2.setTextColor(col(R.color.md_on_surface_variant))
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
        avatar.setTextColor(col(R.color.md_on_primary_container))
        avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.md_primary_container))
        }
        who.addView(avatar, lp(dp(46), dp(46)).apply { marginEnd = dp(14) })
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val small = TextView(this)
        small.text = "Created by"
        small.textSize = 12f
        small.setTextColor(col(R.color.md_on_surface_variant))
        texts.addView(small, lp(WRAP, WRAP))
        val name = TextView(this)
        name.text = CREATOR_NAME
        name.textSize = 18f
        name.typeface = Typeface.DEFAULT_BOLD
        name.setTextColor(col(R.color.md_on_surface))
        texts.addView(name, lp(WRAP, WRAP))
        who.addView(texts, lp(0, WRAP, 1f))
        c.addView(who, lp(MATCH, WRAP))

        c.addNav("t.me/GulamAzamFaridi", R.drawable.ic_send) { openTelegram() }
        return c
    }

    // ------------------------------------------------------------------ UI helpers

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = GradientDrawable().apply {
            setColor(col(R.color.md_surface))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), col(R.color.md_outline_variant))
        }
        c.clipToOutline = true
        return c
    }

    private fun caption(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.08f
        t.setTextColor(col(R.color.md_on_surface_variant))
        return t
    }

    /** A single, consistent Material 3 icon treatment rather than colorful app-like tiles. */
    private fun iconTile(icon: Int): View {
        val tile = FrameLayout(this)
        tile.background = GradientDrawable().apply {
            setColor(col(R.color.md_primary_container))
            cornerRadius = dp(12).toFloat()
        }
        val iv = ImageView(this)
        iv.setImageResource(icon)
        iv.imageTintList = ColorStateList.valueOf(col(R.color.md_on_primary_container))
        tile.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        return tile
    }

    private fun rowContainer(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(60)
        r.setPadding(dp(16), dp(4), dp(16), dp(4))
        return r
    }

    private fun labelView(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.maxLines = 1
        t.setTextColor(col(R.color.md_on_surface))
        return t
    }

    private fun rowRipple(row: View) {
        val primary = col(R.color.md_primary)
        val rippleColor = ColorStateList.valueOf(
            Color.argb(28, Color.red(primary), Color.green(primary), Color.blue(primary))
        )
        row.background = RippleDrawable(rippleColor, null, ColorDrawable(Color.WHITE))
        row.isClickable = true
        row.isFocusable = true
    }

    private fun LinearLayout.addRow(row: View) {
        if (childCount > 0) {
            val line = View(this@MainActivity)
            line.setBackgroundColor(col(R.color.md_outline_variant))
            addView(line, lp(MATCH, dp(1)).apply { marginStart = dp(64) })
        }
        addView(row, lp(MATCH, WRAP))
    }

    private fun LinearLayout.addSwitch(
        label: String, icon: Int, checked: Boolean, onChange: (Boolean) -> Unit
    ): MaterialSwitch {
        val row = rowContainer()
        row.addView(iconTile(icon), lp(dp(36), dp(36)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f).apply { marginEnd = dp(8) })
        val sw = MaterialSwitch(this@MainActivity)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, value -> if (!refreshing) onChange(value) }
        row.addView(sw, lp(WRAP, WRAP))
        rowRipple(row)
        row.setOnClickListener { if (sw.isEnabled) sw.toggle() }
        addRow(row)
        return sw
    }

    private fun LinearLayout.addChoice(
        label: String, icon: Int, items: List<String>, selected: Int, onSelect: (Int) -> Unit
    ) {
        val row = rowContainer()
        row.addView(iconTile(icon), lp(dp(36), dp(36)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f).apply { marginEnd = dp(8) })
        val group = ChipGroup(this@MainActivity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            isSingleLine = true
            chipSpacingHorizontal = dp(4)
        }
        items.forEachIndexed { index, item ->
            val chip = Chip(this@MainActivity).apply {
                id = View.generateViewId()
                text = item
                isCheckable = true
                isChecked = index == selected
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { onSelect(index) }
            }
            group.addView(chip)
        }
        row.addView(group, lp(WRAP, WRAP))
        addRow(row)
    }

    /** Tappable list row with a Material-style icon and chevron. */
    private fun LinearLayout.addNav(label: String, icon: Int, onClick: () -> Unit) {
        val row = rowContainer()
        row.addView(iconTile(icon), lp(dp(36), dp(36)).apply { marginEnd = dp(12) })
        row.addView(labelView(label), lp(0, WRAP, 1f))
        val chevron = ImageView(this@MainActivity)
        chevron.setImageResource(R.drawable.ic_chevron)
        chevron.imageTintList = ColorStateList.valueOf(col(R.color.md_on_surface_variant))
        row.addView(chevron, lp(dp(18), dp(18)))
        rowRipple(row)
        row.setOnClickListener { onClick() }
        addRow(row)
    }

    private fun pill(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(col(R.color.md_on_primary_container))
            backgroundTintList = ColorStateList.valueOf(col(R.color.md_primary_container))
            cornerRadius = dp(14)
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(44)
            setOnClickListener { onClick() }
        }
    }

    private fun step(n: Int, text: String): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val badge = TextView(this)
        badge.text = n.toString()
        badge.textSize = 12f
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.gravity = Gravity.CENTER
        badge.setTextColor(col(R.color.md_on_primary_container))
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.md_primary_container))
        }
        r.addView(badge, lp(dp(24), dp(24)).apply { marginEnd = dp(10); topMargin = dp(1) })
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(col(R.color.md_on_surface))
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
            setColor(col(R.color.md_surface_variant))
            cornerRadius = dp(12).toFloat()
        }
        val t = TextView(this)
        t.text = text
        t.typeface = Typeface.MONOSPACE
        t.textSize = 12f
        t.setTextColor(col(R.color.md_on_surface))
        r.addView(t, lp(0, WRAP, 1f))
        val copy = ImageView(this)
        copy.setImageResource(R.drawable.ic_copy)
        copy.imageTintList = ColorStateList.valueOf(col(R.color.md_primary))
        copy.contentDescription = "Copy command"
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
            setColor(col(if (ok) R.color.md_success else R.color.md_error))
        }
        item.addView(dot, lp(dp(7), dp(7)).apply { marginEnd = dp(4) })
        val t = TextView(this)
        t.text = label
        t.textSize = 11f
        t.maxLines = 1
        t.setTextColor(col(R.color.md_on_surface_variant))
        item.addView(t, lp(WRAP, WRAP))
        statusRow.addView(item, lp(0, WRAP, 1f))
    }

}
