package day.q11.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * 위젯이 보여 줄 것(앱이 적어 둔 목록 · 테마 · 글자)과, 위젯에서 막 체크한 일(서버에 가기 전)을 폰에 둔다.
 * 앱(Dart)이 save로 목록 JSON을 넘기면 여기 적고 위젯을 다시 그린다.
 */
internal object WidgetStore {
    private const val PREFS = "oneone_q_widget"
    private const val KEY_FEED = "feed"
    private const val KEY_PENDING = "pending"
    const val KEY_DISPATCHER = "dispatcher"
    const val KEY_CALLBACK = "callback"

    /** 체크한 뒤 이만큼 지나도 목록이 안 바뀌면 체크 표시를 푼다(서버에 못 간 경우). */
    private const val PENDING_TTL_MS = 3 * 60 * 1000L

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun feedRaw(context: Context): String? = prefs(context).getString(KEY_FEED, null)

    fun feed(context: Context): JSONObject? =
        try {
            feedRaw(context)?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }

    fun saveFeed(context: Context, json: String) {
        prefs(context).edit().putString(KEY_FEED, json).apply()
        prunePending(context, rowIds(JSONObject(json)))
    }

    fun clearFeed(context: Context, json: String?) {
        val p = prefs(context).edit().remove(KEY_PENDING)
        if (json.isNullOrBlank()) p.remove(KEY_FEED) else p.putString(KEY_FEED, json)
        p.apply()
    }

    fun handles(context: Context): Pair<Long, Long> {
        val p = prefs(context)
        return Pair(p.getLong(KEY_DISPATCHER, 0L), p.getLong(KEY_CALLBACK, 0L))
    }

    fun saveHandles(context: Context, dispatcher: Long, callback: Long) {
        prefs(context).edit().putLong(KEY_DISPATCHER, dispatcher).putLong(KEY_CALLBACK, callback).apply()
    }

    // ── 위젯에서 막 체크한 일 ─────────────────────────────

    private fun pendingObj(context: Context): JSONObject =
        try {
            JSONObject(prefs(context).getString(KEY_PENDING, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    /** 위젯에서 체크해 '끝냄'으로 보이는 중인지. */
    fun isPendingDone(context: Context, id: String): Boolean {
        val o = pendingObj(context).optJSONObject(id) ?: return false
        if (System.currentTimeMillis() - o.optLong("at", 0L) > PENDING_TTL_MS) return false
        return o.optBoolean("done", false)
    }

    fun setPending(context: Context, id: String, done: Boolean?) {
        val all = pendingObj(context)
        if (done == null) {
            all.remove(id)
        } else {
            all.put(id, JSONObject().put("done", done).put("at", System.currentTimeMillis()))
        }
        prefs(context).edit().putString(KEY_PENDING, all.toString()).apply()
    }

    /** 새 목록에 없는 일(끝낸 일)과 오래된 표시는 지운다. */
    private fun prunePending(context: Context, ids: Set<String>) {
        val all = pendingObj(context)
        val now = System.currentTimeMillis()
        val keep = JSONObject()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = all.optJSONObject(k) ?: continue
            if (now - o.optLong("at", 0L) > PENDING_TTL_MS) continue
            if (!ids.contains(k)) continue
            keep.put(k, o)
        }
        prefs(context).edit().putString(KEY_PENDING, keep.toString()).apply()
    }

    private fun rowIds(feed: JSONObject): Set<String> {
        val out = HashSet<String>()
        val rows = feed.optJSONArray("rows") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val id = r.optString("id", "")
            if (id.isNotEmpty()) out.add(id)
        }
        return out
    }
}

/** 앱 테마(App State 토큰)를 위젯 색으로. 값이 없으면 유리. */
internal class WidgetTheme(o: JSONObject?) {
    val mode: String = o?.optString("mode", "glass")?.ifBlank { "glass" } ?: "glass"
    val ink: Int = color(o, "ink", 0xFFFFFFFF)
    val meta: Int = color(o, "meta", 0x9EFFFFFF)
    val accent: Int = color(o, "accent", 0xFF6C5AFF)
    val accentInk: Int = color(o, "accentInk", 0xFFFFFFFF)
    val overdue: Int = color(o, "overdue", 0xFFFFB86C)
    val danger: Int = color(o, "danger", 0xFFFFB4B4)
    val warn: Int = color(o, "warn", 0xFFFFCF7A)
    val chkBorder: Int = color(o, "chkBorder", 0x66FFFFFF)
    val chkDone: Int = color(o, "chkDone", 0xFF6C5AFF)
    val chkDoneInk: Int = color(o, "chkDoneInk", 0xFFFFFFFF)
    val doneTitle: Int = color(o, "doneTitle", 0x80FFFFFF)
    val tagRadius: Float = num(o, "tag", 6.0)
    val chkRadius: Float = num(o, "chk", 7.0)

