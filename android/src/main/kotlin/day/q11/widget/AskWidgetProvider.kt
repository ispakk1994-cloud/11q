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
 * - 마이크 = 11Q가 열리며 대화 탭 말로 묻기(앱 'askVoice')
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

            rv.setInt(R.id.q11_card, "setBackgroundResource", th.bgRes)
            TodayWidgetProvider.tint(rv, R.id.q11_logo, th.accent, TodayWidgetProvider.chipShape(th))
            rv.setTextColor(R.id.q11_logo, th.accentInk)

            // 묻기 줄 = 보조 버튼 토큰(ctlFillHex · ctlBorderHex × borderWidth) · 모서리 pillRadius(시안)
            Q11Box.ctl(context, rv, R.id.q11_ak_bar_o, R.id.q11_ak_bar_i, th, th.pillRadius)
            TodayWidgetProvider.tintImage(rv, R.id.q11_ak_search, th.ink)
            rv.setTextColor(R.id.q11_ak_text, th.meta)
            rv.setTextViewText(R.id.q11_ak_text, if (en) "Ask my files…" else "내 자료에 물어보기…")
            rv.setInt(R.id.q11_ak_div, "setBackgroundColor", th.ctlLine)
            TodayWidgetProvider.tintImage(rv, R.id.q11_ak_mic_icon, th.ink)
            rv.setContentDescription(R.id.q11_ak_ask, if (en) "Ask my files" else "내 자료에 물어보기")
            rv.setContentDescription(R.id.q11_ak_mic, if (en) "Ask by voice" else "말로 묻기")

            TodayWidgetProvider.openApp(context, "ask")?.let { rv.setOnClickPendingIntent(R.id.q11_ak_ask, it) }
            TodayWidgetProvider.openApp(context, "askVoice")?.let { rv.setOnClickPendingIntent(R.id.q11_ak_mic, it) }
            TodayWidgetProvider.openApp(context, null)?.let { rv.setOnClickPendingIntent(R.id.q11_logo, it) }
            return rv
        }
    }
}
