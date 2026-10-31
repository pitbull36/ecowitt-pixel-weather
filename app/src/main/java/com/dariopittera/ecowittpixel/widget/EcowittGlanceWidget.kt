package com.dariopittera.ecowittpixel.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action. ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dariopittera.ecowittpixel.MainActivity
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.Psychrometrics
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.model.TempUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EcowittGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(110.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 120.dp)
        private val EXPANDED_SIZE = DpSize(260.dp, 210.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = LocalHistoryStore(context)
        val settings = store.loadSettings()
        val cached = store.loadWidgetSnapshotCache()
            ?: EcowittApiClient(store).generateSimulatedSnapshot(settings, SimulationParameters())

        provideContent {
            GlanceTheme {
                WidgetContent(
                    snapshot = cached,
                    tempUnit = settings.tempUnit
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        snapshot: HomeAtmosphereSnapshot,
        tempUnit: TempUnit
    ) {
        val size = LocalSize.current
        val isCompact = size.width < 210.dp
        val isExpanded = size.height >= 195.dp
        val timeStr = SimpleDateFormat("HH:mm", Locale.UK).format(Date(snapshot.lastSyncMs))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ColorProvider(Color(0xFF0E192B)))
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            if (isCompact) {
                CompactWidgetBody(snapshot, tempUnit, timeStr)
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Top header bar
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Ecowitt atmosphere",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${snapshot.gatewayModel} • updated $timeStr",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF90CAF9)),
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(ColorProvider(Color(0xFF1B2E4B)))
                                .clickable(actionRunCallback<RefreshWidgetAction>())
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Sync",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF80DEEA)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Multi-room sensor cards row
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        snapshot.rooms.take(3).forEachIndexed { idx, room ->
                            if (idx > 0) {
                                Spacer(modifier = GlanceModifier.width(6.dp))
                            }
                            RoomWidgetCell(
                                room = room,
                                tempUnit = tempUnit,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    if (isExpanded) {
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        val primaryRoom = snapshot.primaryGatewayRoom
                        val outdoorRoom = snapshot.outdoorRoom
                        val advice = if (primaryRoom != null) {
                            Psychrometrics.buildVentilationAdvice(primaryRoom, outdoorRoom, tempUnit)
                        } else {
                            snapshot.pressureForecastText
                        }
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(14.dp)
                                .background(ColorProvider(Color(0xFF15253E)))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(
                                    text = snapshot.pressureForecastText,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF80DEEA)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = advice,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFCFD8DC)),
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactWidgetBody(
        snapshot: HomeAtmosphereSnapshot,
        tempUnit: TempUnit,
        timeStr: String
    ) {
        val room = snapshot.primaryGatewayRoom ?: return
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = room.roomName.substringBefore(" ("),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF90CAF9)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = tempUnit.format(room.temperatureC),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "${room.humidityPct.toInt()}% RH • DP ${tempUnit.format(room.dewPointC, 0)}",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF80DEEA)),
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${room.comfortStatus.label} • $timeStr",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFB0BEC5)),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun RoomWidgetCell(
        room: SensorRoomState,
        tempUnit: TempUnit,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val accent = when {
            room.temperatureC >= 24.5f -> Color(0xFFFF8A65)
            room.temperatureC <= 16.5f -> Color(0xFF4DD0E1)
            else -> Color(0xFF26A69A)
        }
        Box(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(ColorProvider(Color(0xFF172740)))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = room.roomName.substringBefore(" ("),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFCFD8DC)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = tempUnit.format(room.temperatureC),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${room.humidityPct.toInt()}% RH",
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "DP ${tempUnit.format(room.dewPointC, 0)}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF90A4AE)),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val store = LocalHistoryStore(context)
        val settings = store.loadSettings()
        val api = EcowittApiClient(store)
        val updated = api.fetchAtmosphere(settings, SimulationParameters())
        store.saveWidgetSnapshotCache(updated)
        EcowittGlanceWidget().updateAll(context)
    }
}
