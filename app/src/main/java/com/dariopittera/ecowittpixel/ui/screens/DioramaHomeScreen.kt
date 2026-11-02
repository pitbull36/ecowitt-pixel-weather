package com.dariopittera.ecowittpixel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.dariopittera.ecowittpixel.model.MouldRiskLevel
import com.dariopittera.ecowittpixel.model.Psychrometrics
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.ui.shader.RoomDioramaCanvas
import com.dariopittera.ecowittpixel.ui.theme.ComfortEmerald
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.HumidViolet
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceCard
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceElevated
import com.dariopittera.ecowittpixel.ui.theme.ThermalAmber
import com.dariopittera.ecowittpixel.ui.theme.ThermalCoral
import com.dariopittera.ecowittpixel.ui.theme.humidityAccentColor
import com.dariopittera.ecowittpixel.ui.theme.temperatureAccentColor
import java.util.Locale

@Composable
fun DioramaHomeScreen(
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings,
    simParams: SimulationParameters,
    selectedRoomIndex: Int,
    wholeHomeOverview: Boolean,
    onSelectRoomIndex: (Int) -> Unit,
    onToggleWholeHome: () -> Unit,
    onUpdateRoomConfig: (String, RoomConfig) -> Unit,
    onUpdateSimParams: (SimulationParameters) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rooms = snapshot.rooms
    var stackEnabledChannelIds by remember(rooms.map { it.channelId }) {
        mutableStateOf(rooms.map { it.channelId }.toSet())
    }
    val visibleStackRooms = remember(rooms, stackEnabledChannelIds) {
        rooms.filter { it.channelId in stackEnabledChannelIds }.ifEmpty { rooms }
    }
    val safeIdx = selectedRoomIndex.coerceIn(0, (rooms.size - 1).coerceAtLeast(0))
    val activeRoom = rooms.getOrNull(safeIdx) ?: return
    val outdoorReference = snapshot.outdoorRoom
    var showSimSliders by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 1. Top Header Bar with Gateway Status & 3D Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ecowitt atmosphere",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ComfortEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${snapshot.source.label} • ${snapshot.gatewayModel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB0BEC5)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (settings.demoModeEnabled) {
                    AssistChip(
                        onClick = { showSimSliders = !showSimSliders },
                        label = { Text(if (showSimSliders) "Hide sliders" else "Sim controls") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Simulation controls",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = PixelSurfaceElevated,
                            labelColor = Color.White,
                            leadingIconContentColor = CoolCyan
                        )
                    )
                }

                AssistChip(
                    onClick = onToggleWholeHome,
                    label = { Text(if (wholeHomeOverview) "Single room" else "3D home stack") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (wholeHomeOverview) Icons.Outlined.ViewInAr else Icons.Outlined.Layers,
                            contentDescription = "Toggle 3D stack",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = PixelSurfaceElevated,
                        labelColor = Color.White,
                        leadingIconContentColor = ThermalAmber
                    )
                )

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh telemetry",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Horizontal Sensor Room Selector Strip (Single-select in Single Room mode; Multi-channel filter in 3D Stack mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wholeHomeOverview && rooms.size > 1) {
                val allEnabled = stackEnabledChannelIds.size >= rooms.size
                Surface(
                    color = if (allEnabled) Color(0xFF1F3654) else PixelSurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = if (allEnabled) 1.8.dp else 1.dp,
                            color = if (allEnabled) CoolCyan else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            stackEnabledChannelIds = rooms.map { it.channelId }.toSet()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (allEnabled) "✓ All channels" else "All channels",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (allEnabled) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "${stackEnabledChannelIds.size}/${rooms.size} shown in 3D",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90CAF9)
                        )
                    }
                }
            }

            rooms.forEachIndexed { idx, room ->
                val isSelected = if (wholeHomeOverview) {
                    room.channelId in stackEnabledChannelIds
                } else {
                    idx == safeIdx
                }
                val tempCol = temperatureAccentColor(room.temperatureC)
                Surface(
                    color = if (isSelected) Color(0xFF1F3352) else PixelSurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) tempCol else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            if (wholeHomeOverview) {
                                val next = stackEnabledChannelIds.toMutableSet()
                                if (room.channelId in next) {
                                    if (next.size > 1) {
                                        next.remove(room.channelId)
                                    }
                                } else {
                                    next.add(room.channelId)
                                }
                                stackEnabledChannelIds = next
                                onSelectRoomIndex(idx)
                            } else {
                                onSelectRoomIndex(idx)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) tempCol else tempCol.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = (if (wholeHomeOverview && isSelected) "✓ " else "") + room.roomName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            Text(
                                text = "${settings.tempUnit.format(room.temperatureC)}  •  ${room.humidityPct.toInt()}% RH",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) Color(0xFF90CAF9) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Interactive 3D Isometric Room Diorama Card (Full height in 3D stack mode, zero clutter overlays!)
        val canvasHeight = if (wholeHomeOverview) 530.dp else 340.dp
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                ) {
                    RoomDioramaCanvas(
                        room = activeRoom,
                        allRooms = if (wholeHomeOverview) visibleStackRooms else rooms,
                        wholeHomeOverview = wholeHomeOverview,
                        tempUnit = settings.tempUnit,
                        gyroEnabled = true,
                        timeOfDayHours = simParams.timeOfDayHours,
                        onSelectRoomIndex = onSelectRoomIndex,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Room Preset Archetype Switcher Bar below the 3D scene
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E192B))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3D scene preset:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF90A4AE)
                    )
                    RoomArchetype.entries.forEach { arch ->
                        val selected = activeRoom.archetype == arch
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val existing = settings.roomConfigs[activeRoom.channelId] ?: RoomConfig(
                                    channelId = activeRoom.channelId,
                                    customName = activeRoom.roomName,
                                    archetype = arch
                                )
                                onUpdateRoomConfig(
                                    activeRoom.channelId,
                                    existing.copy(
                                        archetype = arch,
                                        alertTempMinC = arch.scientificAlertTempMinC,
                                        alertTempMaxC = arch.scientificAlertTempMaxC,
                                        alertHumidityMaxPct = arch.scientificAlertHumidityMaxPct
                                    )
                                )
                            },
                            label = { Text(arch.title, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF264166),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 4. Collapsible Live Simulation Sliders
        AnimatedVisibility(visible = settings.demoModeEnabled && showSimSliders) {
            SimulationControlCard(
                simParams = simParams,
                activeRoom = activeRoom,
                onUpdateSimParams = onUpdateSimParams,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Smart Window Ventilation & Local Outdoor Weather Banner
        val effectiveOutdoor = snapshot.effectiveOutdoorWeather
        val ventilationText = Psychrometrics.buildVentilationAdvice(
            indoor = activeRoom,
            outdoor = outdoorReference,
            tempUnit = settings.tempUnit,
            localOutdoor = effectiveOutdoor
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoolCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Air,
                            contentDescription = "Ventilation insight",
                            tint = CoolCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeRoom.archetype.laymanRule} • comfort ${activeRoom.comfortScore}/100",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoolCyan
                        )
                        Text(
                            text = String.format(
                                Locale.UK,
                                "Room standard: target %s–%s and %.0f–%.0f%% RH (%s)",
                                settings.tempUnit.format(activeRoom.archetype.idealTempMinC),
                                settings.tempUnit.format(activeRoom.archetype.idealTempMaxC),
                                activeRoom.archetype.idealHumidityMin,
                                activeRoom.archetype.idealHumidityMax,
                                activeRoom.archetype.standardCitation
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF93C5FD)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = ventilationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                if (effectiveOutdoor != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFF0E1D31))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Outdoor weather (${effectiveOutdoor.locationName}): ${effectiveOutdoor.conditionLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF93C5FD)
                        )
                        Text(
                            text = String.format(
                                Locale.UK,
                                "%s • %.0f%% RH (%.1f g/m³) • %s",
                                settings.tempUnit.format(effectiveOutdoor.temperatureC),
                                effectiveOutdoor.humidityPct,
                                effectiveOutdoor.absoluteHumidityGm3,
                                settings.pressureUnit.format(effectiveOutdoor.pressureHpa)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. When in Full Stack View, show a compressed scrollable summary strip; otherwise full 2x2 Bento Grid
        if (wholeHomeOverview) {
            CompressedMetricsStrip(
                activeRoom = activeRoom,
                snapshot = snapshot,
                settings = settings
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val tempDelta = settings.tempUnit.convert(activeRoom.temperatureC) -
                    settings.tempUnit.convert(activeRoom.temperatureC - activeRoom.tempTrend1hC)
                val tempLaymanTrend = when {
                    tempDelta >= 0.4f -> "Warming up"
                    tempDelta <= -0.4f -> "Cooling down"
                    else -> "Steady warmth"
                }
                BentoMetricCard(
                    title = "Temperature",
                    primaryValue = settings.tempUnit.format(activeRoom.temperatureC),
                    secondaryLabel = "Feels like ${settings.tempUnit.format(activeRoom.feelsLikeC)}",
                    footnote = String.format(
                        Locale.UK,
                        "%s; ideal for this room is %.0f–%.0f%s (1h change: %+.1f%s)",
                        tempLaymanTrend,
                        settings.tempUnit.convert(activeRoom.archetype.idealTempMinC),
                        settings.tempUnit.convert(activeRoom.archetype.idealTempMaxC),
                        settings.tempUnit.symbol,
                        tempDelta,
                        settings.tempUnit.symbol
                    ),
                    accentColor = temperatureAccentColor(activeRoom.temperatureC),
                    icon = Icons.Outlined.Thermostat,
                    modifier = Modifier.weight(1f)
                )

                val humLaymanLabel = when {
                    activeRoom.humidityPct < 35f -> "Dry air"
                    activeRoom.humidityPct <= activeRoom.archetype.idealHumidityMax -> "Comfortable moisture"
                    activeRoom.humidityPct < activeRoom.archetype.scientificAlertHumidityMaxPct -> "Slightly humid (safe)"
                    else -> "Damp air"
                }
                BentoMetricCard(
                    title = "Humidity and moisture",
                    primaryValue = String.format(Locale.UK, "%.0f%%", activeRoom.humidityPct),
                    secondaryLabel = String.format(
                        Locale.UK,
                        "%s (%.1f g/m³)",
                        humLaymanLabel,
                        activeRoom.absoluteHumidityGm3
                    ),
                    footnote = String.format(
                        Locale.UK,
                        "Ideal %.0f–%.0f%% RH; mould alert only if >%.0f%% for 4h+ (VPD: %.2f kPa)",
                        activeRoom.archetype.idealHumidityMin,
                        activeRoom.archetype.idealHumidityMax,
                        activeRoom.archetype.scientificAlertHumidityMaxPct,
                        activeRoom.vapourPressureDeficitKpa
                    ),
                    accentColor = humidityAccentColor(activeRoom.humidityPct),
                    icon = Icons.Outlined.WaterDrop,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mouldColor = when (activeRoom.mouldRisk) {
                    MouldRiskLevel.LOW -> ComfortEmerald
                    MouldRiskLevel.MODERATE -> ThermalAmber
                    MouldRiskLevel.HIGH -> ThermalCoral
                }
                val dewMarginC = (activeRoom.temperatureC - activeRoom.dewPointC).coerceAtLeast(0f)
                BentoMetricCard(
                    title = "Condensation and mould",
                    primaryValue = settings.tempUnit.format(activeRoom.dewPointC),
                    secondaryLabel = activeRoom.mouldRisk.label,
                    footnote = String.format(
                        Locale.UK,
                        "%s (moisture condenses on surfaces below %s, safety margin %.1f°C)",
                        activeRoom.mouldRisk.detail,
                        settings.tempUnit.format(activeRoom.dewPointC),
                        dewMarginC
                    ),
                    accentColor = mouldColor,
                    icon = Icons.Outlined.WbSunny,
                    modifier = Modifier.weight(1f)
                )

                val pressureVal = snapshot.pressureHpa
                val pTrend = snapshot.pressureTrend3hHpa ?: 0f
                val baroLaymanTrend = when {
                    pTrend <= -2.0f -> "Falling fast (${String.format(Locale.UK, "%+.1f", pTrend)} hPa/3h)"
                    pTrend <= -0.7f -> "Easing (${String.format(Locale.UK, "%+.1f", pTrend)} hPa/3h)"
                    pTrend >= 1.5f -> "Rising (${String.format(Locale.UK, "%+.1f", pTrend)} hPa/3h)"
                    else -> "Steady (${String.format(Locale.UK, "%+.1f", pTrend)} hPa/3h)"
                }
                BentoMetricCard(
                    title = "GW1200 barometer",
                    primaryValue = if (pressureVal != null) {
                        settings.pressureUnit.format(pressureVal)
                    } else {
                        "1015 hPa"
                    },
                    secondaryLabel = baroLaymanTrend,
                    footnote = snapshot.pressureForecastText,
                    accentColor = CoolCyan,
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CompressedMetricsStrip(
    activeRoom: SensorRoomState,
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${activeRoom.roomName} climate summary",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Feels like", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    Text(
                        settings.tempUnit.format(activeRoom.feelsLikeC),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = temperatureAccentColor(activeRoom.temperatureC)
                    )
                }
                Column {
                    Text("Dew point", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    Text(
                        settings.tempUnit.format(activeRoom.dewPointC),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CoolCyan
                    )
                }
                Column {
                    Text("Moisture", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    Text(
                        String.format(Locale.UK, "%.1f g/m³", activeRoom.absoluteHumidityGm3),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ComfortEmerald
                    )
                }
                Column {
                    Text("GW1200 pressure", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    Text(
                        snapshot.pressureHpa?.let { settings.pressureUnit.format(it) } ?: "1015 hPa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Feels like, dew point, and moisture (g/m³) are calculated directly from this sensor's temperature and humidity. Barometric pressure is measured by the GW1200 gateway for the whole home.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF78909C),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BentoMetricCard(
    title: String,
    primaryValue: String,
    secondaryLabel: String,
    footnote: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB0BEC5)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun SimulationControlCard(
    simParams: SimulationParameters,
    activeRoom: SensorRoomState,
    onUpdateSimParams: (SimulationParameters) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16253D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Interactive 3D atmosphere simulator (${activeRoom.roomName.substringBefore(" (")})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Adjust sliders to preview thermometer colours, humidity vapour droplets, and day or night lighting.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0BEC5)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = String.format(
                    Locale.UK,
                    "Time of day lighting: %02d:%02d",
                    simParams.timeOfDayHours.toInt(),
                    ((simParams.timeOfDayHours % 1f) * 60f).toInt()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = ThermalAmber
            )
            Slider(
                value = simParams.timeOfDayHours,
                onValueChange = { onUpdateSimParams(simParams.copy(timeOfDayHours = it)) },
                valueRange = 0f..23.9f,
                colors = SliderDefaults.colors(thumbColor = ThermalAmber, activeTrackColor = ThermalAmber)
            )

            val tempOffset = when (activeRoom.channelId) {
                "gateway" -> simParams.livingRoomTempOffsetC
                "ch1" -> simParams.bedroomTempOffsetC
                else -> simParams.outdoorTempOffsetC
            }
            Text(
                text = String.format(Locale.UK, "Room temperature offset: %+.1f°C", tempOffset),
                style = MaterialTheme.typography.labelMedium,
                color = ThermalCoral
            )
            Slider(
                value = tempOffset,
                onValueChange = { newVal ->
                    val updated = when (activeRoom.channelId) {
                        "gateway" -> simParams.copy(livingRoomTempOffsetC = newVal)
                        "ch1" -> simParams.copy(bedroomTempOffsetC = newVal)
                        else -> simParams.copy(outdoorTempOffsetC = newVal)
                    }
                    onUpdateSimParams(updated)
                },
                valueRange = -8f..10f,
                colors = SliderDefaults.colors(thumbColor = ThermalCoral, activeTrackColor = ThermalCoral)
            )

            val humOffset = when (activeRoom.channelId) {
                "gateway" -> simParams.livingRoomHumidityOffsetPct
                "ch1" -> simParams.bedroomHumidityOffsetPct
                else -> simParams.outdoorHumidityOffsetPct
            }
            Text(
                text = String.format(Locale.UK, "Room humidity offset: %+.0f%% RH", humOffset),
                style = MaterialTheme.typography.labelMedium,
                color = HumidViolet
            )
            Slider(
                value = humOffset,
                onValueChange = { newVal ->
                    val updated = when (activeRoom.channelId) {
                        "gateway" -> simParams.copy(livingRoomHumidityOffsetPct = newVal)
                        "ch1" -> simParams.copy(bedroomHumidityOffsetPct = newVal)
                        else -> simParams.copy(outdoorHumidityOffsetPct = newVal)
                    }
                    onUpdateSimParams(updated)
                },
                valueRange = -25f..35f,
                colors = SliderDefaults.colors(thumbColor = HumidViolet, activeTrackColor = HumidViolet)
            )
        }
    }
}
