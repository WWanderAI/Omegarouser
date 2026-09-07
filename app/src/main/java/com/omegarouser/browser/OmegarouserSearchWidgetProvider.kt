package com.omegarouser.browser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Виджет для главного экрана: таблетка с подсказкой поиска и иконкой лупы.
 * Нажатие в любом месте открывает приложение с фокусом на адресной строке
 * и открытой клавиатурой — полноценный текстовый ввод внутри самого виджета
 * невозможен (Android AppWidget не поддерживает интерактивные текстовые поля).
 */
class OmegarouserSearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_search)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetSearchIcon, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
