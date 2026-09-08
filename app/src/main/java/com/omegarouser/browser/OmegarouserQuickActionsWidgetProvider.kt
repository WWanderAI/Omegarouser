package com.omegarouser.browser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Виджет с тремя быстрыми действиями: новая вкладка, история, загрузки.
 */
class OmegarouserQuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)

            views.setOnClickPendingIntent(
                R.id.actionNewTab,
                pendingIntentFor(context, widgetId * 10 + 1, MainActivity::class.java) {
                    putExtra(MainActivity.EXTRA_NEW_TAB, true)
                }
            )
            views.setOnClickPendingIntent(
                R.id.actionHistory,
                pendingIntentFor(context, widgetId * 10 + 2, HistoryActivity::class.java)
            )
            views.setOnClickPendingIntent(
                R.id.actionDownloads,
                pendingIntentFor(context, widgetId * 10 + 3, DownloadsActivity::class.java)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun pendingIntentFor(
        context: Context,
        requestCode: Int,
        activityClass: Class<*>,
        extras: (Intent.() -> Unit)? = null
    ): PendingIntent {
        val intent = Intent(context, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras?.invoke(this)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
