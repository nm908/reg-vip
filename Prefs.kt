package com.touchassist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Macro do người dùng vẽ: pts là toạ độ chuẩn hoá theo màn hình (0..1). 1 điểm = chạm/giữ. */
data class MacroItem(
    val id: Long, val name: String, val pts: List<Pair<Float, Float>>,
    val dur: Int, val rep: Int, val gap: Int
)

object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("touch_assist", Context.MODE_PRIVATE)

    private fun defaults() = mutableListOf(
        MacroItem(1, "Cuộn xuống", listOf(0.5f to 0.7f, 0.5f to 0.3f), 400, 1, 300),
        MacroItem(2, "Cuộn lên", listOf(0.5f to 0.3f, 0.5f to 0.7f), 400, 1, 300)
    )

    fun macros(c: Context): MutableList<MacroItem> {
        val raw = sp(c).getString("macros", null) ?: return defaults()
        val out = mutableListOf<MacroItem>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val p = o.getJSONArray("pts")
                val pts = (0 until p.length()).map {
                    val q = p.getJSONArray(it)
                    q.getDouble(0).toFloat() to q.getDouble(1).toFloat()
                }
                out.add(MacroItem(o.getLong("id"), o.getString("name"), pts, o.getInt("dur"), o.getInt("rep"), o.getInt("gap")))
            }
        } catch (_: Exception) {
            return defaults()
        }
        return out
    }

    fun saveMacros(c: Context, list: List<MacroItem>) {
        val a = JSONArray()
        for (m in list) {
            val pts = JSONArray()
            for (q in m.pts) pts.put(JSONArray().put(q.first.toDouble()).put(q.second.toDouble()))
            a.put(
                JSONObject().put("id", m.id).put("name", m.name).put("dur", m.dur)
                    .put("rep", m.rep).put("gap", m.gap).put("pts", pts)
            )
        }
        sp(c).edit().putString("macros", a.toString()).apply()
    }
}
