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
 * 0.7.0 위젯 1 · 바로 적기(시안 WidgetMoreGalaxy 2×2 · WidgetMoreSizes 4×1) · 0.8.0 다듬기(시안 WidgetPolish).
 * - 적기 = 홈 화면 위 작은 창(오늘 할 일 위젯 `＋ 적기`와 같은 창 · 앱 안 열림)
 * - 음성(0.8.0 `말로` → `음성`) = 같은 작은 창이 열리자마자 폰의 음성 입력(받아쓴 글이 칸에 → 넣기)
 * - 사진 = 11Q가 열리며 대화 탭 카메라(작은 창 카메라와 같은 길 · 앱 'camera')
 * - 낮게 놓으면(2×1 · 4×1) 한 줄 모양 · 좁으면(2×1) 음성 · 사진은 그림만
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
        /** 한 줄 모양에서 이 너비(dp)보다 좁으면 음성 · 사진 글자를 뺌(2×1). */
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
            if (!row) {
                // 머리(0.8.0 로고 없음 · 아래 설명 글 없음 = 위젯 고르는 화면 설명으로) · 누르면 앱
                rv.setTextViewText(R.id.q11_title, if (en) "Quick add" else "바로 적기")
                rv.setTextColor(R.id.q11_title, th.ink)
                TodayWidgetProvider.openApp(context, null)?.let { rv.setOnClickPendingIntent(R.id.q11_qw_head, it) }
                // 종이만: 적기 버튼 오른쪽 아래 그림자(앱 주 버튼과 같은 꼴)
                val paper = th.mode == "paper"
                rv.setViewVisibility(R.id.q11_qw_add_sh, if (paper) View.VISIBLE else View.GONE)
                if (paper) Q11Box.solid(rv, R.id.q11_qw_add_sh, th.ink, th.ctlRadius)
            }

            // 적기 = 이 위젯의 꽉 찬 강조색 하나(선은 1.5까지) · 음성 · 사진 = 보조 버튼(선 1)
            Q11Box.paint(context, rv, R.id.q11_qw_add_o, R.id.q11_qw_add_i, th.accent, th.accentLine, th.primaryBw, th.ctlRadius)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_add_icon, th.accentInk)
            rv.setTextColor(R.id.q11_qw_add_text, th.accentInk)
            rv.setTextViewText(R.id.q11_qw_add_text, if (en) "Add" else "적기")

            Q11Box.paint(context, rv, R.id.q11_qw_mic_o, R.id.q11_qw_mic_i, th.ctl, th.ctlLine, 1f, th.ctlRadius)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_mic_icon, th.ctlInk)
            rv.setTextColor(R.id.q11_qw_mic_text, th.ctlInk)
            rv.setTextViewText(R.id.q11_qw_mic_text, if (en) "Voice" else "음성")

            Q11Box.paint(context, rv, R.id.q11_qw_cam_o, R.id.q11_qw_cam_i, th.ctl, th.ctlLine, 1f, th.ctlRadius)
            TodayWidgetProvider.tintImage(rv, R.id.q11_qw_cam_icon, th.ctlInk)
            rv.setTextColor(R.id.q11_qw_cam_text, th.ctlInk)
            rv.setTextViewText(R.id.q11_qw_cam_text, if (en) "Photo" else "사진")

            val narrow = row && w in 1 until NARROW_DP
            rv.setViewVisibility(R.id.q11_qw_mic_text, if (narrow) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.q11_qw_cam_text, if (narrow) View.GONE else View.VISIBLE)
            rv.setContentDescription(R.id.q11_qw_mic_o, if (en) "Add by voice" else "음성으로 적기")
            rv.setContentDescription(R.id.q11_qw_cam_o, if (en) "Add from a photo" else "사진으로 적기")

            rv.setOnClickPendingIntent(R.id.q11_qw_add_o, quick(context, false))
            rv.setOnClickPendingIntent(R.id.q11_qw_mic_o, quick(context, true))
            TodayWidgetProvider.openApp(context, "camera")?.let { rv.setOnClickPendingIntent(R.id.q11_qw_cam_o, it) }
            return rv
        }

        /** 작은 창(listen = 열리자마자 음성 입력). */
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
