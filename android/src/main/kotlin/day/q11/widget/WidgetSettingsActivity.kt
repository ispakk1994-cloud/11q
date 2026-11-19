package day.q11.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

/**
 * 위젯을 꾹 눌러 `설정` → 글자 크기(0.3.0 · 시안 WidgetNext ⑤): 보통 · 크게(처음) · 아주 크게.
 * 고르면 바로 저장하고 놓인 위젯을 다시 그린다 · 아래 미리 보기는 지금 목록의 앞 두 줄(없으면 보기 글).
 * 위젯을 처음 놓을 때 이 창이 뜨는 폰(안드로이드 11 이하)에서도 그냥 닫으면 위젯은 놓인다.
 */
class WidgetSettingsActivity : Activity() {

    private lateinit var th: WidgetTheme
    private lateinit var brush: SheetPaint
    private lateinit var preview: LinearLayout
    private lateinit var segs: Array<TextView>
    private var feed: JSONObject? = null
    private var en = false
    private var level = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        feed = WidgetStore.feed(this)
        val lang = feed?.optString("lang", "") ?: ""
        en = if (lang.isNotEmpty()) lang == "en" else Locale.getDefault().language == "en"
        th = WidgetTheme(feed?.optJSONObject("theme"))
        brush = SheetPaint(this, th)
        level = WidgetStore.sizeLevel(this)

        setContentView(R.layout.q11_widget_settings)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        setFinishOnTouchOutside(true)

        brush.sheet(findViewById(R.id.q11_ws_sheet))
        brush.logo(findViewById(R.id.q11_ws_logo))
        findViewById<TextView>(R.id.q11_ws_title).apply {
            text = t("title")
            setTextColor(th.ink)
        }
        findViewById<ImageView>(R.id.q11_ws_close).apply {
            brush.ctl(this)
            contentDescription = t("close")
            setOnClickListener { finish() }
        }
        segs = arrayOf(findViewById(R.id.q11_ws_s0), findViewById(R.id.q11_ws_s1), findViewById(R.id.q11_ws_s2))
        segs.forEachIndexed { i, v ->
            v.text = t("s$i")
            v.setOnClickListener { pick(i) }
        }
        preview = findViewById(R.id.q11_ws_preview)
        getDrawable(th.bgRes)?.let { brush.setBg(preview, it) }
        findViewById<TextView>(R.id.q11_ws_help).apply {
            text = t("help")
            setTextColor(th.meta)
        }
        findViewById<TextView>(R.id.q11_ws_done).apply {
            text = t("done")
            brush.primary(this)
            setOnClickListener { finish() }
        }
        insets()
        paintSegs()
        fillPreview()
    }

    private fun pick(i: Int) {
        if (i == level) return
        level = i
        WidgetStore.setSizeLevel(this, i)
        TodayWidgetProvider.refreshAll(this)
        paintSegs()
        fillPreview()
    }

    private fun paintSegs() {
        segs.forEachIndexed { i, v ->
            if (i == level) brush.primary(v) else brush.ctl(v)
            v.isSelected = i == level
        }
    }

    private class Sample(val title: String, val meta: String, val now: Boolean, val over: Boolean)

    private fun samples(): List<Sample> {
        val out = ArrayList<Sample>()
        val rows = feed?.optJSONArray("rows")
        if (rows != null) {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (r.optString("t") != "r") continue
                out.add(Sample(r.optString("title", ""), r.optString("meta", ""), r.optBoolean("now", false), r.optBoolean("over", false)))
                if (out.size == 2) break
            }
        }
        if (out.isEmpty()) {
            out.add(Sample(t("pv1"), "15:00", true, false))
            out.add(Sample(t("pv2"), "18:00", false, false))
        }
        return out
    }

    /** 위젯과 같은 줄 모양(그 크기 단계의 레이아웃 그대로)으로 미리 보기. */
    private fun fillPreview() {
        preview.removeAllViews()
        for (s in samples()) {
            val v = layoutInflater.inflate(WidgetSize.ROW[level], preview, false)
            val box = v.findViewById<ImageView>(R.id.q11_chk_box)
            box.setImageResource(if (th.square) R.drawable.q11_chk_square else R.drawable.q11_chk_round)
            box.setColorFilter(th.chkBorder or 0xFF000000.toInt())
            box.imageAlpha = (th.chkBorder ushr 24) and 0xFF
            if (s.now) {
                v.findViewById<TextView>(R.id.q11_now).apply {
                    visibility = View.VISIBLE
                    text = if (en) "Now" else "지금"
                    setTextColor(th.accentInk)
                    setBackgroundResource(
                        if (TodayWidgetProvider.chipShape(th) == "square") R.drawable.q11_square_white else R.drawable.q11_round_white
                    )
                    backgroundTintList = ColorStateList.valueOf(th.accent)
                }
            }
            v.findViewById<TextView>(R.id.q11_title).apply {
                text = s.title
                setTextColor(th.ink)
            }
            v.findViewById<TextView>(R.id.q11_meta).apply {
                text = s.meta
                setTextColor(if (s.over) th.overdue else th.meta)
            }
            preview.addView(v)
        }
    }

    /** 이 창이 화면 아래로 그려지면(안드로이드 15 이상 꽉 찬 화면) 그만큼 올린다. */
    private fun insets() {
        val root = findViewById<View>(R.id.q11_ws_root)
        val base = root.paddingBottom
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.navigationBars()).bottom
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + b)
                ins
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    private fun t(k: String): String = (if (en) EN else KO)[k] ?: k

    companion object {
        private val KO = mapOf(
            "title" to "위젯 글자 크기",
            "s0" to "보통",
            "s1" to "크게",
            "s2" to "아주 크게",
            "help" to "위젯을 늘리면 줄이 더 보여요",
            "done" to "완료",
            "close" to "닫기",
            "pv1" to "월말 보고서 초안",
            "pv2" to "팀 회식 장소 예약"
        )

        private val EN = mapOf(
            "title" to "Widget text size",
            "s0" to "Normal",
            "s1" to "Large",
            "s2" to "Extra large",
            "help" to "Make the widget taller to see more",
            "done" to "Done",
            "close" to "Close",
            "pv1" to "Draft the monthly report",
            "pv2" to "Book the team dinner"
        )
    }
}
