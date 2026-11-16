package com.sampmobile.launcher

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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var content: FrameLayout
    private val bg = Color.parseColor("#0F1115")
    private val card = Color.parseColor("#1B1E27")
    private val accent = Color.parseColor("#3388FF")
    private val good = Color.parseColor("#44CC77")
    private val bad = Color.parseColor("#FF5566")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun scale() = prefs.getFloat("uiScale", 1f)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun text(s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply { text = s; setTextColor(color); textSize = size * scale(); if (bold) setTypeface(null, Typeface.BOLD) }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; setTextColor(Color.WHITE); background = round(accent, 12); isAllCaps = false
        textSize = 15f * scale(); setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("sampmobile", MODE_PRIVATE)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(12), dp(28), dp(12), dp(8)) }
        content = FrameLayout(this)
        root.addView(text("SAMP MOBILE", 24f, accent, true).apply { setPadding(dp(8), 0, 0, dp(8)) })
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Lobby" to { lobby() }, "Mods" to { mods() }, "Settings" to { settings() }).forEach { (n, f) ->
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

    // ---------- SERVER QUERY (SA-MP UDP protocol, works with any server) ----------
    data class Info(val host: String, val players: Int, val max: Int, val ping: Long)

    private fun query(ip: String, port: Int): Info? {
        try {
            val addr = InetAddress.getByName(ip)
            val raw = addr.address
            if (raw.size != 4) return null
            val req = ByteArray(11)
            "SAMP".toByteArray().copyInto(req)
            raw.copyInto(req, 4)
            req[8] = (port and 0xFF).toByte()
            req[9] = ((port shr 8) and 0xFF).toByte()
            req[10] = 'i'.code.toByte()
            DatagramSocket().use { s ->
                s.soTimeout = 2500
                val t0 = System.currentTimeMillis()
                s.send(DatagramPacket(req, req.size, addr, port))
                val buf = ByteArray(2048)
                val pkt = DatagramPacket(buf, buf.size)
                s.receive(pkt)
                val ping = System.currentTimeMillis() - t0
                val bb = ByteBuffer.wrap(buf, 0, pkt.length).order(ByteOrder.LITTLE_ENDIAN)
                bb.position(11); bb.get()
                val pl = bb.short.toInt() and 0xFFFF
                val mx = bb.short.toInt() and 0xFFFF
                val len = bb.int
                val hb = ByteArray(maxOf(0, minOf(len, bb.remaining())))
                bb.get(hb)
                return Info(String(hb, Charsets.ISO_8859_1), pl, mx, ping)
            }
        } catch (e: Exception) { return null }
    }

    // ---------- LOBBY ----------
    // server format: name|ip|port|password
    private fun servers(): MutableList<List<String>> =
        (prefs.getString("servers", "") ?: "").split("\n").filter { it.isNotBlank() }
            .map { val p = it.split("|"); if (p.size >= 4) p else p + List(4 - p.size) { "" } }.toMutableList()
    private fun saveServers(l: List<List<String>>) = prefs.edit().putString("servers", l.joinToString("\n") { it.joinToString("|") }).apply()

    private fun lobby() {
        val col = column()
        val nick = prefs.getString("nick", "") ?: ""
        col.addView(text(if (nick.isBlank()) "Set your nickname in Settings" else "Player: $nick", 13f, Color.LTGRAY))
        col.addView(text("Server List", 18f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
        servers().forEachIndexed { i, s ->
            val b = box()
            b.addView(text(s[0], 16f, Color.WHITE, true))
            b.addView(text("${s[1]}:${s[2]}", 13f, Color.LTGRAY))
            val st = text("Checking...", 13f, Color.GRAY)
            b.addView(st)
            val port = s[2].toIntOrNull() ?: 7777
            Thread {
                val r = query(s[1], port)
                runOnUiThread {
                    if (r == null) { st.text = "Offline or no response"; st.setTextColor(bad) }
                    else { st.text = "${r.host}\n${r.players}/${r.max} players  |  ${r.ping} ms"; st.setTextColor(good) }
                }
            }.start()
            b.addView(button("Connect") { connect(s[1], s[2], s[3]) })
            b.setOnLongClickListener {
                AlertDialog.Builder(this).setMessage("Delete ${s[0]}?").setPositiveButton("Delete") { _, _ ->
                    saveServers(servers().also { it.removeAt(i) }); lobby() }.setNegativeButton("Cancel", null).show(); true
            }
            col.addView(b)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("+ Add Server") { addServer() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Refresh") { lobby() }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row)
        col.addView(text("Long-press a server to delete. Works with any SA-MP server (IPv4).", 12f, Color.GRAY))
        show(col)
    }

    private fun addServer() {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        val n = EditText(this).apply { hint = "Server name" }
        val ip = EditText(this).apply { hint = "IP address" }
        val p = EditText(this).apply { hint = "Port"; setText("7777"); inputType = 2 }
        val pw = EditText(this).apply { hint = "Password (optional)" }
        listOf(n, ip, p, pw).forEach { l.addView(it) }
        AlertDialog.Builder(this).setTitle("Add Server").setView(l).setPositiveButton("Save") { _, _ ->
            if (ip.text.isNotBlank()) {
                val clean = { t: EditText -> t.text.toString().trim().replace("|", "").replace("\n", "") }
                val name = clean(n).ifBlank { clean(ip) }
                saveServers(servers().also { it.add(listOf(name, clean(ip), clean(p).ifBlank { "7777" }, clean(pw))) })
                lobby()
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun connect(ip: String, port: String, pass: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("server", "$ip:$port"))
        val pkg = prefs.getString("pkg", "") ?: ""
        val i = if (pkg.isNotBlank()) packageManager.getLaunchIntentForPackage(pkg) else null
        if (i == null) { Toast.makeText(this, "Set the game package in Settings first. IP copied.", Toast.LENGTH_LONG).show(); return }
        i.putExtra("ip", ip).putExtra("port", port).putExtra("nickname", prefs.getString("nick", "")).putExtra("password", pass)
        Toast.makeText(this, "IP copied: $ip:$port", Toast.LENGTH_SHORT).show()
        startActivity(i)
    }

    // ---------- MODS ----------
    private fun mods() {
        val col = column()
        col.addView(text("Mods (AML / CLEO / MonetLoader / Modpack)", 18f, Color.WHITE, true))
        val pathBox = box()
        pathBox.addView(text("Mods folder path", 13f, Color.LTGRAY))
        val path = EditText(this).apply { setTextColor(Color.WHITE); setText(prefs.getString("modPath", "/storage/emulated/0/")); textSize = 13f }
        pathBox.addView(path)
        pathBox.addView(button("Save path") { prefs.edit().putString("modPath", path.text.toString().trim()).apply(); toast("Saved") })
        col.addView(pathBox)
        listOf("AML", "CLEO", "MonetLoader", "Modpack").forEach { name ->
            val b = box()
            val sw = Switch(this).apply {
                text = name; setTextColor(Color.WHITE); textSize = 16f * scale()
                isChecked = prefs.getBoolean("mod_$name", true)
                setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean("mod_$name", on).apply()
                    val n = toggleFiles(name, on); toast(if (n == 0) "No files named like \"$name\" in that path" else "$n file(s) updated")
                }
            }
            b.addView(sw); col.addView(b)
        }
        col.addView(text("The switch renames files whose name contains the mod name (adds/removes .off). Android 11+ may block Android/data; if so, use a folder outside it.", 12f, Color.GRAY))
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

    private fun switchRow(col: LinearLayout, label: String, key: String, def: Boolean) {
        val b = box()
        b.addView(Switch(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 15f * scale(); isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        })
        col.addView(b)
    }

    private fun settings() {
        val col = column()
        col.addView(text("Settings", 18f, Color.WHITE, true))
        val pb = box()
        pb.addView(text("Nickname", 13f, Color.LTGRAY))
        val nick = EditText(this).apply { setTextColor(Color.WHITE); setText(prefs.getString("nick", "")); hint = "Your name"; setHintTextColor(Color.GRAY) }
        pb.addView(nick)
        pb.addView(text("Game app package name", 13f, Color.LTGRAY).apply { setPadding(0, dp(8), 0, 0) })
        val pkg = EditText(this).apply { setTextColor(Color.WHITE); setText(prefs.getString("pkg", "")); hint = "e.g. com.xxx.sampmobile"; setHintTextColor(Color.GRAY) }
        pb.addView(pkg)
        pb.addView(button("Save") {
            prefs.edit().putString("nick", nick.text.toString().trim()).putString("pkg", pkg.text.toString().trim()).apply(); toast("Saved")
        })
        col.addView(pb)
        col.addView(text("Mouse & Keyboard", 16f, accent, true).apply { setPadding(0, dp(8), 0, 0) })
        slider(col, "Mouse sensitivity", "sens", 1f, 0.1f, 5f)
        switchRow(col, "Show mouse pointer", "pointer", true)
        switchRow(col, "Invert Y axis", "invertY", false)
        switchRow(col, "Use keyboard in test pad", "keys", true)
        col.addView(text("Display", 16f, accent, true).apply { setPadding(0, dp(8), 0, 0) })
        slider(col, "UI size", "uiScale", 1f, 0.8f, 1.6f) { buildUi() }
        col.addView(text("Voice", 16f, accent, true).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(button("Allow microphone") {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        col.addView(text("Input test pad (tap it, then use mouse/keyboard)", 13f, Color.LTGRAY).apply { setPadding(0, dp(12), 0, dp(4)) })
        col.addView(InputPad(), LinearLayout.LayoutParams(-1, dp(220)))
        show(col)
    }

    // Test pad: shows the mouse pointer (using your sensitivity) and the last key pressed
    inner class InputPad : View(this@MainActivity) {
        private var px = 200f
        private var py = 200f
        private var last = "-"
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            background = round(card); isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener { requestFocus(); if (Build.VERSION.SDK_INT >= 26) requestPointerCapture() }
            if (Build.VERSION.SDK_INT >= 26) setOnCapturedPointerListener { _, e ->
                val s = prefs.getFloat("sens", 1f)
                val inv = if (prefs.getBoolean("invertY", false)) -1f else 1f
                px = (px + e.x * s).coerceIn(0f, width.toFloat())
                py = (py + e.y * s * inv).coerceIn(0f, height.toFloat())
                invalidate(); true
            }
            setOnGenericMotionListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_HOVER_MOVE) { px = e.x; py = e.y; invalidate() }
                false
            }
            setOnKeyListener { _, code, e ->
                if (!prefs.getBoolean("keys", true)) return@setOnKeyListener false
                if (code == KeyEvent.KEYCODE_ESCAPE && Build.VERSION.SDK_INT >= 26) releasePointerCapture()
                if (e.action == KeyEvent.ACTION_DOWN) { last = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"); invalidate() }
                true
            }
        }
        override fun onDraw(c: Canvas) {
            paint.color = Color.WHITE; paint.textSize = dp(14).toFloat()
            c.drawText("Last key: $last   (ESC = release mouse)", dp(10).toFloat(), dp(24).toFloat(), paint)
            if (prefs.getBoolean("pointer", true)) {
                paint.color = accent
                val p = Path().apply { moveTo(px, py); lineTo(px, py + dp(22)); lineTo(px + dp(15), py + dp(16)); close() }
                c.drawPath(p, paint)
            }
        }
    }
}
