package com.dariopittera.ecowittpixel.watch.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.AlertEvaluator
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ConnectionSource
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.watch.WatchMainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class EcowittTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        return CallbackToFutureAdapter.getFuture { completer ->
            tileScope.launch {
                try {
                    val store = LocalHistoryStore(applicationContext)
                    var settings = store.loadSettings().copy(demoModeEnabled = false)
                    val cachedSnap = store.loadWidgetSnapshotCache()?.takeIf { !it.isSimulatedOrPreview }
                    val isManualRefresh = requestParams.currentState.lastClickableId == ID_REFRESH_TILE
                    val isCacheFresh = cachedSnap != null &&
                        (System.currentTimeMillis() - cachedSnap.lastSyncMs) <= 10 * 60 * 1000L

                    var snapshot: HomeAtmosphereSnapshot
                    if (!isManualRefresh && isCacheFresh && cachedSnap != null && store.hasSyncedFromPhone()) {
                        snapshot = cachedSnap
                    } else {
                        val requestedCount = if (isManualRefresh || cachedSnap == null) {
                            WearDataLayerBridge.requestSyncFromPhone(applicationContext)
                        } else {
                            0
                        }
                        if (isManualRefresh && requestedCount > 0) {
                            delay(400L)
                        }
                        val (dlSettings, dlSnap) = WearDataLayerBridge.pullFromDataLayer(applicationContext, store)
                        if (dlSettings != null) {
                            settings = dlSettings.copy(demoModeEnabled = false)
                            store.saveSettings(settings)
                        }
                        snapshot = dlSnap
                            ?: cachedSnap
                            ?: EcowittApiClient(store).fetchAtmosphere(settings, SimulationParameters())

                        if (isManualRefresh ||
                            snapshot.isSimulatedOrPreview ||
                            !store.hasSyncedFromPhone()
                        ) {
                            val fresh = EcowittApiClient(store).fetchAtmosphere(settings, SimulationParameters())
                            if (!fresh.isSimulatedOrPreview) {
                                snapshot = fresh
                            }
                            if (snapshot.isSimulatedOrPreview || !store.hasSyncedFromPhone()) {
                                val (discSettings, discSnap) = WearDataLayerBridge.discoverPhoneOrGatewayOnLan(
                                    applicationContext,
                                    store
                                )
                                if (discSettings != null) {
                                    settings = discSettings.copy(demoModeEnabled = false)
                                    store.saveSettings(settings)
                                }
                                if (discSnap != null && !discSnap.isSimulatedOrPreview) {
                                    snapshot = discSnap
                                }
                            }
                            if (isManualRefresh && !snapshot.isSimulatedOrPreview) {
                                snapshot = snapshot.copy(lastSyncMs = System.currentTimeMillis())
                            }
                        }
                    }

                    snapshot = settings.applyToSnapshot(snapshot)
                    store.saveWidgetSnapshotCache(snapshot)

                    val tileLayout = buildTileLayout(snapshot, settings)
                    val tile = TileBuilders.Tile.Builder()
                        .setResourcesVersion(RESOURCES_VERSION)
                        .setFreshnessIntervalMillis(15 * 60 * 1000L)
                        .setTileTimeline(
                            TimelineBuilders.Timeline.fromLayoutElement(tileLayout)
                        )
                        .build()
                    completer.set(tile)
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "EcowittTileRequest"
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .build()
            )
            "EcowittTileResources"
        }
    }

    private fun buildTileLayout(
        snapshot: HomeAtmosphereSnapshot,
        settings: AppSettings
    ): LayoutElementBuilders.LayoutElement {
        val primaryRoom = snapshot.primaryGatewayRoom ?: snapshot.rooms.firstOrNull()
        val tempC = primaryRoom?.temperatureC ?: 21.0f
        val humPct = primaryRoom?.humidityPct ?: 50.0f

        val tempColorArgb = tileTemperatureArgb(tempC)
        val humColorArgb = tileHumidityArgb(humPct)

        val tempSweepDeg = (((tempC - 8f) / 26f).coerceIn(0.10f, 1f)) * 106f
        val humSweepDeg = ((humPct / 100f).coerceIn(0.10f, 1f)) * 106f

        val breaches = AlertEvaluator.evaluateBreaches(snapshot, settings)

        // Outer top bezel arc (Temperature, centered at 0° top)
        val topTrackArc = Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(106f))
                    .setThickness(dp(5f))
                    .setColor(argb(0xFF162438.toInt()))
                    .build()
            )
            .build()

        val topFillArc = Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(tempSweepDeg))
                    .setThickness(dp(5f))
                    .setColor(argb(tempColorArgb))
                    .build()
            )
            .build()

        // Outer bottom bezel arc (Humidity, centered at 180° bottom, bluish water tone)
        val bottomTrackArc = Arc.Builder()
            .setAnchorAngle(degrees(180f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(106f))
                    .setThickness(dp(5f))
                    .setColor(argb(0xFF0E1B2E.toInt()))
                    .build()
            )
            .build()

        val bottomFillArc = Arc.Builder()
            .setAnchorAngle(degrees(180f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(humSweepDeg))
                    .setThickness(dp(5f))
                    .setColor(argb(humColorArgb))
                    .build()
            )
            .build()

        // Central 3-room summary column
        val centerColumn = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // Header label inside top arc
        centerColumn.addContent(
            Text.Builder()
                .setText("ECOWITT ROOMS")
                .setFontStyle(
                    FontStyle.Builder()
                        .setSize(sp(9f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(argb(0xFF60A5FA.toInt()))
                        .build()
                )
                .build()
        )
        centerColumn.addContent(Spacer.Builder().setHeight(dp(4f)).build())

        snapshot.rooms.take(3).forEachIndexed { idx, room ->
            if (idx > 0) {
                centerColumn.addContent(Spacer.Builder().setHeight(dp(3f)).build())
            }
            val hasBreach = breaches.any { it.channelId == room.channelId }
            centerColumn.addContent(buildRoomRow(room, settings, hasBreach))
        }

        centerColumn.addContent(Spacer.Builder().setHeight(dp(5f)).build())

        // Tap-to-refresh footer pill above the bottom humidity arc
        val refreshClickable = Clickable.Builder()
            .setId(ID_REFRESH_TILE)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val syncDateTimeStr = SimpleDateFormat("dd MMM HH:mm", Locale.UK)
            .format(Date(snapshot.lastSyncMs))

        val footerText = if (breaches.isNotEmpty()) {
            "Alert: ${breaches.first().shortBadge} • $syncDateTimeStr"
        } else {
            "Tap to sync • $syncDateTimeStr"
        }

        centerColumn.addContent(
            Box.Builder()
                .setModifiers(
                    Modifiers.Builder()
                        .setClickable(refreshClickable)
                        .setBackground(
                            Background.Builder()
                                .setColor(argb(if (breaches.isNotEmpty()) 0xFF3A121A.toInt() else 0xFF102236.toInt()))
                                .setCorner(Corner.Builder().setRadius(dp(10f)).build())
                                .build()
                        )
                        .setPadding(
                            Padding.Builder()
                                .setStart(dp(8f))
                                .setEnd(dp(8f))
                                .setTop(dp(2f))
                                .setBottom(dp(2f))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Text.Builder()
                        .setText(footerText)
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(9f))
                                .setColor(argb(if (breaches.isNotEmpty()) 0xFFFFCDD2.toInt() else 0xFF93C5FD.toInt()))
                                .build()
                        )
                        .build()
                )
                .build()
        )

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(0xFF000000.toInt())).build())
                    .build()
            )
            .addContent(topTrackArc)
            .addContent(topFillArc)
            .addContent(bottomTrackArc)
            .addContent(bottomFillArc)
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setModifiers(
                        Modifiers.Builder()
                            .setPadding(
                                Padding.Builder()
                                    .setStart(dp(20f))
                                    .setEnd(dp(20f))
                                    .setTop(dp(16f))
                                    .setBottom(dp(16f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(centerColumn.build())
                    .build()
            )
            .build()
    }

    private fun buildRoomRow(
        room: SensorRoomState,
        settings: AppSettings,
        hasBreach: Boolean
    ): LayoutElementBuilders.LayoutElement {
        val openRoomAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(WatchMainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        WatchMainActivity.EXTRA_CHANNEL_ID,
                        ActionBuilders.stringExtra(room.channelId)
                    )
                    .build()
            )
            .build()

        val clickable = Clickable.Builder()
            .setId("open_${room.channelId}")
            .setOnClick(openRoomAction)
            .build()

        val shortName = formatTileRoomName(room.roomName).take(12)
        val tempStr = settings.tempUnit.format(room.temperatureC)
        val humStr = "${room.humidityPct.roundToInt()}%"
        val rowBg = if (hasBreach) 0xFF2D141C.toInt() else 0xFF111E30.toInt()

        return Box.Builder()
            .setWidth(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(rowBg))
                            .setCorner(Corner.Builder().setRadius(dp(10f)).build())
                            .build()
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(8f))
                            .setEnd(dp(8f))
                            .setTop(dp(3f))
                            .setBottom(dp(3f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText(if (hasBreach) "! $shortName" else shortName)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(tempStr)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(tileTemperatureArgb(room.temperatureC)))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(humStr)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(10f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(tileHumidityArgb(room.humidityPct)))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun formatTileRoomName(rawName: String): String {
        return rawName
            .replace(" (GW1200)", "", ignoreCase = true)
            .replace(Regex("""\s*\(Ch\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { rawName }
    }

    private fun tileTemperatureArgb(tempC: Float): Int {
        return when {
            tempC >= 26.0f -> 0xFFEF5350.toInt()
            tempC >= 23.0f -> 0xFFFF9800.toInt()
            tempC >= 19.0f -> 0xFFFFD54F.toInt()
            else -> 0xFFF8FAFC.toInt()
        }
    }

    private fun tileHumidityArgb(humidityPct: Float): Int {
        return when {
            humidityPct < 35f -> 0xFF60A5FA.toInt()
            humidityPct < 60f -> 0xFF38BDF8.toInt()
            humidityPct < 65f -> 0xFF0EA5E9.toInt()
            else -> 0xFF3B82F6.toInt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }

    companion object {
        private const val RESOURCES_VERSION = "ecowitt_tile_v5"
        private const val ID_REFRESH_TILE = "id_refresh_tile"
    }
}
