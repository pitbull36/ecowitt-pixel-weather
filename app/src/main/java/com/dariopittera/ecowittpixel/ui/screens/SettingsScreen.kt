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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Watch
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
    connectedWatchNodes: List<WearNodeInfo>,
    isSyncingWatch: Boolean,
    onSyncWatchNow: () -> Unit,
    onCheckOtaUpdate: () -> Unit,
    onTestNotifications: () -> Unit,
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

        // 0b. Pixel Watch Companion Sync Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15263B)),
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
                            imageVector = Icons.Outlined.Watch,
                            contentDescription = "Pixel Watch companion",
                            tint = CoolCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val watchTitle = if (connectedWatchNodes.isNotEmpty()) {
                            val names = connectedWatchNodes.joinToString(", ") { it.displayName }
                            "Pixel Watch connected ($names)"
                        } else {
                            "Pixel Watch companion"
                        }
                        Text(
                            text = watchTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Syncs API keys, room names, 24h history, and live telemetry to your Pixel Watch via Wearable Data Layer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
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
                        onSyncWatchNow()
                    },
                    enabled = !isSyncingWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = CoolCyan)
                ) {
                    Text(
                        text = if (isSyncingWatch) "Syncing..." else "Sync to watch",
                        color = Color(0xFF061624),
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

        // 4. Per-Sensor Room Assignment & Scientific Standards
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
                        text = "Sensor channels and scientific alert standards",
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
                    var nameDraft by remember(room.channelId, cfg.customName) { mutableStateOf(cfg.customName) }

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
                                    // Selecting a room preset automatically applies its scientific standard thresholds
                                    onUpdateRoomConfig(
                                        room.channelId,
                                        cfg.copy(
                                            archetype = arch,
                                            alertTempMinC = arch.scientificAlertTempMinC,
                                            alertTempMaxC = arch.scientificAlertTempMaxC,
                                            alertHumidityMaxPct = arch.scientificAlertHumidityMaxPct
                                        )
                                    )
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
                        text = "Standard: ${cfg.archetype.standardCitation}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ThermalAmber
                    )
                    Text(
                        text = cfg.archetype.standardRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.UK,
                                "Room criteria: <%.1f°C, >%.1f°C, or >%.0f%% RH",
                                cfg.alertTempMinC,
                                cfg.alertTempMaxC,
                                cfg.alertHumidityMaxPct
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    onUpdateRoomConfig(
                                        room.channelId,
                                        cfg.copy(
                                            alertTempMinC = cfg.archetype.scientificAlertTempMinC,
                                            alertTempMaxC = cfg.archetype.scientificAlertTempMaxC,
                                            alertHumidityMaxPct = cfg.archetype.scientificAlertHumidityMaxPct
                                        )
                                    )
                                },
                                label = { Text("Reset to standard", fontSize = 10.sp) }
                            )
                            Switch(
                                checked = cfg.alertsEnabled,
                                onCheckedChange = {
                                    onUpdateRoomConfig(room.channelId, cfg.copy(alertsEnabled = it))
                                }
                            )
                        }
                    }

                    Text(
                        text = String.format(Locale.UK, "Min temperature alert: %.1f°C", cfg.alertTempMinC),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = cfg.alertTempMinC,
                        onValueChange = {
                            onUpdateRoomConfig(room.channelId, cfg.copy(alertTempMinC = it))
                        },
                        valueRange = 0f..24f
                    )

                    Text(
                        text = String.format(Locale.UK, "Max temperature alert: %.1f°C", cfg.alertTempMaxC),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = cfg.alertTempMaxC,
                        onValueChange = {
                            onUpdateRoomConfig(room.channelId, cfg.copy(alertTempMaxC = it))
                        },
                        valueRange = 20f..36f
                    )

                    Text(
                        text = String.format(Locale.UK, "Max humidity alert: %.0f%% RH", cfg.alertHumidityMaxPct),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = cfg.alertHumidityMaxPct,
                        onValueChange = {
                            onUpdateRoomConfig(room.channelId, cfg.copy(alertHumidityMaxPct = it))
                        },
                        valueRange = 45f..90f
                    )
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
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun CustomNotificationCriteriaCard(
    settings: AppSettings,
    snapshot: HomeAtmosphereSnapshot,
    onUpdateSettings: (AppSettings) -> Unit,
    onTestNotifications: () -> Unit
) {
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
                            text = "App notifications on met criteria",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Fires Android alerts during live polling and background syncs when criteria are met",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = {
                        onUpdateSettings(settings.copy(notificationsEnabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onTestNotifications,
                colors = ButtonDefaults.buttonColors(containerColor = CoolCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Test active criteria now",
                    color = Color(0xFF061624),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Smart environmental criteria",
                style = MaterialTheme.typography.labelMedium,
                color = ThermalAmber,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            SmartCriterionToggleRow(
                title = "High mould and condensation risk (ASHRAE 160)",
                subtitle = "Notifies when indoor dew point approaches room temperature (<3.2°C spread or ≥70% RH)",
                checked = settings.mouldRiskNotifications,
                onCheckedChange = { onUpdateSettings(settings.copy(mouldRiskNotifications = it)) }
            )
            SmartCriterionToggleRow(
                title = "Smart window ventilation advice",
                subtitle = "Notifies when a room is humid and outdoor air carries ≥1.2 g/m³ less water vapour",
                checked = settings.ventilationAdviceNotifications,
                onCheckedChange = { onUpdateSettings(settings.copy(ventilationAdviceNotifications = it)) }
            )
            SmartCriterionToggleRow(
                title = "Rapid barometric pressure drop",
                subtitle = "Notifies when GW1200 pressure falls by ≥2.0 hPa over 3 hours (approaching rain/storm front)",
                checked = settings.pressureDropNotifications,
                onCheckedChange = { onUpdateSettings(settings.copy(pressureDropNotifications = it)) }
            )
            SmartCriterionToggleRow(
                title = "Sensor low battery alert",
                subtitle = "Notifies when any WH31 sensor channel reports low battery voltage",
                checked = settings.lowBatteryNotifications,
                onCheckedChange = { onUpdateSettings(settings.copy(lowBatteryNotifications = it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            Text(
                text = "Custom user notification rules (${settings.customCriteria.size})",
                style = MaterialTheme.typography.labelMedium,
                color = CoolCyan,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            settings.customCriteria.forEach { crit ->
                val targetRoom = snapshot.rooms.firstOrNull { it.channelId == crit.channelId }
                    ?: snapshot.rooms.firstOrNull()
                val actualVal = if (targetRoom != null) {
                    when (crit.metric) {
                        NotificationMetric.TEMPERATURE -> targetRoom.temperatureC
                        NotificationMetric.HUMIDITY -> targetRoom.humidityPct
                        NotificationMetric.DEW_POINT -> targetRoom.dewPointC
                        NotificationMetric.PRESSURE -> targetRoom.relativePressureHpa ?: snapshot.pressureHpa ?: 1013.2f
                        NotificationMetric.COMFORT_SCORE -> targetRoom.comfortScore.toFloat()
                    }
                } else null
                val isMet = actualVal != null && crit.enabled && crit.operator.matches(actualVal, crit.threshold)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMet) Color(0xFF2B1C28) else Color(0xFF142236)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${targetRoom?.roomName ?: crit.channelId.uppercase()} • ${crit.metric.label} ${crit.operator.symbol} ${
                                        String.format(Locale.UK, "%.1f%s", crit.threshold, crit.metric.unitSuffix)
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMet) Color(0xFFFF8A80) else Color.White
                                )
                                Text(
                                    text = crit.customMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                if (actualVal != null) {
                                    Text(
                                        text = String.format(
                                            Locale.UK,
                                            "Live reading: %.1f%s (%s)",
                                            actualVal,
                                            crit.metric.unitSuffix,
                                            if (isMet) "CRITERION MET" else "Within target"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMet) ThermalAmber else ComfortEmerald,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AssistChip(
                                    onClick = {
                                        val updatedList = settings.customCriteria.filterNot { it.id == crit.id }
                                        onUpdateSettings(settings.copy(customCriteria = updatedList))
                                    },
                                    label = { Text("Remove", fontSize = 10.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = crit.enabled,
                                    onCheckedChange = { enabled ->
                                        val updatedList = settings.customCriteria.map {
                                            if (it.id == crit.id) it.copy(enabled = enabled) else it
                                        }
                                        onUpdateSettings(settings.copy(customCriteria = updatedList))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            Text(
                text = "Add new notification criterion",
                style = MaterialTheme.typography.labelMedium,
                color = ThermalAmber,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Select Room
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                snapshot.rooms.forEach { r ->
                    FilterChip(
                        selected = draftChannelId == r.channelId,
                        onClick = { draftChannelId = r.channelId },
                        label = { Text(r.roomName.substringBefore(" ("), fontSize = 11.sp) }
                    )
                }
            }

            // 2. Select Metric
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
                                NotificationMetric.HUMIDITY -> 65.0f
                                NotificationMetric.DEW_POINT -> 16.0f
                                NotificationMetric.PRESSURE -> 1002.0f
                                NotificationMetric.COMFORT_SCORE -> 65.0f
                            }
                        },
                        label = { Text(m.label, fontSize = 11.sp) }
                    )
                }
            }

            // 3. Select Comparison Operator (< or >)
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

            Text(
                text = String.format(
                    Locale.UK,
                    "Criterion threshold: %s %.1f%s",
                    draftOperator.symbol,
                    draftThreshold.coerceIn(valueRange.start, valueRange.endInclusive),
                    draftMetric.unitSuffix
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Slider(
                value = draftThreshold.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { draftThreshold = it },
                valueRange = valueRange
            )

            OutlinedTextField(
                value = draftMessage,
                onValueChange = { draftMessage = it },
                label = { Text("Custom notification message") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                },
                colors = ButtonDefaults.buttonColors(containerColor = ComfortEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Add notification criterion",
                    color = Color(0xFF06211E),
                    fontWeight = FontWeight.Bold
                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
