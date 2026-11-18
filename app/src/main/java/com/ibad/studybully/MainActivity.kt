package com.ibad.studybully

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var statusTv: TextView
    private lateinit var toggleBtn: Button
    private lateinit var checks: LinearLayout
    private lateinit var countTv: TextView
    private lateinit var logTv: TextView
    private lateinit var boardEt: EditText
    private lateinit var stareBtn: Button
    private lateinit var wallBtn: Button
    private val io = Executors.newSingleThreadExecutor()
    private val red = Color.parseColor("#D7261E")
    private val grey = Color.parseColor("#8A8A8A")
    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(28), px(22), px(48))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(col)
        }

        col.addView(text("STUDY BULLY", 38f, red, bold = true, condensed = true))
        col.addView(text("Your phone, now hostile to procrastination.", 14f, grey))
        statusTv = text("", 26f, Color.WHITE, bold = true, condensed = true).apply { setPadding(0, px(24), 0, px(6)) }
        col.addView(statusTv)
        toggleBtn = button("") {
            StudyMode.setActive(this, !p.active)
            refresh()
        }
        col.addView(toggleBtn)

        col.addView(button("WALL MODE  ·  KEEP IT OPEN ON THE DESK") {
            startActivity(Intent(this, WallActivity::class.java))
        })

        col.addView(section("1 · PERMISSIONS"))
        checks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(checks)

        col.addView(section("2 · YOUR COLLECTION"))
        countTv = text("", 15f, Color.WHITE)
        col.addView(countTv)
        boardEt = EditText(this).apply {
            setText(p.boardUrl)
            setTextColor(Color.WHITE)
            setHintTextColor(grey)
            hint = "Pinterest board link"
            textSize = 13f
            isSingleLine = true
        }
        col.addView(boardEt)
        col.addView(button("IMPORT FROM PINTEREST BOARD") { importBoard() })
        col.addView(button("ADD IMAGES FROM GALLERY") { pickImages() })
        col.addView(text("Board bigger than ~25–50 pins? In the Pinterest app open a pin → Share → " +
            "\"Add to Study Bully\". Duplicates are skipped automatically.", 13f, grey))
        logTv = text("", 12f, Color.parseColor("#C8C8C8")).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, px(8), 0, 0)
        }
        col.addView(logTv)

        col.addView(section("3 · SETTINGS"))
        col.addView(switch("Follow Do Not Disturb — lets a Samsung Mode (with DND on) switch Study Mode on/off", p.followDnd) { on ->
            p.followDnd = on
            if (on) p.active = StudyMode.dndOn(this)
            StudyMode.sync(this)
            refresh()
        })
        col.addView(switch("Mix built-in text roasts into the rotation", p.mixRoasts) { p.mixRoasts = it })
        stareBtn = button(stareLabel()) {
            val opts = intArrayOf(0, 2, 3, 5, 8)
            p.stareSeconds = opts[(opts.indexOf(p.stareSeconds) + 1) % opts.size]
            stareBtn.text = stareLabel()
        }
        col.addView(stareBtn)
        wallBtn = button(wallLabel()) {
            val opts = intArrayOf(10, 20, 30, 60, 120, 300)
            val i = opts.indexOf(p.wallSeconds)
            p.wallSeconds = opts[(if (i < 0) 1 else i + 1) % opts.size]
            wallBtn.text = wallLabel()
        }
        col.addView(wallBtn)
        col.addView(button("PREVIEW") { StudyMode.launch(this, preview = true) })
        col.addView(button("CLEAR COLLECTION", danger = true) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete all stored images?")
                .setPositiveButton("Delete") { _, _ -> ImageStore.clearAll(this); refresh(); log("Collection cleared.") }
                .setNegativeButton("Cancel", null)
                .show()
        })

        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 33 && !notifOk()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    override fun onResume() {
        super.onResume()
        StudyMode.sync(this)
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun wallLabel() = "WALL MODE: NEW SLIDE EVERY ${p.wallSeconds}s"

    private fun stareLabel() = "STARE LOCK: ${p.stareSeconds}s BEFORE YOU CAN DISMISS"

    private fun notifOk() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        statusTv.text = if (p.active) "STUDY MODE: ON" else "STUDY MODE: OFF"
        statusTv.setTextColor(if (p.active) red else grey)
        toggleBtn.text = if (p.active) "TURN STUDY MODE OFF" else "TURN STUDY MODE ON"
        countTv.text = "${ImageStore.count(this)} images stored on this phone (work offline)."

        checks.removeAllViews()
        val pm = getSystemService(PowerManager::class.java)
        checks.addView(checkRow("Notifications", notifOk()) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        })
        checks.addView(checkRow("Appear on top (needed for full-screen on unlock)", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        checks.addView(checkRow("Unrestricted battery (stops One UI killing it)", pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        checks.addView(button("+ ADD STUDY MODE TILE TO QUICK SETTINGS") { requestTile() })
        checks.addView(button("OPEN APP INFO (battery / restricted settings)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, StudyTileService::class.java),
                "Study Mode",
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor,
                java.util.function.Consumer<Int> { _ -> refresh() }
            )
        } else {
            Toast.makeText(this, "Swipe down twice → ✎ edit → drag \"Study Mode\" in.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importBoard() {
        val url = boardEt.text.toString().trim()
        p.boardUrl = url
        log("Connecting to Pinterest…")
        val app = applicationContext
        io.execute {
            val result = try {
                PinterestImporter.importBoard(app, url) { msg -> runOnUiThread { log(msg) } }
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            runOnUiThread { log(result); refresh() }
        }
    }

    private fun pickImages() {
        val i = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(i, REQ_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri)
        else data.data?.let { uris.add(it) }
        log("Importing ${uris.size} images…")
        val app = applicationContext
        io.execute {
            val c = ImageStore.addUris(app, uris)
            runOnUiThread { log("Gallery: $c"); refresh() }
        }
    }

    private fun log(msg: String) {
        logTv.text = msg
    }

    // ---- tiny brutalist UI kit ----

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, condensed: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(if (condensed) "sans-serif-condensed" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun section(s: String) = text(s, 13f, red, bold = true).apply {
        letterSpacing = 0.25f
        typeface = Typeface.create("monospace", Typeface.BOLD)
        setPadding(0, px(30), 0, px(8))
    }

    private fun button(label: String, danger: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = true
        setTextColor(if (danger) red else Color.WHITE)
        typeface = Typeface.create("monospace", Typeface.BOLD)
        textSize = 13f
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#111111"))
            setStroke(px(2), if (danger) red else Color.parseColor("#3A3A3A"))
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(8) }
        setOnClickListener { onClick() }
    }

    private fun checkRow(label: String, ok: Boolean, fix: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, px(6), 0, px(6))
        addView(text(if (ok) "✓" else "✗", 20f, if (ok) Color.WHITE else red, bold = true).apply {
            setPadding(0, 0, px(12), 0)
        })
        addView(text(label, 14f, if (ok) grey else Color.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (!ok) addView(Button(this@MainActivity).apply {
            text = "FIX"
            setTextColor(red)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.BLACK); setStroke(px(2), red) }
            setOnClickListener { fix() }
        })
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        isChecked = initial
        setPadding(0, px(10), 0, px(10))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    companion object {
        private const val REQ_PICK = 10
        private const val REQ_NOTIF = 11
    }
}
