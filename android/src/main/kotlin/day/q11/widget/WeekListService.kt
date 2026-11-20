package day.q11.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

/** 0.7.0 한 주 위젯 목록: 고른 날의 ◆ 프로젝트 줄 · 할 일 줄(오늘 할 일 위젯과 같은 줄 모양). */
@Suppress("DEPRECATION")
class WeekListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WeekListFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

@Suppress("DEPRECATION")
internal class WeekListFactory(
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
        val days = WeekWidgetProvider.days(feed)
        val out = ArrayList<JSONObject>()
        if (days != null) {
            val day = days.optJSONObject(WeekWidgetProvider.selIndex(context, widgetId, days))
            val all = day?.optJSONArray("rows")
            if (all != null) {
                for (i in 0 until all.length()) {
                    out.add(all.optJSONObject(i) ?: continue)
                }
            }
        }
        rows = out
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val r = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.q11_widget_prow)
        return if (r.optString("t") == "p") Q11Rows.project(context, theme, level, r) else Q11Rows.task(context, theme, level, r)
    }

    override fun getLoadingView(): RemoteViews? = null

    /** ◆ 줄 · 할 일 줄 × 글자 크기 3단계 */
    override fun getViewTypeCount(): Int = 6

    override fun getItemId(position: Int): Long {
        val r = rows.getOrNull(position) ?: return position.toLong()
        return (r.optString("t") + ":" + r.optString("id")).hashCode().toLong()
    }

    override fun hasStableIds(): Boolean = true
}
