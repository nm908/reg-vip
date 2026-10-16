package com.touchassist.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView

object Ui {
    val CYAN = 0xFF22D3EE.toInt()
    val RED = 0xFFFF3B5C.toInt()
    val BG = 0xFF050B1F.toInt()
    val GREY = 0xFF9FB3D1.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    // Nền chia đôi đỏ - xanh như ảnh mẫu
    fun splitBg() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFF8A1238.toInt(), 0xFF0A1030.toInt(), 0xFF0E4BC4.toInt())
    )

    fun rounded(c: Context, fill: Int, stroke: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(c, 2), stroke)
        cornerRadius = dp(c, radius).toFloat()
    }

    fun text(c: Context, t: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val k = if (Prefs.sp(c).getBoolean("big_text", false)) 1.3f else 1f
        return TextView(c).apply {
            text = t
            textSize = size * k
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(c, 6), 0, 0)
        }
    }

    fun button(c: Context, t: String, click: () -> Unit): Button = Button(c).apply {
        text = t
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(c, 0xFF0E3A8C.toInt(), CYAN, 14)
        setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10))
        setOnClickListener { click() }
    }
}
