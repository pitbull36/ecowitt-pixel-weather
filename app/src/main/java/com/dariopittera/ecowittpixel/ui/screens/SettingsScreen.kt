package com.dariopittera.ecowittpixel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.PressureUnit
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.TempUnit
import com.dariopittera.ecowittpixel.ui.theme.ComfortEmerald
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceCard
import com.dariopittera.ecowittpixel.ui.theme.ThermalAmber
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    snapshot: HomeAtmosphereSnapshot,
    isScanningSubnet: Boolean,
    statusBanner: String?,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateRoomConfig: (String, RoomConfig) -> Unit,
    onScanSubnet: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ipText by remember(settings.gatewayIp) { mutableStateOf(settings.gatewayIp) }
    var appKeyText by remember(settings.cloudApplicationKey) { mutableStateOf(settings.cloudApplicationKey) }
    var apiKeyText by remember(settings.cloudApiKey) { mutableStateOf(settings.cloudApiKey) }
    var macText by remember(settings.cloudDeviceMac) { mutableStateOf(settings.cloudDeviceMac) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Gateway, rooms, and alerts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Configure your Ecowitt GW1200 local LAN connection, cloud fallback, 3D room presets, and Pixel notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0BEC5)
        )

        if (!statusBanner.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3448)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Simulation Mode vs Live GW1200 Telemetry Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Interactive demo and simulation mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Turn off when you are ready to poll your real GW1200 over local WiFi or Ecowitt cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                    Switch(
                        checked = settings.demoModeEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(
                                settings.copy(
                                    demoModeEnabled = enabled,
                                    gatewayIp = ipText,
                                    cloudApplicationKey = appKeyText,
                                    cloudApiKey = apiKeyText,
                                    cloudDeviceMac = macText
                                )
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. GW1200 Local LAN HTTP API Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Router,
                        contentDescription = "GW1200 LAN",
                        tint = ComfortEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GW1200 local WiFi HTTP API",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Queries http://<ip>/get_livedata_info directly on your home LAN for sub-second updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("GW1200 local IPv4 address") },
                    placeholder = { Text("e.g. 192.168.1.120") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanSubnet,
                        enabled = !isScanningSubnet,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanningSubnet) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CoolCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Radar,
                                contentDescription = "Scan LAN",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScanningSubnet) "Scanning..." else "Auto-find GW1200")
                    }

                    Button(
                        onClick = {
                            onUpdateSettings(
                                settings.copy(
                                    gatewayIp = ipText,
                                    cloudApplicationKey = appKeyText,
                                    cloudApiKey = apiKeyText,
                                    cloudDeviceMac = macText,
                                    demoModeEnabled = false
                                )
                            )
                            onTestConnection()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save and connect", color = Color(0xFF06211E), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Ecowitt Cloud API v3 Fallback Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = "Ecowitt Cloud",
                        tint = CoolCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ecowitt cloud API v3 (remote fallback and history)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Used automatically when away from home WiFi and to backfill 24-hour and 7-day history from api.ecowitt.net.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = appKeyText,
                    onValueChange = { appKeyText = it },
                    label = { Text("Application key (from ecowitt.net user profile)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = macText,
                    onValueChange = { macText = it },
                    label = { Text("GW1200 MAC address (leave blank to auto-detect)") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onUpdateSettings(
                            settings.copy(
                                gatewayIp = ipText,
                                cloudApplicationKey = appKeyText,
                                cloudApiKey = apiKeyText,
                                cloudDeviceMac = macText
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save cloud credentials")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Per-Sensor Room Assignment & Alert Thresholds
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = "Sensors",
                        tint = ThermalAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sensor channels, 3D rooms, and thresholds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                snapshot.rooms.forEachIndexed { idx, room ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        )
                    }
                    val cfg = settings.roomConfigs[room.channelId] ?: RoomConfig(
                        channelId = room.channelId,
                        customName = room.roomName,
                        archetype = room.archetype
                    )
                    var nameDraft by remember(cfg.customName) { mutableStateOf(cfg.customName) }

                    Text(
                        text = "${room.hardwareLabel} (${room.channelId.uppercase()})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoolCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = {
                            nameDraft = it
                            onUpdateRoomConfig(room.channelId, cfg.copy(customName = it))
                        },
                        label = { Text("Room display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoomArchetype.entries.forEach { arch ->
                            FilterChip(
                                selected = cfg.archetype == arch,
                                onClick = {
                                    onUpdateRoomConfig(room.channelId, cfg.copy(archetype = arch))
                                },
                                label = { Text(arch.title, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF234168),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            Locale.UK,
                            "High humidity alert threshold: %.0f%% RH",
                            cfg.alertHumidityMaxPct
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0BEC5)
                    )
                    Slider(
                        value = cfg.alertHumidityMaxPct,
                        onValueChange = {
                            onUpdateRoomConfig(room.channelId, cfg.copy(alertHumidityMaxPct = it))
                        },
                        valueRange = 50f..85f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Display Units, 3D Gyroscope Parallax & Notification Settings
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "Preferences",
                        tint = CoolCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Units, 3D parallax, and background alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TempUnit.entries.forEach { u ->
                        FilterChip(
                            selected = settings.tempUnit == u,
                            onClick = { onUpdateSettings(settings.copy(tempUnit = u)) },
                            label = { Text(u.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PressureUnit.entries.forEach { p ->
                        FilterChip(
                            selected = settings.pressureUnit == p,
                            onClick = { onUpdateSettings(settings.copy(pressureUnit = p)) },
                            label = { Text(p.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pixel gyroscope 3D tilt parallax", color = Color.White)
                        Text(
                            "Tilts the 3D room diorama gently as you move your phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                    Switch(
                        checked = settings.gyroscopeParallaxEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(gyroscopeParallaxEnabled = it))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mould, frost, and smart ventilation notifications", color = Color.White)
                        Text(
                            "Notifies you when indoor humidity exceeds your room threshold or when outdoor air can dry a room",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(notificationsEnabled = it))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
