package com.touchassist.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import kotlin.math.hypot

class MacroEditorActivity : Activity() {
    private lateinit var draw: DrawView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@MacroEditorActivity, 14), Ui.dp(this@MacroEditorActivity, 30),
                Ui.dp(this@MacroEditorActivity, 14), Ui.dp(this@MacroEditorActivity, 14))
        }
        root.addView(Ui.text(this, "Tạo macro mới", 22f, Ui.CYAN, true))
        root.addView(Ui.text(this, "Vẽ đường vuốt trong khung (khung tương ứng toàn màn hình). Chỉ chạm một điểm = chạm hoặc giữ.", 13f, Ui.GREY))
        draw = DrawView(this)
        root.addView(draw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            .apply { topMargin = Ui.dp(this@MacroEditorActivity, 8) })

        val name = EditText(this).apply {
            hint = "Tên macro"; setTextColor(Color.WHITE); setHintTextColor(Ui.GREY); setSingleLine()
        }
        root.addView(name)
        val dur = seek(root, "Thời gian thực hiện", 50, 3000, 400, " ms")
        val rep = seek(root, "Số lần lặp", 1, 20, 1, "")
        val gap = seek(root, "Nghỉ giữa các lần", 0, 2000, 300, " ms")

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Ui.button(this, "Xóa vẽ") { draw.pts.clear(); draw.invalidate() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.button(this, "Lưu") {
            if (draw.pts.isEmpty()) {
                Toast.makeText(this, "Hãy vẽ một đường hoặc chạm một điểm trước", Toast.LENGTH_LONG).show()
            } else {
                val list = Prefs.macros(this)
                val nm = name.text.toString().trim().ifEmpty { "Macro ${list.size + 1}" }
                list.add(MacroItem(System.currentTimeMillis(), nm, draw.pts.toList(), dur(), rep(), gap()))
                Prefs.saveMacros(this, list)
                Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        setContentView(root)
    }

    private fun seek(root: LinearLayout, label: String, lo: Int, hi: Int, def: Int, unit: String): () -> Int {
        val tv = Ui.text(this, "$label: $def$unit", 14f, Color.WHITE)
        val sb = SeekBar(this).apply { max = hi - lo; progress = def - lo }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { tv.text = "$label: ${p + lo}$unit" }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
        })
        root.addView(tv); root.addView(sb)
        return { sb.progress + lo }
    }
}

private class DrawView(c: Context) : View(c) {
    val pts = mutableListOf<Pair<Float, Float>>() // toạ độ chuẩn hoá theo toàn màn hình
    private val d = c.resources.displayMetrics.density
    private val loc = IntArray(2)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1430.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF22D3EE.toInt(); strokeWidth = 5f * d; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val start = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B5C.toInt() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val sw = resources.displayMetrics.widthPixels.toFloat()
        val sh = resources.displayMetrics.heightPixels.toFloat()
        getLocationOnScreen(loc)
        val x = (e.x + loc[0]) / sw
        val y = (e.y + loc[1]) / sh
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pts.clear(); pts.add(x to y) }
            MotionEvent.ACTION_MOVE -> {
                val l = pts.last()
                if (pts.size < 60 && hypot((x - l.first) * sw, (y - l.second) * sh) > 12f * d) pts.add(x to y)
            }
            MotionEvent.ACTION_UP -> {
                val l = pts.last()
                if (pts.size > 1 && (x != l.first || y != l.second)) {
                    if (pts.size >= 60) pts[pts.size - 1] = x to y else pts.add(x to y)
                }
            }
        }
        parent.requestDisallowInterceptTouchEvent(true)
        invalidate()
        return true
    }

    override fun onDraw(cv: Canvas) {
        cv.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, bg)
        if (pts.isEmpty()) return
        getLocationOnScreen(loc)
        val sw = resources.displayMetrics.widthPixels.toFloat()
        val sh = resources.displayMetrics.heightPixels.toFloat()
        var px = 0f; var py = 0f; var first = true
        for (q in pts) {
            val x = q.first * sw - loc[0]; val y = q.second * sh - loc[1]
            if (!first) cv.drawLine(px, py, x, y, line)
            px = x; py = y; first = false
        }
        val s = pts[0]
        cv.drawCircle(s.first * sw - loc[0], s.second * sh - loc[1], 9f * d, start)
    }
}
