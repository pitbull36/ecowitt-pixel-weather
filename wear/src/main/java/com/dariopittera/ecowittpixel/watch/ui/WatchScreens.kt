package com.dariopittera.ecowittpixel.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.BuildConfig
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.RoomAlertBreach
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.watch.DetailCrownMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WatchRootScreen(
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings,
    breaches: List<RoomAlertBreach>,
    carouselIndex: Int,
    detailChannelId: String?,
    detailCrownMode: DetailCrownMode,
    historyScrubFraction: Float,
    crownYawOffset: Float,
    crownPitchOffset: Float,
    isAmbient: Boolean,
    ambientMinuteTick: Int,
    statusText: String,
    otaLabel: String,
    isCheckingOta: Boolean,
    onStepCarouselByCrown: (Float) -> Unit,
    onSelectCarouselPage: (Int) -> Unit,
    onOpenRoomDetail: (String) -> Unit,
    onCloseRoomDetail: () -> Unit,
    onToggleDetailCrownMode: () -> Unit,
    onResetDetailView: () -> Unit,
    onDetailCrownScroll: (Float) -> Unit,
    onSyncNow: () -> Unit,
    onCheckWatchOta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDetailRoom = detailChannelId?.let { chId ->
        snapshot.rooms.firstOrNull { it.channelId == chId }
    }

    if (activeDetailRoom != null) {
        val roomBreaches = breaches.filter { it.channelId == activeDetailRoom.channelId }
        WatchRoomDetailScreen(
            room = activeDetailRoom,
            settings = settings,
            breaches = roomBreaches,
            crownMode = detailCrownMode,
            scrubFraction = historyScrubFraction,
            crownYawOffset = crownYawOffset,
            crownPitchOffset = crownPitchOffset,
            isAmbient = isAmbient,
            ambientMinuteTick = ambientMinuteTick,
            onBack = onCloseRoomDetail,
            onToggleCrownMode = onToggleDetailCrownMode,
            onResetView = onResetDetailView,
            onCrownScroll = onDetailCrownScroll,
            modifier = modifier
        )
    } else {
        WatchMainCarouselScreen(
            snapshot = snapshot,
            settings = settings,
            breaches = breaches,
            carouselIndex = carouselIndex,
            isAmbient = isAmbient,
            ambientMinuteTick = ambientMinuteTick,
            statusText = statusText,
            otaLabel = otaLabel,
            isCheckingOta = isCheckingOta,
            onStepCarouselByCrown = onStepCarouselByCrown,
            onSelectCarouselPage = onSelectCarouselPage,
            onOpenRoomDetail = onOpenRoomDetail,
            onSyncNow = onSyncNow,
            onCheckWatchOta = onCheckWatchOta,
            modifier = modifier
        )
    }
}

/**
 * Level 1: Main Pixel Watch carousel where rotating the crown cycles between rooms
 * (Indoor Gateway, Channel 1, Channel 2) and a final Sync & OTA card.
 */
