package com.gmobile.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var content: FrameLayout
    private val bg = Color.parseColor("#12141A")
    private val card = Color.parseColor("#1D2029")
    private val accent = Color.parseColor("#3388FF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun scale() = prefs.getFloat("uiScale", 1f)

    private fun text(s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply { text = s; setTextColor(color); textSize = size * scale(); if (bold) setTypeface(null, Typeface.BOLD) }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; setTextColor(Color.WHITE); background = round(accent, 12); isAllCaps = false
        textSize = 15f * scale(); setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("g", MODE_PRIVATE)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(12), dp(24), dp(12), dp(8)) }
        content = FrameLayout(this)
        root.addView(text("G LAUNCHER", 22f, accent, true).apply { setPadding(dp(8), 0, 0, dp(8)) })
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Lobby" to ::lobby, "Mods" to ::mods, "Settings" to ::settings).forEach { (n, f) ->
            nav.addView(button(n) { f() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        root.addView(nav)
        setContentView(root)
        lobby()
    }

    private fun show(v: View) { content.removeAllViews(); content.addView(ScrollView(this).apply { addView(v) }) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(16)) }
    private fun box() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = round(card); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    // ---------- LOBBY ----------
    private fun servers(): MutableList<List<String>> =
        (prefs.getString("servers", "") ?: "").split("\n").filter { it.isNotBlank() }.map { it.split("|") }.toMutableList()
    private fun saveServers(l: List<List<String>>) = prefs.edit().putString("servers", l.joinToString("\n") { it.joinToString("|") }).apply()

    private fun lobby() {
        val col = column()
        col.addView(text("Server List", 18f, Color.WHITE, true))
        servers().forEachIndexed { i, s ->
            val b = box()
            b.addView(text(s[0], 16f, Color.WHITE, true))
            b.addView(text("${s[1]}:${s[2]}", 13f, Color.LTGRAY))
            b.addView(button("Connect") { connect(s[1], s[2]) })
            b.setOnLongClickListener {
                AlertDialog.Builder(this).setMessage("Delete ${s[0]}?").setPositiveButton("Delete") { _, _ ->
                    saveServers(servers().also { it.removeAt(i) }); lobby() }.setNegativeButton("Cancel", null).show(); true
            }
            col.addView(b)
        }
        col.addView(button("+ Add Server") { addServer() })
        col.addView(text("Long-press a server to delete.", 12f, Color.GRAY))
        show(col)
    }

    private fun addServer() {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        val n = EditText(this).apply { hint = "Server name" }
        val ip = EditText(this).apply { hint = "IP address" }
        val p = EditText(this).apply { hint = "Port"; setText("7777"); inputType = 2 }
        listOf(n, ip, p).forEach { l.addView(it) }
        AlertDialog.Builder(this).setTitle("Add Server").setView(l).setPositiveButton("Save") { _, _ ->
            if (ip.text.isNotBlank()) {
                saveServers(servers().also { it.add(listOf(n.text.toString().ifBlank { ip.text.toString() }.replace("|", ""), ip.text.toString().trim(), p.text.toString().trim())) })
                lobby()
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun connect(ip: String, port: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("server", "$ip:$port"))
        val pkg = prefs.getString("pkg", "") ?: ""
        val i = if (pkg.isNotBlank()) packageManager.getLaunchIntentForPackage(pkg) else null
        if (i == null) { Toast.makeText(this, "Set the game package in Settings first. IP copied.", Toast.LENGTH_LONG).show(); return }
        i.putExtra("ip", ip).putExtra("port", port)
        Toast.makeText(this, "IP copied: $ip:$port", Toast.LENGTH_SHORT).show()
        startActivity(i)
    }

    // ---------- MODS ----------
    private fun mods() {
        val col = column()
        col.addView(text("Mods (AML / CLEO / MonetLoader)", 18f, Color.WHITE, true))
        val pathBox = box()
        pathBox.addView(text("Mods folder path", 13f, Color.LTGRAY))
        val path = EditText(this).apply { setTextColor(Color.WHITE); setText(prefs.getString("modPath", "/storage/emulated/0/")); textSize = 13f }
        pathBox.addView(path)
        pathBox.addView(button("Save path") { prefs.edit().putString("modPath", path.text.toString()).apply(); toast("Saved") })
        col.addView(pathBox)
        listOf("AML", "CLEO", "MonetLoader").forEach { name ->
            val b = box()
            val sw = Switch(this).apply {
                text = name; setTextColor(Color.WHITE); textSize = 16f * scale()
                isChecked = prefs.getBoolean("mod_$name", true)
                setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean("mod_$name", on).apply()
                    val n = toggleFiles(name, on); toast(if (n == 0) "No files found for $name in that path" else "$n file(s) updated")
                }
            }
            b.addView(sw); col.addView(b)
        }
        col.addView(text("Toggle renames matching files (adds/removes .off). On Android 11+, Android/data may be blocked by the system; use a folder outside it or a file manager.", 12f, Color.GRAY))
        col.addView(button("Grant file access") { requestAllFiles() })
        show(col)
    }

    private fun toggleFiles(name: String, on: Boolean): Int {
        var n = 0
        try {
            File(prefs.getString("modPath", "") ?: "").walkTopDown().maxDepth(4).filter { it.isFile && it.name.contains(name, true) }.forEach { f ->
                val dst = if (on && f.name.endsWith(".off")) File(f.path.removeSuffix(".off")) else if (!on && !f.name.endsWith(".off")) File(f.path + ".off") else null
                if (dst != null && f.renameTo(dst)) n++
            }
        } catch (e: Exception) { toast("Error: ${e.message}") }
        return n
    }

    private fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager())
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        else toast("Access already granted")
    }

    // ---------- SETTINGS ----------
    private fun slider(col: LinearLayout, label: String, key: String, def: Float, min: Float, max: Float, afterSave: (() -> Unit)? = null) {
        val b = box(); val v = prefs.getFloat(key, def)
        val t = text("$label: ${"%.2f".format(v)}", 15f); b.addView(t)
        b.addView(SeekBar(this).apply {
            this.max = 100; progress = (((v - min) / (max - min)) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val nv = min + (max - min) * p / 100f
                    t.text = "$label: ${"%.2f".format(nv)}"; prefs.edit().putFloat(key, nv).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { afterSave?.invoke() }
            })
        })
        col.addView(b)
    }

    private fun switchRow(col: LinearLayout, label: String, key: String, def: Boolean, onChange: ((Boolean) -> Unit)? = null) {
        val b = box()
        b.addView(Switch(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 15f * scale(); isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply(); onChange?.invoke(on) }
        })
        col.addView(b)
    }

    private fun settings() {
        val col = column()
        col.addView(text("Settings", 18f, Color.WHITE, true))
        val pb = box()
        pb.addView(text("Game app package name", 13f, Color.LTGRAY))
        val pkg = EditText(this).apply { setTextColor(Color.WHITE); setText(prefs.getString("pkg", "")); hint = "e.g. com.xxx.sampmobile"; setHintTextColor(Color.GRAY) }
        pb.addView(pkg); pb.addView(button("Save package") { prefs.edit().putString("pkg", pkg.text.toString().trim()).apply(); toast("Saved") })
        col.addView(pb)
        slider(col, "Mouse sensitivity", "sens", 1f, 0.1f, 5f)
        slider(col, "UI size", "uiScale", 1f, 0.8f, 1.6f) { buildUi() }
        switchRow(col, "Show mouse pointer", "pointer", true)
        switchRow(col, "Capture keyboard", "keys", true)
        col.addView(button("Allow microphone") {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        col.addView(text("Input test pad (tap it, then use mouse/keyboard)", 13f, Color.LTGRAY).apply { setPadding(0, dp(12), 0, dp(4)) })
        col.addView(InputPad(), LinearLayout.LayoutParams(-1, dp(220)))
        show(col)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // Test pad: shows mouse pointer (with your sensitivity) and last key pressed
    inner class InputPad : View(this@MainActivity) {
        private var px = 200f; private var py = 200f; private var last = "-"
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            background = round(card); isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener { requestFocus(); if (Build.VERSION.SDK_INT >= 26) requestPointerCapture() }
            if (Build.VERSION.SDK_INT >= 26) setOnCapturedPointerListener { _, e ->
                val s = prefs.getFloat("sens", 1f); px = (px + e.x * s).coerceIn(0f, width.toFloat()); py = (py + e.y * s).coerceIn(0f, height.toFloat()); invalidate(); true }
            setOnGenericMotionListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_HOVER_MOVE) { px = e.x; py = e.y; invalidate() }; false }
            setOnKeyListener { _, code, e ->
                if (code == KeyEvent.KEYCODE_ESCAPE && Build.VERSION.SDK_INT >= 26) releasePointerCapture()
                if (e.action == KeyEvent.ACTION_DOWN) { last = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"); invalidate() }
                true
            }
        }
        override fun onDraw(c: Canvas) {
            paint.color = Color.WHITE; paint.textSize = dp(14).toFloat(); c.drawText("Last key: $last   (ESC = release mouse)", dp(10).toFloat(), dp(24).toFloat(), paint)
            if (prefs.getBoolean("pointer", true)) {
                paint.color = accent; val p = Path().apply { moveTo(px, py); lineTo(px, py + dp(22)); lineTo(px + dp(15), py + dp(16)); close() }; c.drawPath(p, paint)
            }
        }
    }
}
