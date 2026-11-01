package com.dariopittera.ecowittpixel.worker

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
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dariopittera.ecowittpixel.MainActivity
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.MouldRiskLevel
import com.dariopittera.ecowittpixel.model.NotificationMetric
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.widget.EcowittGlanceWidget
import java.util.Locale
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
        runCatching { WearDataLayerBridge.pushToWatch(applicationContext, store, settings, snapshot) }

        if (settings.notificationsEnabled) {
            evaluateAndNotify(applicationContext, snapshot, settings)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ecowitt_periodic_sync"
        private const val CHANNEL_ID = "ecowitt_atmosphere_alerts"
        private const val COOLDOWN_MS = 60L * 60L * 1000L // 60 minutes per unique criterion

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

        /**
         * Evaluates all enabled room thresholds, smart psychrometric rules, and custom user-defined
         * criteria against [snapshot], dispatches Android notifications when criteria are met, and
         * returns the list of human-readable summaries of met criteria.
         */
        fun evaluateAndNotify(
            context: Context,
            snapshot: HomeAtmosphereSnapshot,
            settings: AppSettings,
            forceTest: Boolean = false
        ): List<String> {
            val triggeredSummaries = mutableListOf<String>()
            if (!settings.notificationsEnabled && !forceTest) return triggeredSummaries

            val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ecowitt room and criteria alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for met room temperature, humidity, mould, ventilation, and custom criteria"
            }
            mgr.createNotificationChannel(channel)

            val cooldownPrefs = context.getSharedPreferences("ecowitt_alert_cooldowns", Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            fun dispatchCriterionAlert(
                key: String,
                notifId: Int,
                title: String,
                body: String,
                channelId: String = "gateway"
            ) {
                triggeredSummaries.add("$title — $body")
                if (!canPost) return
                val lastSentMs = cooldownPrefs.getLong(key, 0L)
                if (forceTest || (nowMs - lastSentMs) >= COOLDOWN_MS) {
                    val watchIntent = Intent(context, WatchActionReceiver::class.java).apply {
                        action = WatchActionReceiver.ACTION_OPEN_ROOM_ON_WATCH
                        putExtra(WatchActionReceiver.EXTRA_CHANNEL_ID, channelId)
                    }
                    val watchPendingIntent = PendingIntent.getBroadcast(
                        context,
                        notifId,
                        watchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val watchAction = NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_view,
                        "Open room on watch",
                        watchPendingIntent
                    ).build()

                    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .addAction(watchAction)
                        .extend(NotificationCompat.WearableExtender().addAction(watchAction))
                        .setAutoCancel(true)
                        .build()
                    NotificationManagerCompat.from(context).notify(notifId, notif)
                    cooldownPrefs.edit().putLong(key, nowMs).apply()
                }
            }

            fun clearCriterionCooldown(key: String) {
                if (cooldownPrefs.contains(key)) {
                    cooldownPrefs.edit().remove(key).apply()
                }
            }

            val outdoor = snapshot.outdoorRoom

            // 1. Per-room scientific and customisable min/max temperature & humidity criteria
            snapshot.rooms.forEachIndexed { idx, room ->
                val cfg = settings.roomConfigs[room.channelId]
                val alertsOn = cfg?.alertsEnabled ?: true
                if (alertsOn) {
                    val maxHum = cfg?.alertHumidityMaxPct ?: room.archetype.scientificAlertHumidityMaxPct
                    val minHum = cfg?.alertHumidityMinPct ?: 30.0f
                    val minTemp = cfg?.alertTempMinC ?: room.archetype.scientificAlertTempMinC
                    val maxTemp = cfg?.alertTempMaxC ?: room.archetype.scientificAlertTempMaxC
                    val citation = room.archetype.standardCitation

                    val humHighKey = "room_hum_high_${room.channelId}"
                    val humLowKey = "room_hum_low_${room.channelId}"
                    if (room.humidityPct >= maxHum) {
                        val outdoorDryerMsg = if (outdoor != null && !room.archetype.isOutdoor &&
                            room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3 > 1.0f
                        ) {
                            " Outdoor air is drier; open a window to ventilate."
                        } else {
                            " Consider running a dehumidifier or extractor fan."
                        }
                        dispatchCriterionAlert(
                            key = humHighKey,
                            notifId = 1000 + idx,
                            title = "${room.roomName}: high humidity (${room.humidityPct.toInt()}% RH)",
                            body = "Exceeds ${maxHum.toInt()}% RH limit ($citation, dew point ${settings.tempUnit.format(room.dewPointC)}).$outdoorDryerMsg",
                            channelId = room.channelId
                        )
                    } else {
                        clearCriterionCooldown(humHighKey)
                    }

                    if (!room.archetype.isOutdoor && room.humidityPct <= minHum) {
                        dispatchCriterionAlert(
                            key = humLowKey,
                            notifId = 1100 + idx,
                            title = "${room.roomName}: dry air (${room.humidityPct.toInt()}% RH)",
                            body = "Below ${minHum.toInt()}% RH minimum comfort threshold ($citation).",
                            channelId = room.channelId
                        )
                    } else {
                        clearCriterionCooldown(humLowKey)
                    }

                    val tempKey = "room_temp_${room.channelId}"
                    if (room.temperatureC >= maxTemp || room.temperatureC <= minTemp) {
                        val reason = if (room.temperatureC >= maxTemp) {
                            "above ${settings.tempUnit.format(maxTemp)} maximum limit"
                        } else {
                            "below ${settings.tempUnit.format(minTemp)} minimum limit"
                        }
                        dispatchCriterionAlert(
                            key = tempKey,
                            notifId = 2000 + idx,
                            title = "${room.roomName}: temperature $reason",
                            body = "Current ${settings.tempUnit.format(room.temperatureC)} is $reason ($citation).",
                            channelId = room.channelId
                        )
                    } else {
                        clearCriterionCooldown(tempKey)
                    }
                }

                // 2. Mould & condensation criterion (ASHRAE 160)
                val mouldKey = "mould_risk_${room.channelId}"
                if (settings.mouldRiskNotifications && !room.archetype.isOutdoor && room.mouldRisk == MouldRiskLevel.HIGH) {
                    dispatchCriterionAlert(
                        key = mouldKey,
                        notifId = 3000 + idx,
                        title = "${room.roomName}: high mould and condensation risk",
                        body = "Dew point ${settings.tempUnit.format(room.dewPointC)} is close to room temperature (${settings.tempUnit.format(room.temperatureC)} at ${room.humidityPct.toInt()}% RH).",
                        channelId = room.channelId
                    )
                } else {
                    clearCriterionCooldown(mouldKey)
                }

                // 3. Smart window ventilation criterion (indoor vs outdoor absolute humidity)
                val ventKey = "vent_advice_${room.channelId}"
                if (settings.ventilationAdviceNotifications && !room.archetype.isOutdoor && outdoor != null && outdoor.channelId != room.channelId) {
                    val absDiff = room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3
                    if (room.humidityPct >= room.archetype.idealHumidityMax && absDiff >= 1.2f) {
                        dispatchCriterionAlert(
                            key = ventKey,
                            notifId = 4000 + idx,
                            title = "Open window in ${room.roomName}",
                            body = String.format(
                                Locale.UK,
                                "Outdoor air carries %.1f g/m³ less moisture than indoors (%.0f%% RH) and will naturally dry the room.",
                                absDiff,
                                room.humidityPct
                            ),
                            channelId = room.channelId
                        )
                    } else {
                        clearCriterionCooldown(ventKey)
                    }
                }

                // 4. Sensor low battery criterion
                val battKey = "low_batt_${room.channelId}"
                if (settings.lowBatteryNotifications && room.batteryLow) {
                    dispatchCriterionAlert(
                        key = battKey,
                        notifId = 5000 + idx,
                        title = "${room.roomName}: sensor battery low",
                        body = "Replace the battery in ${room.hardwareLabel} (${room.channelId.uppercase()}) to maintain continuous telemetry.",
                        channelId = room.channelId
                    )
                } else {
                    clearCriterionCooldown(battKey)
                }
            }

            // 5. Rapid barometric pressure drop criterion
            val pressKey = "pressure_drop_gateway"
            val pTrend = snapshot.pressureTrend3hHpa ?: 0f
            if (settings.pressureDropNotifications && pTrend <= -2.0f) {
                dispatchCriterionAlert(
                    key = pressKey,
                    notifId = 6000,
                    title = "Rapid barometric pressure drop (${String.format(Locale.UK, "%+.1f", pTrend)} hPa/3h)",
                    body = snapshot.pressureForecastText,
                    channelId = "gateway"
                )
            } else {
                clearCriterionCooldown(pressKey)
            }

            // 6. Custom user-defined notification criteria rules
            settings.customCriteria.forEachIndexed { cIdx, crit ->
                val critKey = "custom_crit_${crit.id}"
                if (!crit.enabled) {
                    clearCriterionCooldown(critKey)
                    return@forEachIndexed
                }
                val targetRoom = snapshot.rooms.firstOrNull { it.channelId == crit.channelId }
                    ?: snapshot.rooms.firstOrNull()
                    ?: return@forEachIndexed

                val actualValue: Float = when (crit.metric) {
                    NotificationMetric.TEMPERATURE -> targetRoom.temperatureC
                    NotificationMetric.HUMIDITY -> targetRoom.humidityPct
                    NotificationMetric.DEW_POINT -> targetRoom.dewPointC
                    NotificationMetric.PRESSURE -> targetRoom.relativePressureHpa ?: snapshot.pressureHpa ?: 1013.2f
                    NotificationMetric.COMFORT_SCORE -> targetRoom.comfortScore.toFloat()
                }

                if (crit.operator.matches(actualValue, crit.threshold)) {
                    val formattedActual = String.format(Locale.UK, "%.1f%s", actualValue, crit.metric.unitSuffix)
                    val formattedTarget = String.format(Locale.UK, "%.1f%s", crit.threshold, crit.metric.unitSuffix)
                    val bodyText = if (crit.customMessage.isNotBlank()) {
                        "${crit.customMessage} (current: $formattedActual, criterion: ${crit.operator.symbol} $formattedTarget)"
                    } else {
                        "${targetRoom.roomName} ${crit.metric.label.lowercase()} is $formattedActual (${crit.operator.symbol} $formattedTarget)."
                    }
                    dispatchCriterionAlert(
                        key = critKey,
                        notifId = 7000 + cIdx,
                        title = "Criterion met: ${targetRoom.roomName} ${crit.metric.label.lowercase()} ${crit.operator.symbol} $formattedTarget",
                        body = bodyText,
                        channelId = targetRoom.channelId
                    )
                } else {
                    clearCriterionCooldown(critKey)
                }
            }

            // If user explicitly tapped "Test active notifications now" and no threshold is currently breached,
            // send a verification notification confirming the alert channel is active on their Pixel 10.
            if (forceTest && triggeredSummaries.isEmpty() && canPost) {
                val leadRoom = snapshot.primaryGatewayRoom ?: snapshot.rooms.firstOrNull()
                val summary = if (leadRoom != null) {
                    "All ${snapshot.rooms.size} rooms are currently within target criteria (${leadRoom.roomName}: ${settings.tempUnit.format(leadRoom.temperatureC)}, ${leadRoom.humidityPct.toInt()}% RH)."
                } else {
                    "Notification channel verified and ready."
                }
                val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Ecowitt criteria notifications active")
                    .setContentText(summary)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(9999, notif)
                triggeredSummaries.add("Test notification sent: all rooms currently within criteria")
            }

            return triggeredSummaries
        }
    }
}
