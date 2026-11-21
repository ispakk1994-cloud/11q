package day.q11.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews

/**
 * 0.7.0 위젯 3 · 자료에 묻기(시안 WidgetMoreGalaxy 4×1).
 * - `내 자료에 물어보기…` = 11Q가 열리며 대화 탭 · 입력칸에 바로 커서 · 범위 = 전체 자료(앱 'ask' · AskKick)
 * - 마이크 = 11Q가 열리며 대화 탭 음성으로 묻기(앱 'askVoice')
 * - 0.8.0 다듬기: 위젯 바탕 없이 검색창(알약) 하나 · 로고는 알약 안 왼쪽
 * - 4×2 `최근 넣은 자료`(시안 WidgetMoreSizes)는 다음 판
 */
class AskWidgetProvider : AppWidgetProvider() {

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
        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, AskWidgetProvider::class.java))

        fun refreshAll(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            for (id in ids) render(context, manager, id)
            return ids.size
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                manager.updateAppWidget(widgetId, buildViews(context))
            } catch (e: Exception) {
            }
        }

        fun buildViews(context: Context): RemoteViews {
            val feed = WidgetStore.feed(context)
            val th = WidgetTheme(feed?.optJSONObject("theme"))
            val en = feed?.optString("lang", "") == "en"
            val rv = RemoteViews(context.packageName, R.layout.q11_widget_ask)

            // 0.8.0 검색창 하나(시안 WidgetPolish ⑦): 바탕 카드 없이 알약 = 위젯 바탕과 같은 그림 · 모서리 pillRadius
            //   로고는 이 위젯에만(글자 없는 검색창이라 11Q 검색인 표시) · 알약 안 왼쪽
            rv.setInt(R.id.q11_ak_pill, "setBackgroundResource", th.askBgRes)
            TodayWidgetProvider.tint(rv, R.id.q11_logo, th.accent, TodayWidgetProvider.chipShape(th))
            rv.setTextColor(R.id.q11_logo, th.accentInk)
            rv.setTextColor(R.id.q11_ak_text, th.meta)
            rv.setTextViewText(R.id.q11_ak_text, if (en) "Ask my files…" else "내 자료에 물어보기…")
            rv.setInt(R.id.q11_ak_div, "setBackgroundColor", WidgetTheme.alpha(th.ink, 0.14f))
            TodayWidgetProvider.tintImage(rv, R.id.q11_ak_mic_icon, th.link)
            rv.setContentDescription(R.id.q11_ak_ask, if (en) "Ask my files" else "내 자료에 물어보기")
            rv.setContentDescription(R.id.q11_ak_mic, if (en) "Ask by voice" else "음성으로 묻기")

            TodayWidgetProvider.openApp(context, "ask")?.let { rv.setOnClickPendingIntent(R.id.q11_ak_ask, it) }
            TodayWidgetProvider.openApp(context, "askVoice")?.let { rv.setOnClickPendingIntent(R.id.q11_ak_mic, it) }
            return rv
        }
    }
}
