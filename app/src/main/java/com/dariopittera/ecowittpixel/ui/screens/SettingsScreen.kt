package com.dariopittera.ecowittpixel.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.BuildConfig
import com.dariopittera.ecowittpixel.data.WearNodeInfo
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ComparisonOperator
import com.dariopittera.ecowittpixel.model.CustomAlertCriterion
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.NotificationMetric
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
    otaButtonLabel: String,
    isCheckingOta: Boolean,
    onCheckOtaUpdate: () -> Unit,
    onTestNotifications: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateRoomConfig: (String, RoomConfig, Boolean) -> Unit,
    onScanSubnet: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ipText by remember(settings.gatewayIp) { mutableStateOf(settings.gatewayIp) }
    var appKeyText by remember(settings.cloudApplicationKey) { mutableStateOf(settings.cloudApplicationKey) }
    var apiKeyText by remember(settings.cloudApiKey) { mutableStateOf(settings.cloudApiKey) }
    var macText by remember(settings.cloudDeviceMac) { mutableStateOf(settings.cloudDeviceMac) }
    var locationText by remember(settings.weatherLocationQuery) { mutableStateOf(settings.weatherLocationQuery) }
    var showAppKey by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

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
            text = "Configure your Ecowitt GW1200 connection, scientific room standards, and over-the-air updates.",
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

        Spacer(modifier = Modifier.height(12.dp))

        // 0. Over-The-Air (OTA) App Update Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132A2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = "OTA Update",
                            tint = ComfortEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "App version v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Checks GitHub releases and installs any newer build automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onCheckOtaUpdate,
                    enabled = !isCheckingOta,
                    colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald)
                ) {
                    Text(
                        text = otaButtonLabel,
                        color = Color(0xFF06211E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                            text = "Turn off when you are ready to poll your real GW1200 over Ecowitt cloud or local WiFi.",
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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Ecowitt Cloud API v3 Card
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
                        text = "Ecowitt cloud API v3 (live and 7-day history)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter your Application key and API key from ecowitt.net -> User Center.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = appKeyText,
                    onValueChange = { appKeyText = it },
                    label = { Text("Application key (from ecowitt.net user profile)") },
                    singleLine = true,
                    visualTransformation = if (showAppKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showAppKey = !showAppKey }) {
                            Icon(
                                imageVector = if (showAppKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showAppKey) {
                                    "Hide application key"
                                } else {
                                    "Show application key"
                                },
                                tint = Color(0xFFB0BEC5)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showApiKey) {
                                    "Hide API key"
                                } else {
                                    "Show API key"
                                },
                                tint = Color(0xFFB0BEC5)
                            )
                        }
                    },
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
                                cloudDeviceMac = macText,
                                demoModeEnabled = false
                            )
                        )
                        onTestConnection()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Save cloud credentials and connect",
                        color = Color(0xFF06211E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. GW1200 Local LAN HTTP API Card
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
                        text = "GW1200 local WiFi HTTP API (optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Queries http://<ip>/get_livedata_info directly on your home LAN when on the same WiFi.",
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save LAN IP")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3b. Local Outdoor Weather Reference Card (Open-Meteo + Physical Outdoor Sensor)
        val effectiveOutdoor = snapshot.effectiveOutdoorWeather
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = "Local weather",
                        tint = ComfortEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Local outdoor weather context",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Checks outdoor temperature, humidity, rain, wind, and pressure so indoor room and window advice is always sensible.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }

                if (effectiveOutdoor != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF102136))
                            .border(
                                width = 1.dp,
                                color = ComfortEmerald.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Active outdoor reference: ${effectiveOutdoor.locationName} (${effectiveOutdoor.conditionLabel})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ComfortEmerald
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = String.format(
                                    Locale.UK,
                                    "Temp %s (feels %s) • %d%% RH (%.1f g/m³, DP %s) • %s • wind %.0f km/h",
                                    settings.tempUnit.format(effectiveOutdoor.temperatureC),
                                    settings.tempUnit.format(effectiveOutdoor.feelsLikeC),
                                    effectiveOutdoor.humidityPct.toInt(),
                                    effectiveOutdoor.absoluteHumidityGm3,
                                    settings.tempUnit.format(effectiveOutdoor.dewPointC),
                                    settings.pressureUnit.format(effectiveOutdoor.pressureHpa),
                                    effectiveOutdoor.windSpeedKmh
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    label = { Text("City or town (leave blank for automatic location)") },
                    placeholder = { Text("e.g. London, Milan, Zurich") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            locationText = ""
                            onUpdateSettings(settings.copy(weatherLocationQuery = ""))
                            onTestConnection()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Auto-detect area")
                    }
                    Button(
                        onClick = {
                            onUpdateSettings(settings.copy(weatherLocationQuery = locationText.trim()))
                            onTestConnection()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save weather city")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Per-Sensor Room Assignment & Scientific Standards (Tabbed by room for clean layout)
        var selectedRoomIdx by remember { mutableStateOf(0) }
        val activeRoomIdx = selectedRoomIdx.coerceIn(0, (snapshot.rooms.size - 1).coerceAtLeast(0))
        val selectedRoom = snapshot.rooms.getOrNull(activeRoomIdx)

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
                    Column {
                        Text(
                            text = "Rooms and 3D scene presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Select a room below to rename it and choose its 3D scene preset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Room selector pill tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    snapshot.rooms.forEachIndexed { idx, r ->
                        val isSelected = idx == activeRoomIdx
                        val roomCfg = settings.roomConfigs[r.channelId]
                        val tabTitle = (roomCfg?.customName ?: r.roomName).substringBefore(" (")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) CoolCyan else Color(0xFF142236)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CoolCyan else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedRoomIdx = idx }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "$tabTitle (${r.channelId.uppercase()})",
                                color = if (isSelected) Color(0xFF061624) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (selectedRoom != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val savedCfg = settings.roomConfigs[selectedRoom.channelId] ?: RoomConfig(
                        channelId = selectedRoom.channelId,
                        customName = selectedRoom.roomName,
                        archetype = selectedRoom.archetype
                    )
                    var draftCfg by remember(selectedRoom.channelId, savedCfg) {
                        mutableStateOf(savedCfg)
                    }
                    var justSaved by remember(selectedRoom.channelId) {
                        mutableStateOf(false)
                    }
                    val hasUnsavedChanges = draftCfg != savedCfg

                    Text(
                        text = "Hardware channel: ${selectedRoom.hardwareLabel} (${selectedRoom.channelId.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoolCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = draftCfg.customName,
                        onValueChange = {
                            draftCfg = draftCfg.copy(customName = it)
                            justSaved = false
                        },
                        label = { Text("Room display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "3D room archetype and scientific preset",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.SemiBold
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
                                selected = draftCfg.archetype == arch,
                                onClick = {
                                    draftCfg = draftCfg.copy(
                                        archetype = arch,
                                        alertTempMinC = arch.scientificAlertTempMinC,
                                        alertTempMaxC = arch.scientificAlertTempMaxC,
                                        alertHumidityMaxPct = arch.scientificAlertHumidityMaxPct
                                    )
                                    justSaved = false
                                },
                                label = { Text(arch.title, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF234168),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = draftCfg.archetype.laymanRule,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CoolCyan
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = draftCfg.archetype.standardRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onUpdateRoomConfig(selectedRoom.channelId, draftCfg, true)
                            justSaved = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasUnsavedChanges) ComfortEmerald else CoolCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                hasUnsavedChanges -> "Save room settings"
                                justSaved -> "Saved room settings ✓"
                                else -> "Save room settings"
                            },
                            color = Color(0xFF06211E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Custom Notification Criteria Builder & Smart Triggers Card
        CustomNotificationCriteriaCard(
            settings = settings,
            snapshot = snapshot,
            onUpdateSettings = onUpdateSettings,
            onTestNotifications = onTestNotifications
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Display Units & 3D Gyroscope Parallax Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Display units and 3D parallax",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
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
                        Text("Gyroscope 3D tilt parallax", color = Color.White)
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
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ThresholdSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CoolCyan
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun CustomNotificationCriteriaCard(
    settings: AppSettings,
    snapshot: HomeAtmosphereSnapshot,
    onUpdateSettings: (AppSettings) -> Unit,
    onTestNotifications: () -> Unit
) {
    var showAddRuleForm by remember { mutableStateOf(false) }
    var draftChannelId by remember {
        mutableStateOf(snapshot.rooms.firstOrNull()?.channelId ?: "gateway")
    }
    var draftMetric by remember { mutableStateOf(NotificationMetric.TEMPERATURE) }
    var draftOperator by remember { mutableStateOf(ComparisonOperator.BELOW) }
    var draftThreshold by remember { mutableStateOf(19.0f) }
    var draftMessage by remember { mutableStateOf("Custom room alert triggered") }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PixelSurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header + Master Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = CoolCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Notifications and custom rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Alerts on your phone and Wear OS watch (60 min cooldown per rule).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = {
                        onUpdateSettings(settings.copy(notificationsEnabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onTestNotifications,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Send test notification now",
                    color = CoolCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Smart environmental triggers in clean mini-cards
            Text(
                text = "Smart environmental triggers",
                style = MaterialTheme.typography.labelMedium,
                color = ThermalAmber,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartCriterionToggleRow(
                    title = "Mould and condensation risk",
                    subtitle = "ASHRAE 160: dew point within 2.2°C of room temp, ≥76% RH, or ≥70% RH sustained 6h+",
                    checked = settings.mouldRiskNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(mouldRiskNotifications = it)) }
                )
                SmartCriterionToggleRow(
                    title = "Window ventilation advice",
                    subtitle = "Alerts when outdoor air is drier than a humid indoor room",
                    checked = settings.ventilationAdviceNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(ventilationAdviceNotifications = it)) }
                )
                SmartCriterionToggleRow(
                    title = "Barometric storm drop",
                    subtitle = "Alerts when pressure falls by ≥2.0 hPa over 3 hours",
                    checked = settings.pressureDropNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(pressureDropNotifications = it)) }
                )
                SmartCriterionToggleRow(
                    title = "Low sensor battery",
                    subtitle = "Alerts when any WH31 sensor channel reports low voltage",
                    checked = settings.lowBatteryNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(lowBatteryNotifications = it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Custom user rules header + Add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom alert rules (${settings.customCriteria.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoolCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Your own conditions on temperature, humidity, dew point, pressure, or comfort.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = { showAddRuleForm = !showAddRuleForm },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "New rule",
                            modifier = Modifier.size(16.dp),
                            tint = ComfortEmerald
                        )
                    },
                    label = {
                        Text(
                            text = if (showAddRuleForm) "Close" else "New rule",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Collapsible Add Rule Builder
            if (showAddRuleForm) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102033)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Create custom alert rule",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "1. Room",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolCyan
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            snapshot.rooms.forEach { r ->
                                val shortName = (settings.roomConfigs[r.channelId]?.customName ?: r.roomName)
                                    .substringBefore(" (")
                                FilterChip(
                                    selected = draftChannelId == r.channelId,
                                    onClick = { draftChannelId = r.channelId },
                                    label = { Text(shortName, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "2. Metric and condition",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolCyan
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NotificationMetric.entries.forEach { m ->
                                FilterChip(
                                    selected = draftMetric == m,
                                    onClick = {
                                        draftMetric = m
                                        draftThreshold = when (m) {
                                            NotificationMetric.TEMPERATURE -> 19.0f
                                            NotificationMetric.HUMIDITY -> 68.0f
                                            NotificationMetric.DEW_POINT -> 16.0f
                                            NotificationMetric.PRESSURE -> 1002.0f
                                            NotificationMetric.COMFORT_SCORE -> 65.0f
                                        }
                                    },
                                    label = { Text(m.label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComparisonOperator.entries.forEach { op ->
                                FilterChip(
                                    selected = draftOperator == op,
                                    onClick = { draftOperator = op },
                                    label = { Text("${op.label} (${op.symbol})", fontSize = 11.sp) }
                                )
                            }
                        }

                        val valueRange = when (draftMetric) {
                            NotificationMetric.TEMPERATURE -> -5f..38f
                            NotificationMetric.HUMIDITY -> 20f..95f
                            NotificationMetric.DEW_POINT -> 0f..26f
                            NotificationMetric.PRESSURE -> 980f..1040f
                            NotificationMetric.COMFORT_SCORE -> 20f..95f
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        ThresholdSliderRow(
                            label = "3. Threshold (${draftMetric.label} ${draftOperator.symbol})",
                            valueText = String.format(
                                Locale.UK,
                                "%s %.1f%s",
                                draftOperator.symbol,
                                draftThreshold.coerceIn(valueRange.start, valueRange.endInclusive),
                                draftMetric.unitSuffix
                            ),
                            value = draftThreshold.coerceIn(valueRange.start, valueRange.endInclusive),
                            valueRange = valueRange,
                            onValueChange = { draftThreshold = it }
                        )

                        OutlinedTextField(
                            value = draftMessage,
                            onValueChange = { draftMessage = it },
                            label = { Text("4. Notification message") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val newRule = CustomAlertCriterion(
                                    id = "crit_${System.currentTimeMillis()}",
                                    channelId = draftChannelId,
                                    metric = draftMetric,
                                    operator = draftOperator,
                                    threshold = draftThreshold.coerceIn(valueRange.start, valueRange.endInclusive),
                                    customMessage = draftMessage.ifBlank { "Custom alert criterion met" },
                                    enabled = true
                                )
                                onUpdateSettings(
                                    settings.copy(customCriteria = settings.customCriteria + newRule)
                                )
                                showAddRuleForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Save custom rule",
                                color = Color(0xFF06211E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active custom rule cards (clean two-row layout)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.customCriteria.forEach { crit ->
                    val targetRoom = snapshot.rooms.firstOrNull { it.channelId == crit.channelId }
                        ?: snapshot.rooms.firstOrNull()
                    val roomLabel = (settings.roomConfigs[crit.channelId]?.customName
                        ?: targetRoom?.roomName
                        ?: crit.channelId.uppercase()).substringBefore(" (")
                    val actualVal = if (targetRoom != null) {
                        when (crit.metric) {
                            NotificationMetric.TEMPERATURE -> targetRoom.temperatureC
                            NotificationMetric.HUMIDITY -> targetRoom.humidityPct
                            NotificationMetric.DEW_POINT -> targetRoom.dewPointC
                            NotificationMetric.PRESSURE -> targetRoom.relativePressureHpa ?: snapshot.pressureHpa ?: 1013.2f
                            NotificationMetric.COMFORT_SCORE -> targetRoom.comfortScore.toFloat()
                        }
                    } else null
                    val isThresholdCrossed = actualVal != null && crit.enabled && crit.operator.matches(actualVal, crit.threshold)
                    val isSustainedMet = if (isThresholdCrossed && targetRoom != null &&
                        crit.metric == NotificationMetric.HUMIDITY && crit.operator == ComparisonOperator.ABOVE
                    ) {
                        com.dariopittera.ecowittpixel.model.Psychrometrics.isHumiditySustained(
                            targetRoom,
                            crit.threshold,
                            minSustainedHours = 4.0f
                        )
                    } else {
                        isThresholdCrossed
                    }
                    val isMet = isThresholdCrossed && isSustainedMet

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMet) Color(0xFF2B1C28) else Color(0xFF142236)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top row: Room + Rule expression on left, Switch + Delete icon on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = roomLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoolCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.UK,
                                            "%s %s %.1f%s",
                                            crit.metric.label,
                                            crit.operator.symbol,
                                            crit.threshold,
                                            crit.metric.unitSuffix
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMet) Color(0xFFFF8A80) else Color.White
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = crit.enabled,
                                        onCheckedChange = { enabled ->
                                            val updatedList = settings.customCriteria.map {
                                                if (it.id == crit.id) it.copy(enabled = enabled) else it
                                            }
                                            onUpdateSettings(settings.copy(customCriteria = updatedList))
                                        }
                                    )
                                    IconButton(
                                        onClick = {
                                            val updatedList = settings.customCriteria.filterNot { it.id == crit.id }
                                            onUpdateSettings(settings.copy(customCriteria = updatedList))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete rule",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom row: Message + live status badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = crit.customMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (actualVal != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val stateLabel = when {
                                        isMet -> "Triggered"
                                        isThresholdCrossed -> "Sustaining (<4h)"
                                        else -> "OK"
                                    }
                                    Text(
                                        text = String.format(
                                            Locale.UK,
                                            "Now %.1f%s • %s",
                                            actualVal,
                                            crit.metric.unitSuffix,
                                            stateLabel
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMet || isThresholdCrossed) ThermalAmber else ComfortEmerald,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartCriterionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF142236)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
