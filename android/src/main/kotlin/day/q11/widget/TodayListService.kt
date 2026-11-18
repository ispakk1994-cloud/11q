package day.q11.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

/** 위젯 목록 줄(묶음 머리 · 할 일 줄)을 만든다. */
@Suppress("DEPRECATION")
class TodayListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayListFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

@Suppress("DEPRECATION")
internal class TodayListFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<JSONObject> = emptyList()
    private var theme = WidgetTheme(null)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val feed = WidgetStore.feed(context)
        theme = WidgetTheme(feed?.optJSONObject("theme"))
        val all = feed?.optJSONArray("rows")
        val compact = TodayWidgetProvider.isCompact(context, widgetId)
        val out = ArrayList<JSONObject>()
        if (all != null) {
            for (i in 0 until all.length()) {
                val r = all.optJSONObject(i) ?: continue
                if (compact && r.optString("t") == "h") continue
                out.add(r)
            }
        }
        rows = out
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val r = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.q11_widget_head)
        return if (r.optString("t") == "h") head(r) else row(r)
    }

    private fun head(r: JSONObject): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.q11_widget_head)
        v.setTextViewText(R.id.q11_head_text, r.optString("text", ""))
        val color = when (r.optString("tone")) {
            "danger" -> theme.danger
            "overdue" -> theme.overdue
            else -> theme.ink
        }
        v.setTextColor(R.id.q11_head_text, color)
        return v
    }

    private fun row(r: JSONObject): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.q11_widget_row)
        val id = r.optString("id", "")
        val done = id.isNotEmpty() && WidgetStore.isPendingDone(context, id)

        // 체크칸
        v.setImageViewResource(R.id.q11_chk_box, if (theme.square) R.drawable.q11_chk_square else R.drawable.q11_chk_round)
        v.setInt(R.id.q11_chk_box, "setColorFilter", theme.chkBorder)
        v.setImageViewResource(R.id.q11_chk_fill, if (theme.square) R.drawable.q11_chk_square_fill else R.drawable.q11_chk_round_fill)
        v.setInt(R.id.q11_chk_fill, "setColorFilter", theme.chkDone)
        v.setInt(R.id.q11_chk_mark, "setColorFilter", theme.chkDoneInk)
        v.setViewVisibility(R.id.q11_chk_fill, if (done) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.q11_chk_mark, if (done) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.q11_chk_box, if (done) View.GONE else View.VISIBLE)

        // 지금 칩
        val now = r.optBoolean("now", false) && !done
        v.setViewVisibility(R.id.q11_now, if (now) View.VISIBLE else View.GONE)
        if (now) {
            v.setTextViewText(R.id.q11_now, r.optString("nowText", "지금"))
            TodayWidgetProvider.tint(v, R.id.q11_now, theme.accent, TodayWidgetProvider.chipShape(theme))
            v.setTextColor(R.id.q11_now, theme.accentInk)
        }

        // 긴급 · 중요 칩
        val flag = r.optString("flag", "")
        val showFlag = !done && (flag == "urgent" || flag == "important")
        v.setViewVisibility(R.id.q11_flag, if (showFlag) View.VISIBLE else View.GONE)
        if (showFlag) {
            val c = if (flag == "urgent") theme.danger else theme.warn
            v.setTextViewText(R.id.q11_flag, r.optString("flagText", ""))
            v.setTextColor(R.id.q11_flag, c)
            TodayWidgetProvider.tint(v, R.id.q11_flag, WidgetTheme.alpha(c, if (flag == "urgent") 0.18f else 0.16f), TodayWidgetProvider.chipShape(theme))
        }

        // 제목 · 오른쪽 날짜
        v.setTextViewText(R.id.q11_title, r.optString("title", ""))
        v.setTextColor(R.id.q11_title, if (done) theme.doneTitle else theme.ink)
        val flags = Paint.ANTI_ALIAS_FLAG or (if (done) Paint.STRIKE_THRU_TEXT_FLAG else 0)
        v.setInt(R.id.q11_title, "setPaintFlags", flags)
        v.setTextViewText(R.id.q11_meta, r.optString("meta", ""))
        v.setTextColor(R.id.q11_meta, if (r.optBoolean("over", false) && !done) theme.overdue else theme.meta)

        // 누르기: 체크칸 = 끝냄/되돌림 · 나머지 = 앱 열기
        v.setOnClickFillInIntent(
            R.id.q11_chk,
            Intent().putExtra(TodayWidgetProvider.EXTRA_KIND, "check").putExtra(TodayWidgetProvider.EXTRA_ID, id)
        )
        v.setOnClickFillInIntent(
            R.id.q11_body,
            Intent().putExtra(TodayWidgetProvider.EXTRA_KIND, "open").putExtra(TodayWidgetProvider.EXTRA_ID, id)
        )
        return v
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long {
        val r = rows.getOrNull(position) ?: return position.toLong()
        val key = if (r.optString("t") == "h") "h:" + r.optString("text") else "r:" + r.optString("id")
        return key.hashCode().toLong()
    }

    override fun hasStableIds(): Boolean = true
}
