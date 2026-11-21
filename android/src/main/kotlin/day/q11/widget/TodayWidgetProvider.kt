package day.q11.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews

/**
 * 11Q 홈 화면 위젯 · 오늘 할 일(위젯 A).
 * - 머리: 오늘 · N개 남음 · ＋ 적기(알약 · 누르는 곳 넓게 → 홈 화면 위 작은 창 QuickAddActivity)
 *   앱이 열리는 곳은 왼쪽 오늘 · N개 남음만(＋ 옆을 눌러 앱이 열리던 것)
 * - 0.8.0 다듬기(시안 WidgetPolish): 로고 없음 · 여백 좌우 16 · 굵기 500 · 온전한 줄만 + `+N개 더`
 * - 글자 크기: 위젯을 꾹 눌러 `설정`(WidgetSettingsActivity) · 보통 · 크게(처음) · 아주 크게
 * - 목록: 앱 대시보드와 같은 순서(긴급 → 지난 일 → 오늘) · 체크칸으로 끝냄 · 지금 칩
 * - 목록은 앱이 save로 넘긴 JSON(WidgetStore)을 그대로 그린다.
 */
@Suppress("DEPRECATION") // 목록 위젯(RemoteViewsService)은 안드로이드 16에서도 그대로 동작
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            render(context, appWidgetManager, id)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.q11_list)
        WidgetJobs.schedulePeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.q11_list)
    }

    override fun onEnabled(context: Context) {
        WidgetJobs.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        // 0.7.0: 한 주 위젯이 남아 있으면 15분 확인은 그대로
        if (!Q11Widgets.needsPeriodic(context)) WidgetJobs.cancelPeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ITEM) {
            // 체크칸 = 끝냄/되돌림(한 주 위젯 줄도 같이 다시) · 줄 = 앱 열기
            Q11Widgets.handleItem(context, intent, null)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_ITEM = "day.q11.widget.ITEM"
        const val EXTRA_KIND = "q11_kind"
        const val EXTRA_ID = "q11_id"
        const val EXTRA_ACTION = "q11_widget_action"

        /** 이 높이(dp)보다 낮으면 묶음 머리(긴급 · 지난 일 · 오늘)를 빼고 줄만(4×2). */
        const val COMPACT_HEIGHT_DP = 250

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))

        fun refreshAll(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            for (id in ids) {
                render(context, manager, id)
            }
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.q11_list)
            return ids.size
        }

        fun refreshList(context: Context) {
            val ids = widgetIds(context)
            if (ids.isNotEmpty()) {
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.q11_list)
            }
        }

        fun isCompact(context: Context, widgetId: Int): Boolean {
            val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) ?: return true
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            return h in 1 until COMPACT_HEIGHT_DP
        }

        /** 0.0.1 시험 코드와 같은 이름(플러그인 refresh · requestPin에서 씀). */
        fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val feed = WidgetStore.feed(context)
            val th = WidgetTheme(feed?.optJSONObject("theme"))
            val level = WidgetStore.sizeLevel(context)
            val en = feed?.optString("lang", "") == "en"
            val rv = RemoteViews(context.packageName, R.layout.q11_widget_today)

            rv.setInt(R.id.q11_card, "setBackgroundResource", th.bgRes)

            // 머리(0.8.0 다듬기 · 시안 WidgetPolish: 로고 없음 · 굵기 500)
            rv.setTextViewText(R.id.q11_title, text(feed, "title", context.getString(R.string.q11_widget_today)))
            rv.setTextColor(R.id.q11_title, th.ink)
            rv.setTextViewText(R.id.q11_count, text(feed, "count", ""))
            rv.setTextColor(R.id.q11_count, th.meta)
            rv.setTextViewTextSize(R.id.q11_title, TypedValue.COMPLEX_UNIT_SP, WidgetSize.TITLE[level])
            rv.setTextViewTextSize(R.id.q11_count, TypedValue.COMPLEX_UNIT_SP, WidgetSize.COUNT[level])

            // ＋ 적기 알약 = 이 위젯의 꽉 찬 강조색 하나: accentHex · 선 accentLineHex(종이 1.5 · 네온 분홍) · 스위스는 네모
            Q11Box.paint(
                context, rv, R.id.q11_plus_o, R.id.q11_plus,
                th.accent, th.accentLine, th.primaryBw, if (th.square) 0f else 999f
            )
            tintImage(rv, R.id.q11_plus_icon, th.accentInk)
            rv.setTextColor(R.id.q11_plus_text, th.accentInk)
            rv.setTextViewText(R.id.q11_plus_text, if (en) "Add" else "적기")

            // 빈 목록 · 로그아웃 · 아직 앱을 안 연 때
            val empty = if (feed == null) context.getString(R.string.q11_widget_open_app) else text(feed, "empty", "")
            rv.setTextViewText(R.id.q11_empty, empty)
            rv.setTextColor(R.id.q11_empty, th.meta)
            rv.setTextViewTextSize(R.id.q11_empty, TypedValue.COMPLEX_UNIT_SP, WidgetSize.EMPTY[level])

            // 목록
            val svc = Intent(context, TodayListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.q11_list, svc)
            rv.setEmptyView(R.id.q11_list, R.id.q11_empty)
            rv.setPendingIntentTemplate(R.id.q11_list, itemTemplate(context))

            // 0.8.0 마지막 줄이 잘리지 않게: 온전한 줄만 · 넘치면 `+N개 더`(위로 밀면 그대로 더 보임)
            rv.setTextColor(R.id.q11_more, th.meta)
            val avail = Q11Widgets.listAvail(context, widgetId, FIXED_DP)
            Q11Widgets.applyFit(context, rv, R.id.q11_list_box, R.id.q11_more, fitToday(context, widgetId, feed, level, avail), en, level)

            // 누르면: 왼쪽 오늘 · N개 남음 · 빈 칸 · `+N개 더` = 앱 · ＋ 적기(알약 둘레 넓게) = 작은 창
            //   (앱을 한 번도 안 열었으면 작은 창이 앱 적기 칸을 대신 연다)
            openApp(context, null)?.let {
                rv.setOnClickPendingIntent(R.id.q11_open, it)
                rv.setOnClickPendingIntent(R.id.q11_empty, it)
                rv.setOnClickPendingIntent(R.id.q11_more, it)
            }
            rv.setOnClickPendingIntent(R.id.q11_plus_area, quickAdd(context))
            return rv
        }

        /** 머리 · 여백(dp): 위 12 + 아래 10 + 머리 36 + 틈 2 + 넉넉히 2 = 목록 칸 밖 높이 */
        private const val FIXED_DP = 62

        /** 목록 줄 높이(TodayListFactory와 같은 줄 · 4×2는 묶음 머리 없음). */
        private fun fitToday(context: Context, widgetId: Int, feed: org.json.JSONObject?, level: Int, avail: Int): Pair<Int, Int> {
            val all = feed?.optJSONArray("rows") ?: return Pair(-1, 0)
            val compact = isCompact(context, widgetId)
            val items = ArrayList<Pair<Int, Boolean>>()
            for (i in 0 until all.length()) {
                val r = all.optJSONObject(i) ?: continue
                val head = r.optString("t") == "h"
                if (head && compact) continue
                items.add(Pair(if (head) WidgetSize.HEAD_H[level] else WidgetSize.ROW_H[level], !head))
            }
            return Q11Widgets.fit(items, avail, WidgetSize.MORE_H)
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                manager.updateAppWidget(widgetId, buildViews(context, widgetId))
            } catch (e: Exception) {
                // 그리기 실패는 다음 갱신 때 다시
            }
        }

        private fun text(feed: org.json.JSONObject?, key: String, def: String): String {
            if (feed == null || !feed.has(key)) return def
            return feed.optString(key, def)
        }

        /**
         * 흰 모양에 테마 색을 입힌다(안드로이드 12 이상). 그 아래는 네모 바탕색.
         * shape: "round"(칩 · 로고 모서리 5) · "square"(스위스) · null(지금 바탕 모양 그대로 · ＋ 적기 알약).
         */
        fun tint(rv: RemoteViews, viewId: Int, color: Int, shape: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (shape) {
                    "round" -> rv.setInt(viewId, "setBackgroundResource", R.drawable.q11_round_white)
                    "square" -> rv.setInt(viewId, "setBackgroundResource", R.drawable.q11_square_white)
                }
                rv.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
            } else {
                rv.setInt(viewId, "setBackgroundColor", color)
            }
        }

        /**
         * 그림(체크칸 · ＋)에 색. setColorFilter는 반투명 색을 흰 그림 위에 겹쳐 흰색 그대로 보이므로
         * 색은 불투명으로 입히고 투명도는 setImageAlpha로 따로(0.1.1 · 체크칸 테두리가 시안보다 진하던 것).
         */
        internal fun tintImage(rv: RemoteViews, viewId: Int, color: Int) {
            rv.setInt(viewId, "setColorFilter", color or 0xFF000000.toInt())
            rv.setInt(viewId, "setImageAlpha", (color ushr 24) and 0xFF)
        }

        internal fun chipShape(th: WidgetTheme): String = if (th.tagRadius <= 0.5f) "square" else "round"

        private fun itemTemplate(context: Context): PendingIntent {
            val i = Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_ITEM)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        }

        fun openAppIntent(context: Context, action: String?): Intent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            if (action == null) {
                // 홈 화면 아이콘을 누른 것과 같게(켜져 있으면 그 화면 그대로 앞으로)
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            } else {
                // 켜져 있어도 앱이 알게(onNewIntent) · 주소(data)는 넣지 않음(Flutter 딥링크가 길로 읽음)
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                launch.putExtra(EXTRA_ACTION, action)
            }
            return launch
        }

        private fun quickAdd(context: Context): PendingIntent {
            val i = Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                12,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * 앱 열기. action = 앱 WidgetCatch가 받을 값(null = 홈 화면 아이콘과 같게).
         * 0.7.0: 'week'(한 주 보기) · 'ask' · 'askVoice'(대화) · 'camera'도 · 값마다 요청 번호를 따로(서로 덮지 않게).
         */
        internal fun openApp(context: Context, action: String?): PendingIntent? {
            val launch = openAppIntent(context, action) ?: return null
            val code = when (action) {
                null -> 10
                "add" -> 11
                "camera" -> 13
                "week" -> 14
                "ask" -> 15
                "askVoice" -> 16
                else -> 11
            }
            return PendingIntent.getActivity(
                context,
                code,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
