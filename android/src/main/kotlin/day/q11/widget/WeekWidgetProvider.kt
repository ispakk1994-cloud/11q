package day.q11.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

/**
 * 0.7.0 위젯 5 · 한 주(시안 WidgetMoreGalaxy · 4×2 · 늘이면 줄이 더 보임).
 * - 머리: 11Q · 한 주 · 고른 날 `목 11/19 · 3개 남음` · ＋(고른 날 마감으로 작은 창)
 * - 띠: 오늘부터 7일(점 = 남은 일 · 첫 점 빨강 = 긴급 · ◆ = 프로젝트 마감) · 날을 누르면 그날 일(위젯마다 기억)
 * - 목록: 앱이 넘긴 week.days[i].rows(◆ 프로젝트 줄 → 할 일 줄) · 체크칸 = 오늘 위젯과 같은 끝내기
 * - 앱을 한 번도 안 열었거나 옛 앱(7판 전)이면 `11Q를 한 번 열면 한 주가 보여요`
 */
@Suppress("DEPRECATION") // 목록 위젯(RemoteViewsService)은 안드로이드 16에서도 그대로 동작
class WeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.q11_wk_list)
        WidgetJobs.schedulePeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.q11_wk_list)
    }

    override fun onEnabled(context: Context) {
        WidgetJobs.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        if (!Q11Widgets.needsPeriodic(context)) WidgetJobs.cancelPeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetStore.clearWeekSel(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DAY -> {
                val wid = intent.getIntExtra(EXTRA_WID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) return
                WidgetStore.setWeekSel(context, wid, intent.getStringExtra(EXTRA_KEY) ?: "")
                val m = AppWidgetManager.getInstance(context)
                render(context, m, wid)
                m.notifyAppWidgetViewDataChanged(wid, R.id.q11_wk_list)
                return
            }
            TodayWidgetProvider.ACTION_ITEM -> {
                // 할 일 줄 · ◆ 줄을 누르면 앱의 `한 주`로(앱 WidgetCatch 7판 'week')
                Q11Widgets.handleItem(context, intent, "week")
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_DAY = "day.q11.widget.WEEK_DAY"
        const val EXTRA_WID = "q11_wid"
        const val EXTRA_KEY = "q11_day_key"

        private val CELL_O = intArrayOf(R.id.q11_d0_o, R.id.q11_d1_o, R.id.q11_d2_o, R.id.q11_d3_o, R.id.q11_d4_o, R.id.q11_d5_o, R.id.q11_d6_o)
        private val CELL_I = intArrayOf(R.id.q11_d0_i, R.id.q11_d1_i, R.id.q11_d2_i, R.id.q11_d3_i, R.id.q11_d4_i, R.id.q11_d5_i, R.id.q11_d6_i)
        private val WD = intArrayOf(R.id.q11_d0_wd, R.id.q11_d1_wd, R.id.q11_d2_wd, R.id.q11_d3_wd, R.id.q11_d4_wd, R.id.q11_d5_wd, R.id.q11_d6_wd)
        private val DAY = intArrayOf(R.id.q11_d0_day, R.id.q11_d1_day, R.id.q11_d2_day, R.id.q11_d3_day, R.id.q11_d4_day, R.id.q11_d5_day, R.id.q11_d6_day)
        private val DOTS = intArrayOf(R.id.q11_d0_dots, R.id.q11_d1_dots, R.id.q11_d2_dots, R.id.q11_d3_dots, R.id.q11_d4_dots, R.id.q11_d5_dots, R.id.q11_d6_dots)
        private val DIA = intArrayOf(R.id.q11_d0_dia, R.id.q11_d1_dia, R.id.q11_d2_dia, R.id.q11_d3_dia, R.id.q11_d4_dia, R.id.q11_d5_dia, R.id.q11_d6_dia)

        /** 날 칸 글자 크기(보통 · 크게 · 아주 크게) */
        private val WD_SIZE = floatArrayOf(10.5f, 11.5f, 12.5f)
        private val DAY_SIZE = floatArrayOf(15.5f, 17f, 18.5f)

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, WeekWidgetProvider::class.java))

        fun refreshAll(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            for (id in ids) render(context, manager, id)
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.q11_wk_list)
            return ids.size
        }

        fun refreshList(context: Context) {
            val ids = widgetIds(context)
            if (ids.isNotEmpty()) {
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.q11_wk_list)
            }
        }

        /** 앱이 넘긴 7일(없으면 null = 옛 앱 · 아직 안 연 앱 · 로그아웃). */
        internal fun days(feed: JSONObject?): JSONArray? {
            val d = feed?.optJSONObject("week")?.optJSONArray("days") ?: return null
            return if (d.length() == 0) null else d
        }

        /** 이 위젯이 고른 날(날짜 열쇠) · 날이 바뀌어 없으면 오늘(0). */
        internal fun selIndex(context: Context, widgetId: Int, days: JSONArray): Int {
            val key = WidgetStore.weekSel(context, widgetId)
            if (key.isEmpty()) return 0
            for (i in 0 until days.length()) {
                if (days.optJSONObject(i)?.optString("key", "") == key) return i
            }
            return 0
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                manager.updateAppWidget(widgetId, buildViews(context, widgetId))
            } catch (e: Exception) {
                // 그리기 실패는 다음 갱신 때 다시
            }
        }

        fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val feed = WidgetStore.feed(context)
            val th = WidgetTheme(feed?.optJSONObject("theme"))
            val level = WidgetStore.sizeLevel(context)
            val en = feed?.optString("lang", "") == "en"
            val week = feed?.optJSONObject("week")
            val days = days(feed)
            val rv = RemoteViews(context.packageName, R.layout.q11_widget_week)

            rv.setInt(R.id.q11_card, "setBackgroundResource", th.bgRes)

            // 머리
            TodayWidgetProvider.tint(rv, R.id.q11_logo, th.accent, TodayWidgetProvider.chipShape(th))
            rv.setTextColor(R.id.q11_logo, th.accentInk)
            rv.setTextViewText(R.id.q11_title, week?.optString("title", "")?.ifEmpty { null } ?: (if (en) "Week" else "한 주"))
            rv.setTextColor(R.id.q11_title, th.ink)
            rv.setTextViewTextSize(R.id.q11_title, TypedValue.COMPLEX_UNIT_SP, WidgetSize.TITLE[level])
            rv.setTextColor(R.id.q11_count, th.meta)
            rv.setTextViewTextSize(R.id.q11_count, TypedValue.COMPLEX_UNIT_SP, WidgetSize.COUNT[level])
            Q11Box.accent(context, rv, R.id.q11_wk_add_o, R.id.q11_wk_add_i, th, 999f)
            TodayWidgetProvider.tintImage(rv, R.id.q11_wk_add_icon, th.accentInk)
            rv.setContentDescription(R.id.q11_wk_add_area, if (en) "Add a task" else "할 일 적기")
            TodayWidgetProvider.openApp(context, "week")?.let { rv.setOnClickPendingIntent(R.id.q11_wk_open, it) }

            rv.setTextColor(R.id.q11_wk_empty, th.meta)
            rv.setTextViewTextSize(R.id.q11_wk_empty, TypedValue.COMPLEX_UNIT_SP, WidgetSize.EMPTY[level])

            if (days == null) {
                // 아직 안 연 앱 · 옛 앱 · 로그아웃: 띠 · 목록 없이 안내 한 줄
                rv.setViewVisibility(R.id.q11_wk_strip, View.GONE)
                rv.setViewVisibility(R.id.q11_wk_list, View.GONE)
                rv.setViewVisibility(R.id.q11_wk_empty, View.VISIBLE)
                rv.setTextViewText(R.id.q11_count, "")
                val signedOut = feed != null && feed.optString("uid", "").isEmpty()
                rv.setTextViewText(
                    R.id.q11_wk_empty,
                    if (signedOut) feed?.optString("empty", "") ?: "" else context.getString(R.string.q11_week_open_app)
                )
                rv.setOnClickPendingIntent(R.id.q11_wk_add_area, addIntent(context, widgetId, null))
                TodayWidgetProvider.openApp(context, null)?.let { rv.setOnClickPendingIntent(R.id.q11_wk_empty, it) }
                return rv
            }

            val sel = selIndex(context, widgetId, days)
            val cur = days.optJSONObject(sel) ?: JSONObject()
            rv.setViewVisibility(R.id.q11_wk_strip, View.VISIBLE)
            rv.setViewVisibility(R.id.q11_wk_list, View.VISIBLE)
            rv.setTextViewText(R.id.q11_count, cur.optString("head", ""))

            // 띠 7칸
            for (i in 0 until 7) {
                val d = days.optJSONObject(i)
                if (d == null) {
                    rv.setViewVisibility(CELL_O[i], View.INVISIBLE)
                    continue
                }
                rv.setViewVisibility(CELL_O[i], View.VISIBLE)
                val on = i == sel
                if (on) Q11Box.accent(context, rv, CELL_O[i], CELL_I[i], th) else Q11Box.clear(context, rv, CELL_O[i], CELL_I[i])
                rv.setTextViewText(WD[i], d.optString("wd", ""))
                rv.setTextColor(WD[i], if (on) th.accentInk else if (i == 0) th.ink else th.meta)
                rv.setTextViewTextSize(WD[i], TypedValue.COMPLEX_UNIT_SP, WD_SIZE[level])
                rv.setTextViewText(DAY[i], d.optInt("day", 0).toString())
                rv.setTextColor(DAY[i], if (on) th.accentInk else th.ink)
                rv.setTextViewTextSize(DAY[i], TypedValue.COMPLEX_UNIT_SP, DAY_SIZE[level])
                val dotColor = if (on) th.accentInk else WidgetTheme.alpha(th.ink, 0.55f)
                rv.setTextViewText(DOTS[i], Q11Rows.dots(d.optInt("n", 0), d.optBoolean("urgent", false), dotColor, th.danger))
                val proj = d.optBoolean("proj", false)
                rv.setViewVisibility(DIA[i], if (proj) View.VISIBLE else View.GONE)
                if (proj) rv.setTextColor(DIA[i], if (on) th.accentInk else th.proj)
                rv.setContentDescription(CELL_O[i], d.optString("aria", d.optString("md", "")))
                rv.setOnClickPendingIntent(CELL_O[i], dayIntent(context, widgetId, i, d.optString("key", "")))
            }

            // 목록(고른 날)
            val svc = Intent(context, WeekListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.q11_wk_list, svc)
            rv.setEmptyView(R.id.q11_wk_list, R.id.q11_wk_empty)
            rv.setTextViewText(R.id.q11_wk_empty, week?.optString("empty", "") ?: "")
            rv.setPendingIntentTemplate(R.id.q11_wk_list, itemTemplate(context))
            rv.setOnClickPendingIntent(R.id.q11_wk_add_area, addIntent(context, widgetId, cur))
            rv.setOnClickPendingIntent(R.id.q11_wk_empty, addIntent(context, widgetId, cur))
            return rv
        }

        private fun mutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        private fun itemTemplate(context: Context): PendingIntent {
            val i = Intent(context, WeekWidgetProvider::class.java).setAction(TodayWidgetProvider.ACTION_ITEM)
            return PendingIntent.getBroadcast(context, 8, i, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag())
        }

        private fun dayIntent(context: Context, widgetId: Int, index: Int, key: String): PendingIntent {
            val i = Intent(context, WeekWidgetProvider::class.java)
                .setAction(ACTION_DAY)
                .putExtra(EXTRA_WID, widgetId)
                .putExtra(EXTRA_KEY, key)
            // 위젯 × 날마다 다른 번호(서로 덮지 않게)
            val code = 20_000 + (widgetId and 0xFFFF) * 8 + index
            return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** ＋ = 작은 창. 오늘이 아닌 날을 골랐으면 그날 마감으로(제목 · 안내 · 날짜를 안 말하면 덧붙일 날짜). */
        private fun addIntent(context: Context, widgetId: Int, day: JSONObject?): PendingIntent {
            val i = Intent(context, QuickAddActivity::class.java)
                .setAction(QuickAddActivity.ACTION_DAY_ADD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val hint = day?.optString("hint", "") ?: ""
            if (hint.isNotEmpty()) {
                i.putExtra(QuickAddActivity.EXTRA_DAY_HINT, hint)
                i.putExtra(QuickAddActivity.EXTRA_DAY_TITLE, day?.optString("addTitle", "") ?: "")
                i.putExtra(QuickAddActivity.EXTRA_DAY_HELP, day?.optString("addHelp", "") ?: "")
            }
            val code = 30_000 + (widgetId and 0xFFFF)
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
