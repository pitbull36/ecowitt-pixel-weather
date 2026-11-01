package com.dariopittera.ecowittpixel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.worker.WeatherSyncWorker

class EcowittWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EcowittGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val interval = runCatching { LocalHistoryStore(context).loadSettings().pollingIntervalMinutes }.getOrDefault(15)
        WeatherSyncWorker.schedulePeriodicSync(context, interval)
        WeatherSyncWorker.triggerImmediateSync(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val interval = runCatching { LocalHistoryStore(context).loadSettings().pollingIntervalMinutes }.getOrDefault(15)
        WeatherSyncWorker.schedulePeriodicSync(context, interval)
        WeatherSyncWorker.triggerImmediateSync(context)
    }
}

class EcowittSingleRoomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EcowittSingleRoomWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val interval = runCatching { LocalHistoryStore(context).loadSettings().pollingIntervalMinutes }.getOrDefault(15)
        WeatherSyncWorker.schedulePeriodicSync(context, interval)
        WeatherSyncWorker.triggerImmediateSync(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val interval = runCatching { LocalHistoryStore(context).loadSettings().pollingIntervalMinutes }.getOrDefault(15)
        WeatherSyncWorker.schedulePeriodicSync(context, interval)
        WeatherSyncWorker.triggerImmediateSync(context)
    }
}