    // ── 작은 창(0.2.0 · 위젯 ＋적기) · 앱의 확인 창 · 입력칸 · 보조 버튼 · 토스트와 같은 토큰 ──
    /** 창 바탕 dlgFillHex · 테두리 cardBorderHex × borderWidth · 모서리 dlgRadius */
    val sheet: Int = color(o, "sheet", 0xF7101014)
    val line: Int = color(o, "line", 0x38FFFFFF)
    val borderW: Float = num(o, "bw", 1.0)
    val dlgRadius: Float = num(o, "dlgR", 18.0)
    /** 입력칸 inputFillHex · inputBorderHex(적는 중엔 accentHex) · 모서리 ctlRadius */
    val field: Int = color(o, "field", 0x73000000)
    val fieldLine: Int = color(o, "fieldLine", 0x58FFFFFF)
    val ctlRadius: Float = num(o, "ctlR", 12.0)
    /** 보조 버튼(닫기 · 말로 적기) ctlFillHex · ctlBorderHex · ctlInkHex */
    val ctl: Int = color(o, "ctl", 0x1CFFFFFF)
    val ctlLine: Int = color(o, "ctlLine", 0x38FFFFFF)
    val ctlInk: Int = color(o, "ctlInk", 0xFFFFFFFF)
    /** 주 버튼 테두리 accentLineHex */
    val accentLine: Int = color(o, "accentLine", 0x00000000)
    /** 토스트 alertFillHex · 되돌리기 linkHex · 모서리 pillRadius */
    val alertFill: Int = color(o, "alert", 0xFC221F2E)
    val link: Int = color(o, "link", 0xFFB8AFFF)
    val pillRadius: Float = num(o, "pillR", 999.0)
    /** 그림자 shadowHex · shadowX · shadowY · shadowBlur */
    val shadow: Int = color(o, "shadow", 0x00000000)
    val shadowX: Float = num(o, "shX", 0.0)
    val shadowY: Float = num(o, "shY", 0.0)
    val shadowBlur: Float = num(o, "shBlur", 0.0)

    val square: Boolean get() = chkRadius <= 1f

    val bgRes: Int
        get() = when (mode) {
            "paper" -> R.drawable.q11_bg_paper
            "neon" -> R.drawable.q11_bg_neon
            "swiss" -> R.drawable.q11_bg_swiss
            "diary" -> R.drawable.q11_bg_diary
            "white", "lightglass" -> R.drawable.q11_bg_white
            else -> R.drawable.q11_bg_glass
        }

    companion object {
        private fun num(o: JSONObject?, key: String, def: Double): Float {
            if (o == null || !o.has(key)) return def.toFloat()
            val v = o.optDouble(key, def)
            return (if (v.isNaN()) def else v).toFloat()
        }

        private fun color(o: JSONObject?, key: String, def: Long): Int {
            if (o == null || !o.has(key)) return def.toInt()
            return try {
                when (val v = o.get(key)) {
                    is Number -> v.toLong().toInt()
                    is String -> Color.parseColor(v)
                    else -> def.toInt()
                }
            } catch (e: Exception) {
                def.toInt()
            }
        }

        /** 같은 색을 알파만 줄여서(칩 바탕 · 긴급 18% · 중요 16%). */
        fun alpha(color: Int, a: Float): Int {
            val base = Color.alpha(color) / 255f
            val na = (base * a * 255f).toInt().coerceIn(0, 255)
            return Color.argb(na, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
