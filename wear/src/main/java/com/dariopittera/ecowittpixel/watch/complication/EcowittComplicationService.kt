package com.dariopittera.ecowittpixel.watch.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.AlertEvaluator
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.watch.WatchMainActivity
import kotlin.math.roundToInt

class EcowittComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val settings = AppSettings()
        val sampleRoom = SensorRoomState(
            channelId = "gateway",
            hardwareLabel = "GW1200",
            roomName = "Living room",
            archetype = com.dariopittera.ecowittpixel.model.RoomArchetype.LIVING_ROOM,
            temperatureC = 21.6f,
            humidityPct = 49f,
            relativePressureHpa = 1016.4f
        )
        return buildComplicationData(
            type = type,
            room = sampleRoom,
            settings = settings,
            slotCfg = ComplicationSlotConfig(),
            breachBadge = null,
            tapPendingIntent = null
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val store = LocalHistoryStore(applicationContext)
        val (dlSettings, dlSnapshot) = WearDataLayerBridge.pullFromDataLayer(applicationContext, store)
        val settings = (dlSettings ?: store.loadSettings()).copy(demoModeEnabled = false)
        val rawSnapshot = dlSnapshot
            ?: store.loadWidgetSnapshotCache()
            ?: EcowittApiClient(store).fetchAtmosphere(settings, SimulationParameters())
        val snapshot = settings.applyToSnapshot(rawSnapshot)

        val slotCfg = ComplicationConfigStore.loadSlotConfig(
            applicationContext,
            request.complicationInstanceId
        )

        val breaches = AlertEvaluator.evaluateBreaches(snapshot, settings)
        val activeBreach = if (slotCfg.alertOverrideEnabled) {
            breaches.firstOrNull { it.channelId == slotCfg.channelId } ?: breaches.firstOrNull()
        } else {
            breaches.firstOrNull { it.channelId == slotCfg.channelId }
        }

        val targetChannelId = if (slotCfg.alertOverrideEnabled && activeBreach != null) {
            activeBreach.channelId
        } else {
            slotCfg.channelId
        }

        val room = snapshot.rooms.firstOrNull { it.channelId == targetChannelId }
            ?: snapshot.primaryGatewayRoom
            ?: snapshot.rooms.firstOrNull()
            ?: return null

        val openIntent = Intent(applicationContext, WatchMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WatchMainActivity.EXTRA_CHANNEL_ID, room.channelId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            applicationContext,
            request.complicationInstanceId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return buildComplicationData(
            type = request.complicationType,
            room = room,
            settings = settings,
            slotCfg = slotCfg,
            breachBadge = activeBreach?.shortBadge,
            tapPendingIntent = tapPendingIntent
        )
    }

    private fun buildComplicationData(
        type: ComplicationType,
        room: SensorRoomState,
        settings: AppSettings,
        slotCfg: ComplicationSlotConfig,
        breachBadge: String?,
        tapPendingIntent: PendingIntent?
    ): ComplicationData? {
        val shortRoom = room.roomName.substringBefore(" (").take(7)
        val tempText = settings.tempUnit.format(room.temperatureC)
        val humText = "${room.humidityPct.roundToInt()}%"

        val (shortMain, shortTitle, rangeVal, rangeMin, rangeMax) = when (slotCfg.metric) {
            ComplicationMetricOption.TEMP_AND_HUMIDITY -> Quintuple(
                first = tempText,
                second = breachBadge ?: "$shortRoom $humText",
                third = room.temperatureC.coerceIn(5f, 38f),
                fourth = 5f,
                fifth = 38f
            )
            ComplicationMetricOption.HUMIDITY_AND_DEW -> Quintuple(
                first = humText,
                second = breachBadge ?: "DP ${settings.tempUnit.format(room.dewPointC, 0)}",
                third = room.humidityPct.coerceIn(0f, 100f),
                fourth = 0f,
                fifth = 100f
            )
            ComplicationMetricOption.COMFORT_SCORE -> Quintuple(
                first = "${room.comfortScore}",
                second = breachBadge ?: "$shortRoom Comf",
                third = room.comfortScore.toFloat().coerceIn(0f, 100f),
                fourth = 0f,
                fifth = 100f
            )
            ComplicationMetricOption.PRESSURE -> {
                val p = room.relativePressureHpa ?: 1013.2f
                Quintuple(
                    first = "${p.roundToInt()}",
                    second = breachBadge ?: "hPa $shortRoom",
                    third = p.coerceIn(980f, 1040f),
                    fourth = 980f,
                    fifth = 1040f
                )
            }
        }

        val descText = PlainComplicationText.Builder("${room.roomName}: $tempText, $humText RH").build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortMain).build(),
                contentDescription = descText
            )
                .setTitle(PlainComplicationText.Builder(shortTitle).build())
                .setTapAction(tapPendingIntent)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(
                    if (breachBadge != null) {
                        "$shortRoom: $tempText • $humText ($breachBadge)"
                    } else {
                        "$shortRoom: $tempText • $humText • Comf ${room.comfortScore}"
                    }
                ).build(),
                contentDescription = descText
            )
                .setTitle(PlainComplicationText.Builder(room.roomName.substringBefore(" (")).build())
                .setTapAction(tapPendingIntent)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = rangeVal,
                min = rangeMin,
                max = rangeMax,
                contentDescription = descText
            )
                .setText(PlainComplicationText.Builder(shortMain).build())
                .setTitle(PlainComplicationText.Builder(shortTitle).build())
                .setTapAction(tapPendingIntent)
                .build()

            else -> null
        }
    }

    private data class Quintuple(
        val first: String,
        val second: String,
        val third: Float,
        val fourth: Float,
        val fifth: Float
    )
}
