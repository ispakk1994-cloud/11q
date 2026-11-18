package day.q11.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 11Q 홈 화면 위젯 · 오늘 할 일.
 * 0.0.1(빈 위젯 시험): 11Q 카드만 보여 주고, 누르면 앱을 연다.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))

        fun refreshAll(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context))
            }
            return ids.size
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.q11_widget_today)
            openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.q11_root, it) }
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return null
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
