package com.omegarouser.browser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * Виджет со списком последних закладок (до 4 штук). Тап по строке открывает её.
 */
class OmegarouserBookmarksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val rowIds = intArrayOf(R.id.bookmarkRow1, R.id.bookmarkRow2, R.id.bookmarkRow3, R.id.bookmarkRow4)
        val bookmarks = BookmarkStore.getEntries(context).take(4)

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_bookmarks)

            if (bookmarks.isEmpty()) {
                views.setViewVisibility(R.id.bookmarksEmpty, android.view.View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it, android.view.View.GONE) }
            } else {
                views.setViewVisibility(R.id.bookmarksEmpty, android.view.View.GONE)
                rowIds.forEachIndexed { index, rowId ->
                    val bookmark = bookmarks.getOrNull(index)
                    if (bookmark != null) {
                        views.setViewVisibility(rowId, android.view.View.VISIBLE)
                        views.setTextViewText(rowId, "• " + bookmark.title)
                        val intent = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(bookmark.url)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context, widgetId * 10 + index, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(rowId, pendingIntent)
                    } else {
                        views.setViewVisibility(rowId, android.view.View.GONE)
                    }
                }
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        /** Вызывается при изменении закладок, чтобы виджет обновился сразу, не дожидаясь таймера. */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, OmegarouserBookmarksWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(android.content.ComponentName(context, OmegarouserBookmarksWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
