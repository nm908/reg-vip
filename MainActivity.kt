package com.touchassist.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private lateinit var status: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var macroBox: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private var splash: FrameLayout? = null
    private val rings = mutableListOf<ObjectAnimator>()
    private var splashGone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootFrame = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        rootFrame.addView(buildContent())
        if (savedInstanceState == null) {
            val sp = buildSplash()
            splash = sp
            rootFrame.addView(sp)
        }
        setContentView(rootFrame)
    }

    override fun onResume() {
        super.onResume()
        val on = accessibilityOn()
        status.text = if (on) "Trợ năng: ĐÃ BẬT" else "Trợ năng: CHƯA BẬT - bấm nút bên dưới để bật Touch Assist"
        status.setTextColor(if (on) 0xFF22C55E.toInt() else 0xFFF59E0B.toInt())
        val ov = Settings.canDrawOverlays(this)
        overlayStatus.text = if (ov) "Hiển thị trên ứng dụng khác: ĐÃ CẤP" else "Hiển thị trên ứng dụng khác: CHƯA CẤP"
        overlayStatus.setTextColor(if (ov) 0xFF22C55E.toInt() else 0xFFF59E0B.toInt())
        Prefs.sp(this).edit().putBoolean("overlay_granted", ov).apply()
        renderMacros()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun accessibilityOn(): Boolean {
        val v = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return v.contains("$packageName/${TouchAssistService::class.java.name}")
    }

    // Ảnh nền của bạn: đặt file bg_user.png (hoặc .jpg) vào app/src/main/res/drawable rồi build lại
    private fun userBg(): Drawable? {
        val id = resources.getIdentifier("bg_user", "drawable", packageName)
        return if (id != 0) resources.getDrawable(id, theme) else null
    }

    // ---------- Splash động ----------
    private fun buildSplash(): FrameLayout {
        val f = FrameLayout(this).apply {
            background = Ui.splitBg(); isClickable = true
            setOnClickListener { dismissSplash() } // chạm để bỏ qua
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val box = FrameLayout(this)
        val ringSize = dp(96)

        repeat(3) { idx ->
            val r = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(3), Ui.CYAN) }
                alpha = 0f
            }
            box.addView(r, FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER))
            rings.add(ObjectAnimator.ofPropertyValuesHolder(
                r,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 3f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 3f),
                PropertyValuesHolder.ofFloat("alpha", 0.7f, 0f)
            ).apply { duration = 1600; startDelay = 500L + idx * 450L; repeatCount = ValueAnimator.INFINITE })
        }

        val core = FrameLayout(this).apply { scaleX = 0.2f; scaleY = 0.2f; alpha = 0f }
        core.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setStroke(dp(4), Ui.CYAN); setColor(0xFF0B1B3F.toInt())
            }
        }, FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER))
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.CYAN) }
        }
        core.addView(dot, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        box.addView(core, FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER))

        val title = Ui.text(this, "Touch Assist", 34f, Ui.CYAN, true).apply {
            gravity = Gravity.CENTER; alpha = 0f; translationY = dp(30).toFloat()
        }
        val sub = Ui.text(this, "Một chạm nhỏ - Hỗ trợ lớn", 15f, Color.WHITE).apply {
            gravity = Gravity.CENTER; alpha = 0f; translationY = dp(30).toFloat()
        }
        col.addView(box, LinearLayout.LayoutParams(dp(300), dp(300)))
        col.addView(title); col.addView(sub)
        f.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))

        f.post {
            core.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650)
                .setInterpolator(OvershootInterpolator()).start()
            ObjectAnimator.ofPropertyValuesHolder(
                dot,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 0.6f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 0.6f)
            ).apply { duration = 700; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() }
            title.animate().alpha(1f).translationY(0f).setStartDelay(500).setDuration(500).start()
            sub.animate().alpha(1f).translationY(0f).setStartDelay(800).setDuration(500).start()
            rings.forEach { it.start() }
            Handler(Looper.getMainLooper()).postDelayed({ dismissSplash() }, 2600)
        }
        return f
    }

    private fun dismissSplash() {
        if (splashGone) return
        splashGone = true
        rings.forEach { it.cancel() }
        splash?.let { s -> s.animate().alpha(0f).setDuration(400).withEndAction { rootFrame.removeView(s) }.start() }
    }

    // ---------- Giao diện chính ----------
    private fun LinearLayout.addTop(v: View) {
        addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
    }

    private fun card(body: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@MainActivity, 0x990B1430.toInt(), 0xFF1E3A8A.toInt(), 18)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        c.addView(Ui.text(this, title, 18f, Ui.CYAN, true))
        body.addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        return c
    }

    private fun buildContent(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = FrameLayout(this).apply { background = userBg() ?: Ui.splitBg() }
        header.addView(View(this).apply { setBackgroundColor(0x66000000) })
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(18)) }
        ht.addView(Ui.text(this, "Touch Assist", 34f, Ui.CYAN, true))
        ht.addView(Ui.text(this, "Hỗ trợ thao tác - Dễ dàng hơn mỗi ngày", 14f, Color.WHITE))
        header.addView(ht, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        col.addView(header, LinearLayout.LayoutParams(MATCH, dp(190)))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(40)) }

        val perm = card(body, "Quyền")
        status = Ui.text(this, "", 14f, Color.WHITE)
        perm.addView(status)
        perm.addTop(Ui.button(this, "Mở cài đặt Trợ năng") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        overlayStatus = Ui.text(this, "", 14f, Color.WHITE)
        perm.addView(overlayStatus)
        perm.addTop(Ui.button(this, "Cấp quyền hiển thị trên ứng dụng khác") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val home = card(body, "Nút Home ảo")
        home.addTop(switchRow("Bật nút Home ảo", "enabled", false, false))
        home.addView(slider("Kích thước nút", "size_dp", 40, 100, 60, " dp"))
        home.addView(slider("Độ trong suốt", "alpha", 20, 100, 80, " %"))
        home.addTop(switchRow("Vùng chạm lớn", "big_touch", true, false))
        home.addView(slider("Giữ nút bao lâu mới kích hoạt (chống chạm nhầm, 0 = tắt)", "hold_ms", 0, 1000, 0, " ms"))
        home.addTop(switchRow("Chữ to (người lớn tuổi)", "big_text", false, true))

        val zn = card(body, "Vùng vuốt nhẹ")
        zn.addView(Ui.text(this, "Dải mỏng ở mép màn hình. Chế độ trực tiếp: vuốt nhẹ trong dải, ngón tay ảo vuốt thẳng và đi xa gấp nhiều lần ngay lúc bạn vuốt. Chỉ chạy khi bạn vuốt. Dải che phần màn hình bên dưới nên đừng đặt quá rộng.", 13f, Ui.GREY))
        zn.addTop(switchRow("Bật vùng vuốt nhẹ", "zone_on", false, false))
        zn.addTop(switchRow("Đặt dải bên trái (mặc định bên phải)", "zone_left", false, false))
        zn.addTop(switchRow("Chế độ trực tiếp (đi theo ngón tay, cần Android 8+)", "zone_live", true, false))
        zn.addView(slider("Vuốt gấp bao nhiêu lần (30 = gấp 3 lần)", "zone_gain", 10, 60, 30, ""))
        zn.addView(slider("Độ trễ mỗi bước (nhỏ hơn = sát tay hơn, máy yếu có thể giật)", "zone_step", 10, 60, 10, " ms"))
        zn.addView(slider("Độ rộng dải", "zone_w", 30, 120, 60, " dp"))
        zn.addView(slider("Chiều cao dải", "zone_h", 20, 90, 50, " %"))
        zn.addView(slider("Quãng vuốt đầy đủ (khi tắt chế độ trực tiếp)", "zone_dist", 200, 1500, 600, " px"))
        zn.addView(slider("Thời gian vuốt (khi tắt chế độ trực tiếp)", "zone_dur", 100, 800, 300, " ms"))

        val mc = card(body, "Macro của bạn")
        macroBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mc.addView(macroBox)
        mc.addTop(Ui.button(this, "+ Tạo macro mới") { startActivity(Intent(this, MacroEditorActivity::class.java)) })

        val guide = card(body, "Cách dùng")
        guide.addView(Ui.text(this,
            "1. Cấp hai quyền ở trên, bật Trợ năng cho Touch Assist.\n" +
                "2. Bật nút Home ảo, kéo đến chỗ thuận tay.\n" +
                "3. Chạm nút Home ảo: menu hiện giữa màn hình (Trang chủ, Quay lại, Đa nhiệm, Thông báo và các macro của bạn).\n" +
                "4. Chạm ra ngoài để đóng menu. Chạm nút đỏ để dừng macro đang chạy.\n" +
                "Trên realme: Cài đặt > Pin > cho phép Touch Assist chạy nền và tự khởi động.",
            13f, Ui.GREY))

        col.addView(body)
        return ScrollView(this).apply { addView(col) }
    }

    private fun renderMacros() {
        macroBox.removeAllViews()
        val list = Prefs.macros(this)
        if (list.isEmpty()) macroBox.addView(Ui.text(this, "Chưa có macro. Bấm nút bên dưới để tạo.", 14f, Ui.GREY))
        for (m in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0)
            }
            row.addView(Ui.text(this, m.name, 16f, Color.WHITE), LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(Ui.button(this, "Thử") {
                val ok = TouchAssistService.instance?.scheduleTest(m.id) ?: false
                Toast.makeText(
                    this,
                    if (ok) "Sẽ chạy sau 3 giây - hãy chuyển sang màn hình cần thử" else "Hãy bật Trợ năng và Nút Home ảo trước",
                    Toast.LENGTH_LONG
                ).show()
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) })
            row.addView(Ui.button(this, "Xóa") {
                Prefs.saveMacros(this, Prefs.macros(this).filter { it.id != m.id })
                renderMacros()
            }, LinearLayout.LayoutParams(WRAP, WRAP))
            macroBox.addView(row)
        }
    }

    private fun switchRow(label: String, key: String, def: Boolean, recreateAfter: Boolean) = Switch(this).apply {
        text = label; setTextColor(Color.WHITE)
        isChecked = Prefs.sp(this@MainActivity).getBoolean(key, def)
        setPadding(0, dp(12), 0, dp(4))
        setOnCheckedChangeListener { _, c ->
            Prefs.sp(this@MainActivity).edit().putBoolean(key, c).apply()
            if (recreateAfter) recreate()
        }
    }

    private fun slider(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String): View {
        val s = Prefs.sp(this)
        val cur = s.getInt(key, def)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        val tv = Ui.text(this, "$label: $cur$unit", 14f, Color.WHITE)
        val sb = SeekBar(this).apply { max = hi - lo; progress = cur - lo }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { tv.text = "$label: ${p + lo}$unit" }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {
                s.edit().putInt(key, (b?.progress ?: 0) + lo).apply()
            }
        })
        box.addView(tv); box.addView(sb)
        return box
    }
}
