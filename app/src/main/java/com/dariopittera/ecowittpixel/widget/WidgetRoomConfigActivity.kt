package com.dariopittera.ecowittpixel.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.ui.theme.ComfortEmerald
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.EcowittPixelTheme
import com.dariopittera.ecowittpixel.ui.theme.PixelSkyNavy
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceCard
import com.dariopittera.ecowittpixel.ui.theme.createNormalizedPhoneContext
import com.dariopittera.ecowittpixel.worker.WeatherSyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Configuration activity launched when placing or reconfiguring the 1×1 Room Circle widget
 * (`EcowittSingleRoomWidgetReceiver`) or resized 4×2 widget (`EcowittWidgetReceiver`).
 * Lets the user select which room sensor channel the circular dial displays and previews the
 * circular temperature + humidity bezel dial live.
 */
class WidgetRoomConfigActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(createNormalizedPhoneContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Default result if user backs out before confirming
        val cancelIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelIntent)

        val store = LocalHistoryStore(applicationContext)
        val settings = store.loadSettings()
        val snapshot = store.loadWidgetSnapshotCache()
            ?: settings.applyToSnapshot(
                EcowittApiClient(store).generateSimulatedSnapshot(settings, SimulationParameters())
            )
        val initialChannel = WidgetPreferences.getWidgetRoomChannel(applicationContext, appWidgetId)
        val syncTimeStr = SimpleDateFormat("HH:mm", Locale.UK).format(Date(snapshot.lastSyncMs))

        setContent {
            EcowittPixelTheme {
                val scope = rememberCoroutineScope()
                var selectedChannelId by remember {
                    mutableStateOf(
                        snapshot.rooms.firstOrNull {
                            it.channelId.equals(initialChannel, ignoreCase = true)
                        }?.channelId
                            ?: snapshot.rooms.firstOrNull()?.channelId
                            ?: "gateway"
                    )
                }

                Surface(
                    color = PixelSkyNavy,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select room for 1×1 circle widget",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose which room this circular temperature and humidity dial displays on your home screen. You can also tap ⇄ on the widget corner anytime to switch rooms, or ↻ to force an immediate sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Live 1×1 circular gauge preview for the currently selected room
                        val activeRoom = snapshot.rooms.firstOrNull { it.channelId == selectedChannelId }
                            ?: snapshot.rooms.firstOrNull()
                        if (activeRoom != null) {
                            val previewBmp = remember(activeRoom, settings.tempUnit, syncTimeStr) {
                                WidgetCircularGaugeRenderer.renderRoomCircleBitmap(
                                    room = activeRoom,
                                    tempUnit = settings.tempUnit,
                                    diameterPx = 340,
                                    showSubtitle = true,
                                    syncTimeStr = syncTimeStr
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = activeRoom.roomName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Room cards list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            snapshot.rooms.forEachIndexed { idx, room ->
                                val isSelected = room.channelId == selectedChannelId
                                val dialThumb = remember(room, settings.tempUnit) {
                                    WidgetCircularGaugeRenderer.renderRoomCircleBitmap(
                                        room = room,
                                        tempUnit = settings.tempUnit,
                                        diameterPx = 160,
                                        showSubtitle = false
                                    )
                                }
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF183152) else PixelSurfaceCard
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) CoolCyan else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            selectedChannelId = room.channelId
                                            WidgetPreferences.saveWidgetRoomSelection(
                                                context = applicationContext,
                                                appWidgetId = appWidgetId,
                                                channelId = room.channelId,
                                                roomIndex = idx,
                                                roomName = room.roomName
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            bitmap = dialThumb.asImageBitmap(),
                                            contentDescription = room.roomName,
                                            modifier = Modifier.size(54.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = room.roomName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${room.hardwareLabel} • ${settings.tempUnit.format(room.temperatureC)} • ${room.humidityPct.toInt()}% RH",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF90CAF9),
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Text(
                                                text = "Selected ✓",
                                                color = ComfortEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = {
                                val chosenIdx = snapshot.rooms.indexOfFirst {
                                    it.channelId == selectedChannelId
                                }.coerceAtLeast(0)
                                val chosenRoom = snapshot.rooms.getOrNull(chosenIdx)
                                val chosenName = chosenRoom?.roomName ?: ""

                                scope.launch {
                                    WidgetPreferences.applyRoomSelectionAndRefresh(
                                        context = applicationContext,
                                        appWidgetId = appWidgetId,
                                        channelId = selectedChannelId,
                                        roomIndex = chosenIdx,
                                        roomName = chosenName
                                    )
                                    EcowittGlanceWidget.updateAllWidgets(applicationContext)
                                    WeatherSyncWorker.schedulePeriodicSync(
                                        applicationContext,
                                        settings.pollingIntervalMinutes
                                    )

                                    // Also broadcast standard AppWidgetManager update for immediate launcher redraw
                                    val singleUpdateIntent = Intent(
                                        applicationContext,
                                        EcowittSingleRoomWidgetReceiver::class.java
                                    ).apply {
                                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                    }
                                    sendBroadcast(singleUpdateIntent)

                                    val multiUpdateIntent = Intent(
                                        applicationContext,
                                        EcowittWidgetReceiver::class.java
                                    ).apply {
                                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                    }
                                    sendBroadcast(multiUpdateIntent)

                                    val resultValue = Intent().putExtra(
                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        appWidgetId
                                    )
                                    setResult(Activity.RESULT_OK, resultValue)
                                    finish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Use this room on home screen",
                                color = Color(0xFF062019),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
