package com.dariopittera.ecowittpixel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.ui.charts.ChartMetric
import com.dariopittera.ecowittpixel.ui.charts.DailyRangeBarsCard
import com.dariopittera.ecowittpixel.ui.charts.HourlyThermalHeatmapCard
import com.dariopittera.ecowittpixel.ui.charts.MultiRoomSplineChart
import com.dariopittera.ecowittpixel.ui.charts.PsychrometricComfortScatterChart
import com.dariopittera.ecowittpixel.ui.theme.ComfortEmerald
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceCard

@Composable
fun AnalyticsScreen(
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(ChartMetric.TEMPERATURE) }
    var windowHours by remember { mutableIntStateOf(24) }
    var focusedRoomIdx by remember { mutableIntStateOf(0) }
    var heatmapHumidity by remember { mutableStateOf(false) }

    val rooms = snapshot.rooms
    val focusedRoom = rooms.getOrNull(focusedRoomIdx.coerceIn(0, (rooms.size - 1).coerceAtLeast(0)))
    val totalHistorySamples = remember(rooms) { rooms.sumOf { it.history.size } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Climate analytics",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Multi-sensor timeline, psychrometric comfort zone, and hourly thermal patterns.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0BEC5)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Clean dedicated SQLite archive & CSV export banner card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13263B)),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = CoolCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B3552)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = "SQLite archive",
                            tint = CoolCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Local SQLite archive (${rooms.size} rooms)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "$totalHistorySamples logged points + Ecowitt cloud backfill",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "Export CSV",
                        tint = Color(0xFF06211E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export CSV",
                        color = Color(0xFF06211E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Multi-Sensor Overlaid Spline Timeline Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartMetric.entries.forEach { m ->
                        FilterChip(
                            selected = selectedMetric == m,
                            onClick = { selectedMetric = m },
                            label = { Text(m.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF234168),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    listOf(24 to "24h", 168 to "7d", 720 to "30d", 87600 to "All").forEach { (hrs, label) ->
                        FilterChip(
                            selected = windowHours == hrs,
                            onClick = { windowHours = hrs },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E524E),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MultiRoomSplineChart(
                    rooms = rooms,
                    metric = selectedMetric,
                    windowHours = windowHours,
                    tempUnit = settings.tempUnit,
                    pressureUnit = settings.pressureUnit
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Psychrometric Comfort Matrix (Scatter Plot + ASHRAE Comfort Polygon)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PsychrometricComfortScatterChart(
                    rooms = rooms,
                    tempUnit = settings.tempUnit
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Room Selector for Daily Range Bars & Hourly Heatmap
        if (focusedRoom != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inspect sensor:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF90A4AE)
                )
                rooms.forEachIndexed { idx, room ->
                    FilterChip(
                        selected = focusedRoomIdx == idx,
                        onClick = { focusedRoomIdx = idx },
                        label = { Text(room.roomName.substringBefore(" (")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF234168),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Pixel Weather Style 7-Day Min/Max Range Bars
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DailyRangeBarsCard(
                        room = focusedRoom,
                        tempUnit = settings.tempUnit
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. 24-Hour × 7-Day Thermal / Humidity Calendar Heatmap
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !heatmapHumidity,
                            onClick = { heatmapHumidity = false },
                            label = { Text("Temperature heatmap") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5D3122),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = heatmapHumidity,
                            onClick = { heatmapHumidity = true },
                            label = { Text("Humidity heatmap") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF392B58),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HourlyThermalHeatmapCard(
                        room = focusedRoom,
                        showHumidity = heatmapHumidity
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
