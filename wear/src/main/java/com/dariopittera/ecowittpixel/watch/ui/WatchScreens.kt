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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatWatchRoomName(rawName: String): String {
    return rawName
        .replace(" (GW1200)", "", ignoreCase = true)
        .replace(Regex("""\s*\(Ch\s*\d+\)""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { rawName }
}

@Composable
fun WatchRootScreen(
    snapshot: HomeAtmosphereSnapshot,
    settings: AppSettings,
    breaches: List<RoomAlertBreach>,
    carouselIndex: Int,
    detailChannelId: String?,
    historyScrubFraction: Float,
    detailIsWeekRange: Boolean,
    isAmbient: Boolean,
    ambientMinuteTick: Int,
    statusText: String,
    otaLabel: String,
    isCheckingOta: Boolean,
    onStepCarouselByCrown: (Float) -> Unit,
    onSelectCarouselPage: (Int) -> Unit,
    onOpenRoomDetail: (String) -> Unit,
    onCloseRoomDetail: () -> Unit,
    onResetDetailView: () -> Unit,
    onToggleDetailHistoryRange: () -> Unit,
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
            scrubFraction = historyScrubFraction,
            isWeekRange = detailIsWeekRange,
            isAmbient = isAmbient,
            ambientMinuteTick = ambientMinuteTick,
            onBack = onCloseRoomDetail,
            onResetView = onResetDetailView,
            onToggleHistoryRange = onToggleDetailHistoryRange,
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
 * Level 1: Main Wear OS carousel where rotating the crown cycles between rooms
 * and a final Sync and OTA card.
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
                    text = formatWatchRoomName(room.roomName),
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
                            .background(Color(0xCC3B1018))
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
 * Rotating the crown scrubs either the 24h or 1w (7-day) history timeline (updating the top
 * temperature arc, bottom humidity arc, and 3D room state through time).
 */
@Composable
private fun WatchRoomDetailScreen(
    room: SensorRoomState,
    settings: AppSettings,
    breaches: List<RoomAlertBreach>,
    scrubFraction: Float,
    isWeekRange: Boolean,
    isAmbient: Boolean,
    ambientMinuteTick: Int,
    onBack: () -> Unit,
    onResetView: () -> Unit,
    onToggleHistoryRange: () -> Unit,
    onCrownScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(room.channelId) {
        runCatching { focusRequester.requestFocus() }
    }

    val windowDurationMs = if (isWeekRange) {
        7L * 24L * 3600L * 1000L
    } else {
        24L * 3600L * 1000L
    }
    val nowAnchorMs = remember(room.lastUpdatedMs, room.history.size, isWeekRange) {
        maxOf(System.currentTimeMillis(), room.lastUpdatedMs)
    }
    val windowStartMs = nowAnchorMs - windowDurationMs

    val historyWindow = remember(room.history, windowStartMs, nowAnchorMs) {
        val filtered = room.history
            .filter { it.timestampMs in windowStartMs..nowAnchorMs }
            .sortedBy { it.timestampMs }
            .toMutableList()
        // Always include the live current point at the right edge of the window
        if (filtered.isEmpty() || (nowAnchorMs - filtered.last().timestampMs) > 90_000L) {
            filtered.add(
                com.dariopittera.ecowittpixel.model.HistoricalPoint(
                    timestampMs = nowAnchorMs,
                    temperatureC = room.temperatureC,
                    humidityPct = room.humidityPct,
                    pressureHpa = room.relativePressureHpa
                )
            )
        }
        filtered
    }

    val isLiveNow = scrubFraction >= 0.985f || historyWindow.isEmpty()
    // Anchor the scrub timeline strictly to the full 24h (or 7d) clock span [now - 24h .. now]
    val targetScrubTs = windowStartMs + (scrubFraction.coerceIn(0f, 1f) * windowDurationMs).toLong()

    val scrubbedPoint = if (!isLiveNow && historyWindow.isNotEmpty()) {
        historyWindow.minByOrNull { abs(it.timestampMs - targetScrubTs) }
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
    val dayTimeFmt = remember { SimpleDateFormat("EEE HH:mm", Locale.UK) }
    val timeBadgeText = if (!isLiveNow && scrubbedPoint != null) {
        val targetHoursAgo = ((nowAnchorMs - targetScrubTs) / 3600_000f).coerceAtLeast(0.1f)
        val pointHoursAgo = ((nowAnchorMs - scrubbedPoint.timestampMs) / 3600_000f).coerceAtLeast(0.1f)
        // If there is a recording gap > 45 min, show the scrubbed target time while displaying the nearest recorded sample
        val displayTs = if (abs(scrubbedPoint.timestampMs - targetScrubTs) <= 45 * 60_000L) {
            scrubbedPoint.timestampMs
        } else {
            targetScrubTs
        }
        val hoursAgo = if (displayTs == scrubbedPoint.timestampMs) pointHoursAgo else targetHoursAgo
        val hoursStr = if (hoursAgo < 10f) {
            String.format(Locale.UK, "-%.1fh", hoursAgo)
        } else {
            String.format(Locale.UK, "-%dh", hoursAgo.roundToInt())
        }
        val clockStr = if (isWeekRange && hoursAgo >= 24f) {
            dayTimeFmt.format(Date(displayTs))
        } else {
            timeFmt.format(Date(displayTs))
        }
        "$clockStr ($hoursStr)"
    } else {
        val maxHumInWindow = historyWindow.maxOfOrNull { it.humidityPct }?.roundToInt()
        val rangeLabel = if (isWeekRange) "1w" else "24h"
        if (maxHumInWindow != null) {
            "Live • $rangeLabel peak ${maxHumInWindow}% RH"
        } else {
            "Live now • $rangeLabel scrub"
        }
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
            ambientMinuteTick = ambientMinuteTick,
            modifier = Modifier.fillMaxSize()
        )

        // Top HUD below the upper temperature bezel arc
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                    text = formatWatchRoomName(displayedRoom.roomName),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isAmbient) {
                    Text(
                        text = if (isWeekRange) "1w ▾" else "24h ▾",
                        color = if (isWeekRange) WatchThermalAmber else WatchCoolCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F1E32))
                            .border(
                                1.dp,
                                if (isWeekRange) WatchThermalAmber.copy(alpha = 0.65f) else WatchCoolCyan.copy(alpha = 0.65f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleHistoryRange()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "${settings.tempUnit.format(displayedRoom.temperatureC)}  •  ${displayedRoom.humidityPct.roundToInt()}%",
                color = tempColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = timeBadgeText,
                color = if (isLiveNow) WatchCoolCyan else WatchThermalAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // Bottom HUD above the lower humidity bezel arc: 24h / 1w mini sparkline
        if (!isAmbient) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (historyWindow.size >= 2) {
                    WatchMini24hSparkline(
                        points = historyWindow,
                        windowStartMs = windowStartMs,
                        windowDurationMs = windowDurationMs,
                        scrubFraction = scrubFraction,
                        tempColor = tempColor,
                        humColor = humColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    )
                }

                if (!isLiveNow) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Back to live",
                        color = WatchCoolCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F1E32))
                            .clickable { onResetView() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchMini24hSparkline(
    points: List<com.dariopittera.ecowittpixel.model.HistoricalPoint>,
    windowStartMs: Long,
    windowDurationMs: Long,
    scrubFraction: Float,
    tempColor: Color,
    humColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minT = (points.minOf { it.temperatureC } - 0.5f)
        val maxT = (points.maxOf { it.temperatureC } + 0.5f).coerceAtLeast(minT + 1f)
        val minH = (points.minOf { it.humidityPct } - 2f).coerceAtLeast(0f)
        val maxH = (points.maxOf { it.humidityPct } + 2f).coerceAtLeast(minH + 5f)
        val spanMs = windowDurationMs.coerceAtLeast(60_000L)
        val maxGapMs = if (windowDurationMs > 24L * 3600_000L) 6L * 3600_000L else 150L * 60_000L

        val tempPath = Path()
        val humPath = Path()

        points.forEachIndexed { i, pt ->
            val timeFrac = ((pt.timestampMs - windowStartMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)
            val x = timeFrac * size.width
            val yTemp = size.height - ((pt.temperatureC - minT) / (maxT - minT)).coerceIn(0f, 1f) * size.height
            val yHum = size.height - ((pt.humidityPct - minH) / (maxH - minH)).coerceIn(0f, 1f) * size.height
            if (i == 0 || (pt.timestampMs - points[i - 1].timestampMs) > maxGapMs) {
                tempPath.moveTo(x, yTemp)
                humPath.moveTo(x, yHum)
            } else {
                tempPath.lineTo(x, yTemp)
                humPath.lineTo(x, yHum)
            }
        }

        drawPath(humPath, color = humColor.copy(alpha = 0.72f), style = Stroke(width = 1.8f))
        drawPath(tempPath, color = tempColor, style = Stroke(width = 2.4f))

        val targetTs = windowStartMs + (scrubFraction.coerceIn(0f, 1f) * spanMs).toLong()
        val activePt = points.minByOrNull { abs(it.timestampMs - targetTs) } ?: points.last()
        val scrubX = scrubFraction.coerceIn(0f, 1f) * size.width
        val activeY = size.height - ((activePt.temperatureC - minT) / (maxT - minT)).coerceIn(0f, 1f) * size.height
        val activeHumY = size.height - ((activePt.humidityPct - minH) / (maxH - minH)).coerceIn(0f, 1f) * size.height

        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(scrubX, 0f),
            end = Offset(scrubX, size.height),
            strokeWidth = 1.4f
        )
        drawCircle(
            color = humColor,
            radius = 3.2f,
            center = Offset(scrubX, activeHumY)
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
    val hasUpdateAvailable = otaLabel.startsWith("Update available", ignoreCase = true)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchOledBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Wear OS sync",
                color = WatchCoolCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "GW1200 (${snapshot.rooms.size} rooms) • v${BuildConfig.VERSION_NAME}",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WatchComfortEmerald)
                    .clickable { onSyncNow() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sync telemetry now",
                    color = Color(0xFF041A2E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hasUpdateAvailable) Color(0xFF1E3A5F) else Color(0xFF172B44))
                    .border(
                        1.dp,
                        if (hasUpdateAvailable) WatchThermalAmber else WatchCoolCyan.copy(alpha = 0.7f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = !isCheckingOta) { onCheckWatchOta() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = otaLabel.ifBlank { "No updates (v${BuildConfig.VERSION_NAME})" },
                    color = if (hasUpdateAvailable) WatchThermalAmber else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "‹ Back to rooms",
                color = WatchCoolCyan,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onBackToFirstRoom() }
            )
        }
    }
}