@Composable
private fun WatchMainCarouselScreen(
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings,
    breaches: List<RoomAlertBreach>,
    carouselIndex: Int,
    isAmbient: Boolean,
    ambientMinuteTick: Int,
    statusText: String,
    otaLabel: String,
    isCheckingOta: Boolean,
    onStepCarouselByCrown: (Float) -> Unit,
    onSelectCarouselPage: (Int) -> Unit,
    onOpenRoomDetail: (String) -> Unit,
    onSyncNow: () -> Unit,
    onCheckWatchOta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val rooms = snapshot.rooms
    val totalPages = rooms.size + 1
    val safeIndex = carouselIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchOledBlack)
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                if (abs(rotaryAccumulator) >= 48f) {
                    val step = rotaryAccumulator
                    rotaryAccumulator = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onStepCarouselByCrown(step)
                }
                true
            }
            .pointerInput(totalPages, safeIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        if (dragAccumulator <= -38f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectCarouselPage(safeIndex + 1)
                        } else if (dragAccumulator >= 38f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectCarouselPage(safeIndex - 1)
                        }
                        dragAccumulator = 0f
                    }
                )
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        if (safeIndex < rooms.size) {
            val room = rooms[safeIndex]
            val roomBreaches = breaches.filter { it.channelId == room.channelId }
            val tempColor = watchTemperatureColor(room.temperatureC)
            val humColor = watchHumidityColor(room.humidityPct)

            // Full-bleed circular 3D diorama + top temp bezel arc + bottom humidity bezel arc
            WatchCircularDioramaCanvas(
                room = room,
                breaches = roomBreaches,
                gyroEnabled = settings.gyroscopeParallaxEnabled,
                isAmbient = isAmbient,
                ambientMinuteTick = ambientMinuteTick,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !isAmbient) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenRoomDetail(room.channelId)
                    }
            )

            // Top overlay under the upper temperature bezel arc
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 26.dp, end = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator dots
                if (!isAmbient) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until totalPages) {
                            val selected = i == safeIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) tempColor else Color.White.copy(alpha = 0.28f)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = room.roomName.substringBefore(" ("),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = settings.tempUnit.format(room.temperatureC),
                    color = tempColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Bottom overlay above the lower humidity bezel arc
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val leadBreach = roomBreaches.firstOrNull()
                if (leadBreach != null && !isAmbient) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background( Color(0xCC3B1018))
                            .border(1.dp, WatchThermalCoral, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Alert: ${leadBreach.shortBadge}",
                            color = Color(0xFFFFCDD2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${room.humidityPct.roundToInt()}% RH",
                        color = humColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isAmbient) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•  DP ${settings.tempUnit.format(room.dewPointC, decimals = 0)}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (!isAmbient) {
                    Text(
                        text = "Tap room for 24h scrub",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
                    )
                }
            }
        } else {
            // Final carousel page: Watch Sync & GitHub OTA Update card
            WatchSyncAndOtaPage(
                snapshot = snapshot,
                statusText = statusText,
                otaLabel = otaLabel,
                isCheckingOta = isCheckingOta,
                onSyncNow = onSyncNow,
                onCheckWatchOta = onCheckWatchOta,
                onBackToFirstRoom = { onSelectCarouselPage(0) }
            )
        }
    }
}

/**
 * Level 2: Tapped Room Detail Screen.
 * - Default crown mode: scrubs 24h history (updating the top temperature arc, bottom humidity arc,
 *   and 3D room state through time).
 * - Tap toggle: switches crown to 3D room tilt mode so rotating the crown orbits the 3D diorama.
 */
