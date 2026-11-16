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
