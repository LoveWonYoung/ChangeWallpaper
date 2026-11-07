package com.example.changewallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class WallpaperWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun requestPin(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(
                ComponentName(context, WallpaperWidgetProvider::class.java),
                null,
                null
            )
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WallpaperWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach {
                manager.updateAppWidget(it, views(context))
            }
        }

        private fun views(context: Context): RemoteViews = RemoteViews(
            context.packageName,
            R.layout.wallpaper_widget
        ).apply {
            setOnClickPendingIntent(R.id.widget_root, activityIntent(context))
            setOnClickPendingIntent(
                R.id.widget_previous,
                actionIntent(context, WallpaperActionReceiver.ACTION_PREVIOUS, 101)
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                actionIntent(context, WallpaperActionReceiver.ACTION_NEXT, 102)
            )
            setOnClickPendingIntent(
                R.id.widget_random,
                actionIntent(context, WallpaperActionReceiver.ACTION_RANDOM, 103)
            )
            setOnClickPendingIntent(
                R.id.widget_pause,
                actionIntent(context, WallpaperActionReceiver.ACTION_PAUSE_ONE_DAY, 104)
            )
        }

        private fun activityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WallpaperActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
