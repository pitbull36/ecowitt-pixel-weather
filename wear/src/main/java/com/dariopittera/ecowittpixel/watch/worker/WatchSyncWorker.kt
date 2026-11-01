package com.dariopittera.ecowittpixel.watch.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.AlertEvaluator
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.watch.WatchMainActivity
import com.dariopittera.ecowittpixel.watch.WatchViewModel
import java.util.concurrent.TimeUnit

class WatchSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = LocalHistoryStore(applicationContext)
        val isPhoneNearby = WearDataLayerBridge.hasNearbyPeer(applicationContext)

        if (isPhoneNearby) {
            WearDataLayerBridge.requestSyncFromPhone(applicationContext)
            WearDataLayerBridge.pullFromDataLayer(applicationContext, store)
        } else {
            val settings = store.loadSettings()
            val api = EcowittApiClient(store)
            val snapshot = api.fetchAtmosphere(settings, SimulationParameters())
            store.saveWidgetSnapshotCache(snapshot)

            // Evaluate and fire standalone watch alerts ONLY when disconnected from the phone
            // to avoid duplicate notifications when the Pixel 10 is nearby.
            if (settings.notificationsEnabled) {
                evaluateStandaloneWatchAlerts(applicationContext, snapshot, settings)
            }
        }

        WatchViewModel.refreshWatchSurfaces(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ecowitt_watch_periodic_sync"
        private const val CHANNEL_ID = "ecowitt_watch_alerts"
        private const val COOLDOWN_MS = 60L * 60L * 1000L

        fun schedulePeriodicWatchSync(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchSyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun evaluateStandaloneWatchAlerts(
            context: Context,
            snapshot: HomeAtmosphereSnapshot,
            settings: AppSettings
        ) {
            val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (!canPost) return

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ecowitt standalone watch alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Standalone Pixel Watch room alerts when away from your Pixel 10"
                enableVibration(true)
            }
            mgr.createNotificationChannel(channel)

            val cooldownPrefs = context.getSharedPreferences("ecowitt_watch_cooldowns", Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val breaches = AlertEvaluator.evaluateBreaches(snapshot, settings)
            val activeKeys = breaches.map { it.key }.toSet()

            // Clear resolved cooldowns
            cooldownPrefs.all.keys.toList().forEach { existingKey ->
                if (existingKey !in activeKeys) {
                    cooldownPrefs.edit().remove(existingKey).apply()
                }
            }

            breaches.forEach { breach ->
                val lastSent = cooldownPrefs.getLong(breach.key, 0L)
                if (nowMs - lastSent >= COOLDOWN_MS) {
                    val openRoomIntent = Intent(context, WatchMainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(WatchMainActivity.EXTRA_CHANNEL_ID, breach.channelId)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        breach.notifId,
                        openRoomIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val openAction = NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_view,
                        "Open room on watch",
                        pendingIntent
                    ).build()

                    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(breach.title)
                        .setContentText(breach.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(breach.body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .addAction(openAction)
                        .setAutoCancel(true)
                        .build()

                    NotificationManagerCompat.from(context).notify(breach.notifId, notif)
                    cooldownPrefs.edit().putLong(breach.key, nowMs).apply()
                }
            }
        }
    }
}