@Composable
private fun WatchRoomDetailScreen(
    room: SensorRoomState,
    settings: AppSettings,
    breaches: List<RoomAlertBreach>,
    crownMode: DetailCrownMode,
    scrubFraction: Float,
    crownYawOffset: Float,
    crownPitchOffset: Float,
    isAmbient: Boolean,
    ambientMinuteTick: Int,
    onBack: () -> Unit,
    onToggleCrownMode: () -> Unit,
    onResetView: () -> Unit,
    onCrownScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(room.channelId, crownMode) {
        runCatching { focusRequester.requestFocus() }
    }

    val history24h = remember(room.history, room.lastUpdatedMs) {
        val cutoff = System.currentTimeMillis() - 24L * 3600L * 1000L
        room.history.filter { it.timestampMs >= cutoff }.ifEmpty { room.history }
    }

    val isLiveNow = scrubFraction >= 0.97f || history24h.isEmpty()
    val scrubbedPoint = if (!isLiveNow && history24h.isNotEmpty()) {
        val idx = ((history24h.size - 1) * scrubFraction).roundToInt().coerceIn(0, history24h.lastIndex)
        history24h[idx]
    } else null

    val displayedRoom = if (scrubbedPoint != null) {
        room.copy(
            temperatureC = scrubbedPoint.temperatureC,
            humidityPct = scrubbedPoint.humidityPct,
            relativePressureHpa = scrubbedPoint.pressureHpa ?: room.relativePressureHpa
        )
    } else {
        room
    }

    val tempColor = watchTemperatureColor(displayedRoom.temperatureC)
    val humColor = watchHumidityColor(displayedRoom.humidityPct)
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.UK) }
    val timeBadgeText = if (scrubbedPoint != null) {
        val hoursAgo = ((System.currentTimeMillis() - scrubbedPoint.timestampMs) / 3600_000f)
        String.format(Locale.UK, "-%.1fh (%s)", hoursAgo, timeFmt.format(Date(scrubbedPoint.timestampMs)))
    } else {
        "Live now • Comfort ${displayedRoom.comfortScore}"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchOledBlack)
            .onRotaryScrollEvent { event ->
                onCrownScroll(event.verticalScrollPixels)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Circular 3D room diorama + top temperature arc + bottom humidity arc reflecting scrubbed timestamp
        WatchCircularDioramaCanvas(
            room = displayedRoom,
            breaches = breaches,
            gyroEnabled = settings.gyroscopeParallaxEnabled,
            isAmbient = isAmbient,
            crownYawOffset = crownYawOffset,
            crownPitchOffset = crownPitchOffset,
            ambientMinuteTick = ambientMinuteTick,
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = !isAmbient) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleCrownMode()
                }
        )

        // Top HUD below the upper temperature bezel arc
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 26.dp, end = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isAmbient) {
                    Text(
                        text = "‹ Back",
                        color = WatchCoolCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F1E32))
                            .clickable { onBack() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = displayedRoom.roomName.substringBefore(" ("),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${settings.tempUnit.format(displayedRoom.temperatureC)}  •  ${displayedRoom.humidityPct.roundToInt()}%",
                color = tempColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = timeBadgeText,
                color = if (isLiveNow) WatchComfortEmerald else WatchThermalAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom HUD above the lower humidity bezel arc: 24h mini sparkline + Crown Mode toggle pill
        if (!isAmbient) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (history24h.size >= 2) {
                    WatchMini24hSparkline(
                        points = history24h,
                        scrubFraction = scrubFraction,
                        tempColor = tempColor,
                        humColor = humColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScrub = crownMode == DetailCrownMode.SCRUB_24H
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isScrub) Color(0xFF1A3656) else Color(0xFF3A2814))
                            .border(
                                width = 1.dp,
                                color = if (isScrub) WatchCoolCyan else WatchThermalAmber,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleCrownMode()
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isScrub) "Crown: 24h scrub (tap for 3D tilt)" else "Crown: 3D tilt (tap for 24h scrub)",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!isLiveNow || abs(crownYawOffset) > 0.02f) {
                        Text(
                            text = "Reset",
                            color = WatchCoolCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0F1E32))
                                .clickable { onResetView() }
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchMini24hSparkline(
    points: List<com.dariopittera.ecowittpixel.model.HistoricalPoint>,
    scrubFraction: Float,
    tempColor: Color,
    humColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minT = (points.minOf { it.temperatureC } - 0.5f)
        val maxT = (points.maxOf { it.temperatureC } + 0.5f).coerceAtLeast(minT + 1f)

        val tempPath = Path()
        val humPath = Path()

        points.forEachIndexed { i, pt ->
            val x = (i.toFloat() / (points.size - 1).toFloat()) * size.width
            val yTemp = size.height - ((pt.temperatureC - minT) / (maxT - minT)).coerceIn(0f, 1f) * size.height
            val yHum = size.height - (pt.humidityPct / 100f).coerceIn(0f, 1f) * size.height
            if (i == 0) {
                tempPath.moveTo(x, yTemp)
                humPath.moveTo(x, yHum)
            } else {
                tempPath.lineTo(x, yTemp)
                humPath.lineTo(x, yHum)
            }
        }

        drawPath(humPath, color = humColor.copy(alpha = 0.55f), style = Stroke(width = 1.8f))
        drawPath(tempPath, color = tempColor, style = Stroke(width = 2.4f))

        val scrubX = (scrubFraction.coerceIn(0f, 1f)) * size.width
        val idx = ((points.size - 1) * scrubFraction).roundToInt().coerceIn(0, points.lastIndex)
        val activePt = points[idx]
        val activeY = size.height - ((activePt.temperatureC - minT) / (maxT - minT)).coerceIn(0f, 1f) * size.height

        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(scrubX, 0f),
            end = Offset(scrubX, size.height),
            strokeWidth = 1.4f
        )
        drawCircle(
            color = tempColor,
            radius = 4.2f,
            center = Offset(scrubX, activeY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.0f,
            center = Offset(scrubX, activeY)
        )
    }
}

@Composable
private fun WatchSyncAndOtaPage(
    snapshot: HomeAtmosphereSnapshot,
    statusText: String,
    otaLabel: String,
    isCheckingOta: Boolean,
    onSyncNow: () -> Unit,
    onCheckWatchOta: () -> Unit,
    onBackToFirstRoom: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchOledBlack)
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Pixel Watch sync",
                color = WatchCoolCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = "GW1200 (${snapshot.rooms.size} rooms) • v${BuildConfig.VERSION_NAME}",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WatchComfortEmerald)
                    .clickable { onSyncNow() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sync telemetry now",
                    color = Color(0xFF04221F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF172B44))
                    .border(1.dp, WatchCoolCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !isCheckingOta) { onCheckWatchOta() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = otaLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "‹ Back to rooms",
                color = WatchCoolCyan,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onBackToFirstRoom() }
            )
        }
    }
}
