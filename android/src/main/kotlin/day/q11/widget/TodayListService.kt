package day.q11.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
    private var level = 1

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val feed = WidgetStore.feed(context)
        theme = WidgetTheme(feed?.optJSONObject("theme"))
        level = WidgetStore.sizeLevel(context)
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
        val r = rows.getOrNull(position) ?: return RemoteViews(context.packageName, WidgetSize.HEAD[level])
        return if (r.optString("t") == "h") head(r) else row(r)
    }

    // 0.7.0: 줄 모양은 한 주 위젯과 같이 Q11Rows(같은 코드 · 오늘 할 일 줄은 그대로)
    private fun head(r: JSONObject): RemoteViews = Q11Rows.head(context, theme, level, r)

    private fun row(r: JSONObject): RemoteViews = Q11Rows.task(context, theme, level, r)

    override fun getLoadingView(): RemoteViews? = null

    /** 묶음 머리 · 줄 × 글자 크기 3단계(크기를 바꾸면 모양이 바뀌므로 넉넉히). */
    override fun getViewTypeCount(): Int = 6

    override fun getItemId(position: Int): Long {
        val r = rows.getOrNull(position) ?: return position.toLong()
        val key = if (r.optString("t") == "h") "h:" + r.optString("text") else "r:" + r.optString("id")
        return key.hashCode().toLong()
    }

    override fun hasStableIds(): Boolean = true
}
