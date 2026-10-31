package com.dariopittera.ecowittpixel.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.widget.EcowittGlanceWidget
import java.util.concurrent.TimeUnit

class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = LocalHistoryStore(applicationContext)
        val settings = store.loadSettings()
        val api = EcowittApiClient(store)

        val snapshot = api.fetchAtmosphere(settings, SimulationParameters())
        store.saveWidgetSnapshotCache(snapshot)
        runCatching { EcowittGlanceWidget().updateAll(applicationContext) }

        if (settings.notificationsEnabled) {
            evaluateAndNotify(applicationContext, snapshot, settings)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ecowitt_periodic_sync"
        private const val CHANNEL_ID = "ecowitt_atmosphere_alerts"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
            val mins = intervalMinutes.coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<WeatherSyncWorker>(mins, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        private fun evaluateAndNotify(
            context: Context,
            snapshot: HomeAtmosphereSnapshot,
            settings: AppSettings
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ecowitt climate and mould alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for high humidity, mould risk, low sensor battery, and ventilation tips"
            }
            mgr.createNotificationChannel(channel)

            val outdoor = snapshot.outdoorRoom
            snapshot.rooms.forEachIndexed { idx, room ->
                val cfg = settings.roomConfigs[room.channelId]
                val maxHum = cfg?.alertHumidityMaxPct ?: 68f
                if (room.humidityPct >= maxHum) {
                    val outdoorDryerMsg = if (outdoor != null && !room.archetype.isOutdoor &&
                        room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3 > 1.2f
                    ) {
                        " Outdoor air is drier; open a window to ventilate."
                    } else {
                        " Consider running a dehumidifier or extractor fan."
                    }

                    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("High humidity in ${room.roomName}")
                        .setContentText(
                            "${room.humidityPct.toInt()}% RH (dew point ${settings.tempUnit.format(room.dewPointC)}).$outdoorDryerMsg"
                        )
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()

                    NotificationManagerCompat.from(context).notify(1000 + idx, notif)
                }
            }
        }
    }
}
