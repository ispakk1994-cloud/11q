package day.q11.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/**
 * 0.7.0 위젯 1 · 바로 적기(시안 WidgetMoreGalaxy 2×2 · WidgetMoreSizes 4×1).
 * - 적기 = 홈 화면 위 작은 창(오늘 할 일 위젯 `＋ 적기`와 같은 창 · 앱 안 열림)
 * - 말로 = 같은 작은 창이 열리자마자 폰의 음성 입력(받아쓴 글이 칸에 → 넣기)
 * - 사진 = 11Q가 열리며 대화 탭 카메라(작은 창 카메라와 같은 길 · 앱 'camera')
 * - 낮게 놓으면(2×1 · 4×1) 한 줄 모양 · 좁으면(2×1) 말로 · 사진은 그림만
 */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    companion object {
        /** 이 높이(dp)보다 낮으면 한 줄 모양(2×1 · 4×1). */
        private const val ROW_HEIGHT_DP = 140
        /** 한 줄 모양에서 이 너비(dp)보다 좁으면 말로 · 사진 글자를 뺌(2×1). */
        private const val NARROW_DP = 250

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))

        fun refreshAll(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            for (id in ids) render(context, manager, id)
            return ids.size
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                manager.updateAppWidget(widgetId, buildViews(context, widgetId))
            } catch (e: Exception) {
            }
        }

        fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val feed = WidgetStore.feed(context)
            val th = WidgetTheme(feed?.optJSONObject("theme"))
            val en = feed?.optString("lang", "") == "en"
            val (w, h) = Q11Widgets.sizeDp(context, widgetId)
            val row = h in 1 until ROW_HEIGHT_DP
            val rv = RemoteViews(context.packageName, if (row) R.layout.q11_widget_quick_row else R.layout.q11_widget_quick)

            rv.setInt(R.id.q11_card, "setBackgroundResource", th.bgRes)
            TodayWidgetProvider.tint(rv, R.id.q11_logo, th.accent, TodayWidgetProvider.chipShape(th))
            rv.setTextColor(R.id.q11_logo, th.accentInk)
            if (!row) {
                rv.setTextViewText(R.id.q11_title, if (en) "Quick add" else "바로 적기")
                rv.setTextColor(R.id.q11_title, th.ink)
                rv.setTextViewText(R.id.q11_qw_foot, if (en) "Add · Speak keep 11Q closed" else "적기 · 말로는 앱이 안 열려요")
                rv.setTextColor(R.id.q11_qw_foot, th.meta)
            }

            // 적기(주 버튼) · 말로 · 사진(보조 버튼)
            Q11Box.accent(context, rv, R.id.q11_qw_add_o, R.id.q11_qw_add_i, th)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_add_icon, th.accentInk)
            rv.setTextColor(R.id.q11_qw_add_text, th.accentInk)
            rv.setTextViewText(R.id.q11_qw_add_text, if (en) "Add" else "적기")

            Q11Box.ctl(context, rv, R.id.q11_qw_mic_o, R.id.q11_qw_mic_i, th)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_mic_icon, th.ctlInk)
            rv.setTextColor(R.id.q11_qw_mic_text, th.ctlInk)
            rv.setTextViewText(R.id.q11_qw_mic_text, if (en) "Speak" else "말로")

            Q11Box.ctl(context, rv, R.id.q11_qw_cam_o, R.id.q11_qw_cam_i, th)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_cam_icon, th.ctlInk)
            rv.setTextColor(R.id.q11_qw_cam_text, th.ctlInk)
            rv.setTextViewText(R.id.q11_qw_cam_text, if (en) "Photo" else "사진")

            val narrow = row && w in 1 until NARROW_DP
            rv.setViewVisibility(R.id.q11_qw_mic_text, if (narrow) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.q11_qw_cam_text, if (narrow) View.GONE else View.VISIBLE)
            rv.setContentDescription(R.id.q11_qw_mic_o, if (en) "Speak a task" else "말로 적기")
            rv.setContentDescription(R.id.q11_qw_cam_o, if (en) "Add from a photo" else "사진으로 적기")

            rv.setOnClickPendingIntent(R.id.q11_qw_add_o, quick(context, false))
            rv.setOnClickPendingIntent(R.id.q11_qw_mic_o, quick(context, true))
            TodayWidgetProvider.openApp(context, "camera")?.let { rv.setOnClickPendingIntent(R.id.q11_qw_cam_o, it) }
            // 머리(11Q · 바로 적기) = 앱
            TodayWidgetProvider.openApp(context, null)?.let { rv.setOnClickPendingIntent(R.id.q11_logo, it) }
            return rv
        }

        /** 작은 창(listen = 열리자마자 말로 적기). */
        private fun quick(context: Context, listen: Boolean): PendingIntent {
            val i = Intent(context, QuickAddActivity::class.java)
                .setAction(if (listen) QuickAddActivity.ACTION_LISTEN else QuickAddActivity.ACTION_QUICK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (listen) i.putExtra(QuickAddActivity.EXTRA_LISTEN, true)
            return PendingIntent.getActivity(
                context,
                if (listen) 41 else 40,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
