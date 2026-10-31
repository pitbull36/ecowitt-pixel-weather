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

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Horizontal Sensor Room Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rooms.forEachIndexed { idx, room ->
                val isSelected = idx == safeIdx
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
                        .clickable { onSelectRoomIndex(idx) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tempCol)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = room.roomName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "${settings.tempUnit.format(room.temperatureC)}  •  ${room.humidityPct.toInt()}% RH",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF90CAF9)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Interactive 3D Isometric Room Diorama Card
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                ) {
                    RoomDioramaCanvas(
                        room = activeRoom,
                        allRooms = rooms,
                        wholeHomeOverview = wholeHomeOverview,
                        tempUnit = settings.tempUnit,
                        gyroEnabled = settings.gyroscopeParallaxEnabled,
                        timeOfDayHours = simParams.timeOfDayHours,
                        onSelectRoomIndex = onSelectRoomIndex,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top-left overlay: room title & comfort score pill
                    Surface(
                        color = Color(0xBF0B1526),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = if (wholeHomeOverview) {
                                    "Whole-home 3D cutaway (${rooms.size} zones)"
                                } else {
                                    activeRoom.roomName
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${activeRoom.comfortStatus.label} • comfort ${activeRoom.comfortScore}/100",
                                style = MaterialTheme.typography.labelSmall,
                                color = temperatureAccentColor(activeRoom.temperatureC)
                            )
                        }
                    }

                    // Bottom-left hint: tilt phone or drag to orbit
                    Surface(
                        color = Color(0x990B1526),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (wholeHomeOverview) {
                                "Tap any floor to focus • drag or tilt Pixel to orbit"
                            } else {
                                "Tilt phone or drag to orbit 3D room • double-tap to reset"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCFD8DC),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Room Preset Archetype Switcher Bar below the 3D scene
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E192B))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    existing.copy(archetype = arch)
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

        // 4. Collapsible Live Simulation Sliders (when in Demo/Simulation Mode)
        AnimatedVisibility(visible = settings.demoModeEnabled && showSimSliders) {
            SimulationControlCard(
                simParams = simParams,
                activeRoom = activeRoom,
                onUpdateSimParams = onUpdateSimParams,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Smart Window Ventilation & Outdoor Comparison Banner
        val ventilationText = Psychrometrics.buildVentilationAdvice(
            indoor = activeRoom,
            outdoor = outdoorReference,
            tempUnit = settings.tempUnit
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CoolCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Air,
                        contentDescription = "Ventilation insight",
                        tint = CoolCyan
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart ventilation advisor",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CoolCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ventilationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Modern 2x2 Bento Grid for Psychrometric Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Temperature & Feels-Like Card
            BentoMetricCard(
                title = "Temperature",
                primaryValue = settings.tempUnit.format(activeRoom.temperatureC),
                secondaryLabel = "Feels like ${settings.tempUnit.format(activeRoom.feelsLikeC)}",
                footnote = String.format(
                    Locale.UK,
                    "1h trend: %+.1f%s • target %.0f–%.0f%s",
                    settings.tempUnit.convert(activeRoom.temperatureC) -
                        settings.tempUnit.convert(activeRoom.temperatureC - activeRoom.tempTrend1hC),
                    settings.tempUnit.symbol,
                    settings.tempUnit.convert(activeRoom.archetype.idealTempMinC),
                    settings.tempUnit.convert(activeRoom.archetype.idealTempMaxC),
                    settings.tempUnit.symbol
                ),
                accentColor = temperatureAccentColor(activeRoom.temperatureC),
                icon = Icons.Outlined.Thermostat,
                modifier = Modifier.weight(1f)
            )

            // Relative & Absolute Humidity Card
            BentoMetricCard(
                title = "Humidity and vapour",
                primaryValue = String.format(Locale.UK, "%.0f%%", activeRoom.humidityPct),
                secondaryLabel = String.format(
                    Locale.UK,
                    "Absolute: %.1f g/m³",
                    activeRoom.absoluteHumidityGm3
                ),
                footnote = String.format(
                    Locale.UK,
                    "VPD: %.2f kPa • 1h trend: %+.1f%%",
                    activeRoom.vapourPressureDeficitKpa,
                    activeRoom.humidityTrend1hPct
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
            // Dew Point & Mould / Condensation Risk Card
            val mouldColor = when (activeRoom.mouldRisk) {
                MouldRiskLevel.LOW -> ComfortEmerald
                MouldRiskLevel.MODERATE -> ThermalAmber
                MouldRiskLevel.HIGH -> ThermalCoral
            }
            BentoMetricCard(
                title = "Dew point and mould",
                primaryValue = settings.tempUnit.format(activeRoom.dewPointC),
                secondaryLabel = activeRoom.mouldRisk.label,
                footnote = activeRoom.mouldRisk.detail,
                accentColor = mouldColor,
                icon = Icons.Outlined.WbSunny,
                modifier = Modifier.weight(1f)
            )

            // Barometric Pressure & GW1200 Weather Forecast Card
            val pressureVal = snapshot.pressureHpa
            BentoMetricCard(
                title = "GW1200 barometer",
                primaryValue = if (pressureVal != null) {
                    settings.pressureUnit.format(pressureVal)
                } else {
                    "1015 hPa"
                },
                secondaryLabel = snapshot.pressureTrend3hHpa?.let {
                    String.format(Locale.UK, "3h delta: %+.1f hPa", it)
                } ?: "Steady pressure",
                footnote = snapshot.pressureForecastText,
                accentColor = CoolCyan,
                icon = Icons.Outlined.Speed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
                text = "Adjust sliders to preview heat shimmer, humidity vapour droplets, window condensation, and day or night lighting.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0BEC5)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Time of day slider
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

            // Active room temperature offset
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

            // Active room humidity offset
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
