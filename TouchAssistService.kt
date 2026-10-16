package com.touchassist.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

class TouchAssistService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile var instance: TouchAssistService? = null
    }

    private class Item(val label: String, val run: () -> Unit)

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private var panel: LinearLayout? = null
    private var menu: View? = null
    private var stopBtn: View? = null
    private var zone: View? = null
    // Chế độ trực tiếp của vùng vuốt nhẹ (cần Android 8 trở lên)
    private var liveStroke: GestureDescription.StrokeDescription? = null
    private var liveBusy = false
    private var liveEnding = false
    private var liveAxis = 0
    private var liveSx = 0f; private var liveSy = 0f
    private var liveOx = 0f; private var liveOy = 0f
    private var liveLx = 0f; private var liveLy = 0f
    private var liveTx = 0f; private var liveTy = 0f
    private var expanded = false
    private var running = false
    private var token = 0

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopMacro() }
    override fun onUnbind(intent: Intent?): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "home_x" || key == "home_y") return
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels

    private fun cleanup() {
        stopMacro()
        removeViews()
        try { Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this) } catch (_: Exception) {}
        instance = null
    }

    private fun removeViews() {
        if (!::wm.isInitialized) return
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        zone?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null; menu = null; stopBtn = null; zone = null
    }

    private fun refresh() {
        removeViews()
        val s = Prefs.sp(this)
        if (!s.getBoolean("enabled", false)) return
        addPanel(s)
        if (expanded) addMenu(s)
        if (s.getBoolean("zone_on", false)) addZone(s)
    }

    // Dùng quyền "hiển thị trên ứng dụng khác" nếu đã cấp, nếu chưa thì dùng cửa sổ của Trợ năng.
    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Settings.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
        } else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    private fun setExpanded(b: Boolean) {
        expanded = b
        h.post { refresh() }
    }

    private fun updateStop() { stopBtn?.visibility = if (running) View.VISIBLE else View.GONE }

    private fun stopBubble(sizeDp: Int, pad: Int): TextView {
        val g = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE60B1530.toInt())
            setStroke(dp(2), 0xFFEF4444.toInt())
        }
        return TextView(this).apply {
            text = "\u25A0"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            background = InsetDrawable(g, pad)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp) + 2 * pad, dp(sizeDp) + 2 * pad)
        }
    }

    private fun addPanel(s: SharedPreferences) {
        val size = s.getInt("size_dp", 60)
        val a = s.getInt("alpha", 80) / 100f
        val pad = if (s.getBoolean("big_touch", true)) dp(10) else 0

        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val home = HomeView(this, pad).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size) + 2 * pad, dp(size) + 2 * pad)
            alpha = a
        }
        val stop = stopBubble(size * 3 / 4, pad)
        stop.visibility = if (running) View.VISIBLE else View.GONE
        stop.setOnClickListener { stopMacro() }
        root.addView(home); root.addView(stop)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = s.getInt("home_x", screenW() - dp(size + 40))
        lp.y = s.getInt("home_y", screenH() / 3)

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        home.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            lp.x = ox + dx; lp.y = oy + dy
                            try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) Prefs.sp(this@TouchAssistService).edit()
                            .putInt("home_x", lp.x).putInt("home_y", lp.y).apply()
                        else setExpanded(!expanded)
                    }
                }
                return true
            }
        })
        wm.addView(root, lp)
        panel = root; stopBtn = stop
    }

    private fun menuItems(): List<Item> {
        val l = mutableListOf(
            Item("\uD83C\uDFE0\nTrang chủ") { performGlobalAction(GLOBAL_ACTION_HOME) },
            Item("\u2B05\uFE0F\nQuay lại") { performGlobalAction(GLOBAL_ACTION_BACK) },
            Item("\uD83D\uDDC2\uFE0F\nĐa nhiệm") { performGlobalAction(GLOBAL_ACTION_RECENTS) },
            Item("\uD83D\uDD14\nThông báo") { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
        )
        for (m in Prefs.macros(this)) l.add(Item("\u25B6\uFE0F\n${m.name}") { runMacro(m) })
        return l
    }

    /** Giữ nút đủ lâu mới kích hoạt (holdMs > 0) để tránh chạm nhầm do tay run. */
    private fun bindPress(v: View, holdMs: Int, action: () -> Unit) {
        if (holdMs <= 0) { v.setOnClickListener { action() }; return }
        val r = Runnable { action() }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { h.postDelayed(r, holdMs.toLong()); v.alpha = 0.6f }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); v.alpha = 1f }
            }
            true
        }
    }

    // Menu hiện giữa màn hình, giống AssistiveTouch của iPhone
    private fun addMenu(s: SharedPreferences) {
        val holdMs = s.getInt("hold_ms", 0)
        val size = s.getInt("size_dp", 60)
        val cell = dp(maxOf(88, size * 3 / 2))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = Ui.rounded(this@TouchAssistService, 0xF0101A38.toInt(), 0xFF22D3EE.toInt(), 24)
        }
        for (rowItems in menuItems().chunked(3)) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (item in rowItems) {
                val b = TextView(this).apply {
                    text = item.label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    background = Ui.rounded(this@TouchAssistService, 0x33FFFFFF, 0x4422D3EE, 14)
                    layoutParams = LinearLayout.LayoutParams(cell, cell).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                }
                bindPress(b, holdMs) {
                    setExpanded(false)               // đóng menu trước để không che thao tác
                    h.postDelayed({ item.run() }, 300)
                }
                r.addView(b)
            }
            grid.addView(r)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        scroll.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) setExpanded(false)
            false
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        wm.addView(scroll, lp)
        menu = scroll
    }

    // Vùng vuốt nhẹ: dải mỏng ở mép màn hình. Người dùng chỉ vuốt một đoạn ngắn trong dải,
    // app làm thẳng và hoàn thành cú vuốt đầy đủ ở giữa màn hình. Chỉ chạy khi người dùng vuốt.
    private fun addZone(s: SharedPreferences) {
        val v = View(this).apply {
            background = Ui.rounded(this@TouchAssistService, 0x2222D3EE, 0x6622D3EE, 12)
        }
        val w = dp(s.getInt("zone_w", 60))
        val hh = screenH() * s.getInt("zone_h", 50) / 100
        val lp = WindowManager.LayoutParams(
            w, hh, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = (if (s.getBoolean("zone_left", false)) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        val live = s.getBoolean("zone_live", true) && Build.VERSION.SDK_INT >= 26
        val gain = s.getInt("zone_gain", 30) / 10f
        val lock = dp(8)
        val minMove = dp(14)
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                if (live) {
                    liveTouch(e, gain, lock)
                } else {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY }
                        MotionEvent.ACTION_UP -> {
                            val dx = e.rawX - sx; val dy = e.rawY - sy
                            if (hypot(dx, dy) > minMove) zoneSwipe(sy, dx, dy)
                        }
                    }
                }
                return true
            }
        })
        wm.addView(v, lp)
        zone = v
    }

    // Chế độ trực tiếp: ngón tay ảo đi theo ngón tay thật nhưng đi xa gấp "gain" lần, theo đường thẳng.
    private fun liveTouch(e: MotionEvent, gain: Float, lock: Int) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                liveSx = e.rawX; liveSy = e.rawY
                liveAxis = 0; liveStroke = null; liveBusy = false; liveEnding = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - liveSx; val dy = e.rawY - liveSy
                if (liveAxis == 0) {
                    if (hypot(dx, dy) < lock) return
                    liveAxis = if (abs(dx) > abs(dy)) 1 else 2
                    liveOx = screenW() / 2f; liveOy = liveSy
                    liveLx = liveOx; liveLy = liveOy; liveTx = liveOx; liveTy = liveOy
                    liveBegin()
                }
                val w = screenW().toFloat(); val hgt = screenH().toFloat()
                if (liveAxis == 1) { liveTx = (liveOx + dx * gain).coerceIn(1f, w - 1); liveTy = liveOy }
                else { liveTx = liveOx; liveTy = (liveOy + dy * gain).coerceIn(1f, hgt - 1) }
                livePump()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (liveAxis != 0 && liveStroke != null) { liveEnding = true; livePump() }
                liveAxis = 0
            }
        }
    }

    private fun liveBegin() {
        val p = Path(); p.moveTo(liveLx, liveLy)
        val st = GestureDescription.StrokeDescription(p, 0, 10, true)
        liveStroke = st; liveBusy = true
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(st).build(), object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { liveBusy = false; livePump() }
            override fun onCancelled(d: GestureDescription?) { liveBusy = false; liveStroke = null }
        }, null)
        if (!ok) { liveBusy = false; liveStroke = null }
    }

    private fun livePump() {
        val cur = liveStroke ?: return
        if (liveBusy) return
        val moving = abs(liveTx - liveLx) > 1f || abs(liveTy - liveLy) > 1f
        if (!moving && !liveEnding) return
        val last = liveEnding
        val p = Path(); p.moveTo(liveLx, liveLy)
        if (moving) p.lineTo(liveTx, liveTy)
        val stepMs = Prefs.sp(this).getInt("zone_step", 10).toLong().coerceAtLeast(1L)
        val next = cur.continueStroke(p, 0, stepMs, !last)
        liveStroke = next; liveLx = liveTx; liveLy = liveTy; liveBusy = true
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(next).build(), object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                liveBusy = false
                if (last) liveStroke = null else livePump()
            }
            override fun onCancelled(d: GestureDescription?) { liveBusy = false; liveStroke = null }
        }, null)
        if (!ok) { liveBusy = false; liveStroke = null }
    }

    private fun zoneSwipe(sy: Float, dx: Float, dy: Float) {
        val sp = Prefs.sp(this)
        val dist = sp.getInt("zone_dist", 600).toFloat()
        val dur = sp.getInt("zone_dur", 300).toLong().coerceIn(1L, GestureDescription.getMaxGestureDuration())
        val w = screenW().toFloat(); val hgt = screenH().toFloat()
        val horizontal = abs(dx) > abs(dy)
        val sx = w / 2
        val ex = if (horizontal) (sx + (if (dx > 0) dist else -dist)).coerceIn(1f, w - 1) else sx
        val ey = if (horizontal) sy else (sy + (if (dy > 0) dist else -dist)).coerceIn(1f, hgt - 1)
        val path = Path()
        path.moveTo(sx, sy); path.lineTo(ex, ey)
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build(),
            null, null
        )
    }

    fun runMacro(m: MacroItem) {
        if (running) { stopMacro(); return }
        if (m.pts.isEmpty()) return
        running = true; token++; updateStop()
        step(m, 0, token)
    }

    /** Nút "Thử" trong app: chạy sau 3 giây để kịp chuyển sang màn hình cần thử. */
    fun scheduleTest(id: Long): Boolean {
        if (!Prefs.sp(this).getBoolean("enabled", false)) return false
        val m = Prefs.macros(this).firstOrNull { it.id == id } ?: return false
        h.postDelayed({ runMacro(m) }, 3000)
        return true
    }

    fun stopMacro() {
        running = false; token++
        h.removeCallbacksAndMessages(null)
        updateStop()
    }

    private fun endRun() { running = false; updateStop() }

    private fun step(m: MacroItem, n: Int, tk: Int) {
        if (!running || tk != token) return
        if (m.rep > 0 && n >= m.rep) { endRun(); return }
        val w = screenW().toFloat(); val hgt = screenH().toFloat()
        val pts = m.pts.map { (it.first * w).coerceIn(1f, w - 1) to (it.second * hgt).coerceIn(1f, hgt - 1) }
        val path = Path()
        path.moveTo(pts[0].first, pts[0].second)
        if (pts.size == 2) {
            path.lineTo(pts[1].first, pts[1].second)
        } else if (pts.size > 2) {
            // làm mượt đường vuốt bằng đường cong bậc hai qua trung điểm
            for (i in 1 until pts.size - 1) {
                val mx = (pts[i].first + pts[i + 1].first) / 2
                val my = (pts[i].second + pts[i + 1].second) / 2
                path.quadTo(pts[i].first, pts[i].second, mx, my)
            }
            path.lineTo(pts.last().first, pts.last().second)
        }
        val dur = m.dur.toLong().coerceIn(1L, GestureDescription.getMaxGestureDuration())
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                h.postDelayed({ step(m, n + 1, tk) }, m.gap.toLong())
            }
            override fun onCancelled(d: GestureDescription?) { endRun() }
        }, null)
        if (!ok) endRun()
    }
}

// Nút Home ảo kiểu iPhone: hình vuông bo góc, vòng tròn và chấm ở giữa
private class HomeView(c: Context, val pad: Int) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC141C33.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f * d
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt() }
    override fun onDraw(cv: Canvas) {
        val l = pad.toFloat(); val t = pad.toFloat()
        val r = width - l; val b = height - t
        val s = r - l
        cv.drawRoundRect(l, t, r, b, s * 0.26f, s * 0.26f, bgP)
        val cx = (l + r) / 2; val cy = (t + b) / 2
        cv.drawCircle(cx, cy, s * 0.30f, ring)
        cv.drawCircle(cx, cy, s * 0.15f, dot)
    }
}
