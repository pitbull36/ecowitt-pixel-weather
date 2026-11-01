package com.dariopittera.ecowittpixel.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.model.PressureUnit
import com.dariopittera.ecowittpixel.model.Psychrometrics
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.TempUnit
import com.dariopittera.ecowittpixel.ui.theme.ComfortEmerald
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.HumidViolet
import com.dariopittera.ecowittpixel.ui.theme.ThermalAmber
import com.dariopittera.ecowittpixel.ui.theme.ThermalCoral
import com.dariopittera.ecowittpixel.ui.theme.humidityAccentColor
import com.dariopittera.ecowittpixel.ui.theme.temperatureAccentColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChartMetric(val label: String) {
    TEMPERATURE("Temperature"),
    HUMIDITY("Humidity"),
    DEW_POINT("Dew point"),
    PRESSURE("Barometer")
}

private val SERIES_COLORS = listOf(
    Color(0xFF4DD0E1), // Living room (GW1200)
    Color(0xFFFF8A65), // Bedroom (Ch 1)
    Color(0xFFA5D6A7), // Balcony garden (Ch 2)
    Color(0xFFFFD54F),
    Color(0xFFCE93D8)
)

@Composable
fun MultiRoomSplineChart(
    rooms: List<SensorRoomState>,
    metric: ChartMetric,
    windowHours: Int,
    tempUnit: TempUnit,
    pressureUnit: PressureUnit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val cutoffMs = System.currentTimeMillis() - windowHours * 3600_000L

    val activeRooms = remember(rooms, metric) {
        if (metric == ChartMetric.PRESSURE) {
            rooms.filter { it.relativePressureHpa != null }
        } else {
            rooms
        }
    }

    val roomSeries = remember(activeRooms, metric, windowHours, tempUnit, pressureUnit) {
        activeRooms.mapIndexed { idx, room ->
            val pts = room.history.filter { it.timestampMs >= cutoffMs }
            val values = pts.mapNotNull { pt ->
                val v = when (metric) {
                    ChartMetric.TEMPERATURE -> tempUnit.convert(pt.temperatureC)
                    ChartMetric.HUMIDITY -> pt.humidityPct
                    ChartMetric.DEW_POINT -> tempUnit.convert(
                        Psychrometrics.computeDewPointC(pt.temperatureC, pt.humidityPct)
                    )
                    ChartMetric.PRESSURE -> pt.pressureHpa?.let {
                        if (pressureUnit == PressureUnit.HPA) it else it * 0.02953f
                    }
                }
                if (v != null) Pair(pt.timestampMs, v) else null
            }
            Triple(room.roomName, SERIES_COLORS[idx % SERIES_COLORS.size], values)
        }.filter { it.third.size >= 2 }
    }

    if (roomSeries.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("Collecting historical telemetry points...", color = Color.LightGray)
        }
        return
    }

    val primaryPoints = roomSeries.first().third
    var scrubIndex by remember(primaryPoints.size) { mutableIntStateOf(primaryPoints.lastIndex) }
    val safeScrubIdx = scrubIndex.coerceIn(0, primaryPoints.lastIndex)
    val scrubbedTimestamp = primaryPoints[safeScrubIdx].first
    val timeFormatter = remember(windowHours) {
        if (windowHours <= 24) {
            SimpleDateFormat("EEE HH:mm", Locale.UK)
        } else {
            SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK)
        }
    }
    val unitSuffix = when (metric) {
        ChartMetric.TEMPERATURE, ChartMetric.DEW_POINT -> tempUnit.symbol
        ChartMetric.HUMIDITY -> "%"
        ChartMetric.PRESSURE -> " ${pressureUnit.symbol}"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Interactive Scrubbing Header
        Surface(
            color = Color(0xFF0E1828),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatter.format(Date(scrubbedTimestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF90CAF9)
                    )
                    Text(
                        text = "Drag across chart to inspect",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF78909C)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    roomSeries.forEach { (name, color, pts) ->
                        val idx = safeScrubIdx.coerceIn(0, pts.lastIndex)
                        val value = pts[idx].second
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${name.substringBefore(" (")}: " +
                                    String.format(Locale.UK, "%.1f%s", value, unitSuffix),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val allValues = roomSeries.flatMap { series -> series.third.map { it.second } }
        val rawMin = allValues.minOrNull() ?: 0f
        val rawMax = allValues.maxOrNull() ?: 100f
        val span = (rawMax - rawMin).coerceAtLeast(2.0f)
        val yMin = rawMin - span * 0.14f
        val yMax = rawMax + span * 0.14f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .pointerInput(primaryPoints.size) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        val newIdx = (frac * primaryPoints.lastIndex).toInt()
                            .coerceIn(0, primaryPoints.lastIndex)
                        if (newIdx != scrubIndex) {
                            scrubIndex = newIdx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(primaryPoints.size) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newIdx = (frac * primaryPoints.lastIndex).toInt()
                            .coerceIn(0, primaryPoints.lastIndex)
                        if (newIdx != scrubIndex) {
                            scrubIndex = newIdx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
        ) {
            val leftPad = 46f
            val rightPad = 14f
            val topPad = 12f
            val bottomPad = 24f
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

            // Horizontal grid lines + Y-axis labels
            for (g in 0..3) {
                val frac = g / 3f
                val y = topPad + chartH * (1f - frac)
                val v = yMin + (yMax - yMin) * frac
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1.2f
                )
                val label = textMeasurer.measure(
                    text = String.format(Locale.UK, "%.0f", v),
                    style = TextStyle(color = Color(0xFF90A4AE), fontSize = 10.sp)
                )
                drawText(label, topLeft = Offset(4f, (y - label.size.height / 2f).coerceAtLeast(0f)))
            }

            // Draw each room's smooth cubic Bézier spline
            roomSeries.forEachIndexed { seriesIdx, (_, seriesColor, pts) ->
                if (pts.size < 2) return@forEachIndexed
                val screenPts = pts.mapIndexed { i, pair ->
                    val x = leftPad + (i.toFloat() / pts.lastIndex.coerceAtLeast(1)) * chartW
                    val normY = ((pair.second - yMin) / (yMax - yMin)).coerceIn(0f, 1f)
                    val y = topPad + chartH * (1f - normY)
                    Offset(x, y)
                }

                val splinePath = Path().apply {
                    moveTo(screenPts.first().x, screenPts.first().y)
                    for (i in 0 until screenPts.lastIndex) {
                        val p0 = screenPts[i]
                        val p1 = screenPts[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                // Fill gradient under primary series
                if (seriesIdx == 0) {
                    val fillPath = Path().apply {
                        addPath(splinePath)
                        lineTo(screenPts.last().x, topPad + chartH)
                        lineTo(screenPts.first().x, topPad + chartH)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(seriesColor.copy(alpha = 0.28f), Color.Transparent),
                            startY = topPad,
                            endY = topPad + chartH
                        )
                    )
                }

                drawPath(
                    path = splinePath,
                    color = seriesColor,
                    style = Stroke(width = if (seriesIdx == 0) 4.2f else 3.2f, cap = StrokeCap.Round)
                )

                // Draw active scrub point circle
                val sIdx = safeScrubIdx.coerceIn(0, screenPts.lastIndex)
                val activePt = screenPts[sIdx]
                drawCircle(
                    color = seriesColor.copy(alpha = 0.35f),
                    radius = 11f,
                    center = activePt
                )
                drawCircle(
                    color = seriesColor,
                    radius = 5.5f,
                    center = activePt
                )
            }

            // Vertical scrub cursor line
            val cursorX = leftPad + (safeScrubIdx.toFloat() / primaryPoints.lastIndex.coerceAtLeast(1)) * chartW
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(cursorX, topPad),
                end = Offset(cursorX, topPad + chartH),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
    }
}

@Composable
fun PsychrometricComfortScatterChart(
    rooms: List<SensorRoomState>,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var focusedRoomIdx by remember { mutableStateOf<Int?>(null) }

    fun clampPan(pan: Offset, scale: Float, plotW: Float, plotH: Float): Offset {
        if (scale <= 1.01f) return Offset.Zero
        val maxPanX = (plotW * (scale - 1f)) / 2f
        val maxPanY = (plotH * (scale - 1f)) / 2f
        return Offset(
            pan.x.coerceIn(-maxPanX, maxPanX),
            pan.y.coerceIn(-maxPanY, maxPanY)
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Psychrometric comfort matrix (ASHRAE 55)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Pinch or double-tap to zoom in, drag to pan, or tap a room pill below to focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Interactive Zoom Control Pill (- | 1.0x | + | Reset)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14243A))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .clickable {
                            val nextScale = (zoomScale / 1.4f).coerceIn(1f, 5f)
                            zoomScale = nextScale
                            if (nextScale <= 1.02f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                focusedRoomIdx = null
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text("−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (zoomScale > 1.05f) Color(0xFF1A3A38) else Color(0xFF14243A))
                        .border(
                            1.dp,
                            if (zoomScale > 1.05f) ComfortEmerald else Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            focusedRoomIdx = null
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (zoomScale > 1.05f) String.format(Locale.UK, "%.1fx ↺", zoomScale) else "1.0x",
                        color = if (zoomScale > 1.05f) ComfortEmerald else Color(0xFF90CAF9),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14243A))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .clickable {
                            zoomScale = (zoomScale * 1.4f).coerceIn(1f, 5f)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text("+", color = CoolCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Room Legend Strip (tap any room to focus/highlight its trail, or All rooms)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allSelected = focusedRoomIdx == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (allSelected) Color(0xFF1F3654) else Color(0xFF0E1828))
                    .border(
                        width = 1.dp,
                        color = if (allSelected) CoolCyan else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        focusedRoomIdx = null
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "All rooms",
                    color = if (allSelected) Color.White else Color(0xFF94A3B8),
                    fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                )
            }

            rooms.forEachIndexed { idx, room ->
                val color = SERIES_COLORS[idx % SERIES_COLORS.size]
                val isSelected = focusedRoomIdx == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF1C314D) else Color(0xFF0E1828))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) color else Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (focusedRoomIdx == idx) {
                                focusedRoomIdx = null
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                focusedRoomIdx = idx
                                zoomScale = 2.4f
                                // Center pan on this room's coordinates
                                val nx = ((room.temperatureC - 6f) / (32f - 6f)).coerceIn(0f, 1f)
                                val ny = ((room.humidityPct - 20f) / (95f - 20f)).coerceIn(0f, 1f)
                                // Normalized offset from center (0.5, 0.5)
                                val relX = 0.5f - nx
                                val relY = ny - 0.5f
                                panOffset = Offset(relX * 260f * 2.4f, relY * 190f * 2.4f)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${room.roomName.substringBefore(" (")}: ${tempUnit.format(room.temperatureC)} • ${room.humidityPct.toInt()}%",
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(255.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0E1828))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val leftPad = 44f
                            val rightPad = 16f
                            val topPad = 16f
                            val bottomPad = 28f
                            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
                            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                            val centerX = leftPad + plotW / 2f
                            val centerY = topPad + plotH / 2f
                            if (zoomScale > 2.2f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                focusedRoomIdx = null
                            } else {
                                val nextScale = (zoomScale * 1.85f).coerceIn(1f, 4.5f)
                                val ratio = nextScale / zoomScale
                                val newPan = Offset(
                                    x = (panOffset.x + (centerX - tapOffset.x) * (ratio - 1f)),
                                    y = (panOffset.y + (centerY - tapOffset.y) * (ratio - 1f))
                                )
                                zoomScale = nextScale
                                panOffset = clampPan(newPan, nextScale, plotW, plotH)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val leftPad = 44f
                        val rightPad = 16f
                        val topPad = 16f
                        val bottomPad = 28f
                        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
                        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                        val centerX = leftPad + plotW / 2f
                        val centerY = topPad + plotH / 2f

                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                        val scaleFactor = newScale / zoomScale
                        val rawPan = if (newScale > 1.01f) {
                            Offset(
                                x = (panOffset.x - (centroid.x - centerX) * (scaleFactor - 1f)) + pan.x,
                                y = (panOffset.y - (centroid.y - centerY) * (scaleFactor - 1f)) + pan.y
                            )
                        } else {
                            Offset.Zero
                        }
                        zoomScale = newScale
                        panOffset = clampPan(rawPan, newScale, plotW, plotH)
                    }
                }
        ) {
            val leftPad = 44f
            val rightPad = 16f
            val topPad = 16f
            val bottomPad = 28f
            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
            val centerX = leftPad + plotW / 2f
            val centerY = topPad + plotH / 2f

            val baseMinTempC = 6f
            val baseMaxTempC = 32f
            val baseMinHum = 20f
            val baseMaxHum = 95f

            val safePan = clampPan(panOffset, zoomScale, plotW, plotH)

            fun mapPt(tempC: Float, humPct: Float): Offset {
                val nx = (tempC - baseMinTempC) / (baseMaxTempC - baseMinTempC)
                val ny = (humPct - baseMinHum) / (baseMaxHum - baseMinHum)
                val baseX = leftPad + nx * plotW
                val baseY = topPad + (1f - ny) * plotH
                val zoomedX = centerX + (baseX - centerX) * zoomScale + safePan.x
                val zoomedY = centerY + (baseY - centerY) * zoomScale + safePan.y
                return Offset(zoomedX, zoomedY)
            }

            fun screenXToTempC(screenX: Float): Float {
                val baseX = centerX + (screenX - centerX - safePan.x) / zoomScale
                val nx = ((baseX - leftPad) / plotW).coerceIn(0f, 1f)
                return baseMinTempC + nx * (baseMaxTempC - baseMinTempC)
            }

            fun screenYToHumPct(screenY: Float): Float {
                val baseY = centerY + (screenY - centerY - safePan.y) / zoomScale
                val ny = (1f - (baseY - topPad) / plotH).coerceIn(0f, 1f)
                return baseMinHum + ny * (baseMaxHum - baseMinHum)
            }

            // Dynamic Y-axis grid lines + humidity labels that adapt to zoom/pan
            for (i in 0..4) {
                val frac = i / 4f
                val y = topPad + plotH * (1f - frac)
                val humVal = screenYToHumPct(y)
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1f
                )
                val lbl = textMeasurer.measure(
                    text = "${humVal.toInt()}%",
                    style = TextStyle(color = Color(0xFF90A4AE), fontSize = 9.sp)
                )
                drawText(lbl, topLeft = Offset(6f, y - lbl.size.height / 2f))
            }

            // Dynamic X-axis vertical grid lines + temperature labels that adapt to zoom/pan
            for (i in 0..4) {
                val frac = 0.08f + (i / 4f) * 0.84f
                val x = leftPad + plotW * frac
                val tempValC = screenXToTempC(x)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(x, topPad),
                    end = Offset(x, topPad + plotH),
                    strokeWidth = 1f
                )
                val lbl = textMeasurer.measure(
                    text = tempUnit.format(tempValC, decimals = if (zoomScale >= 1.8f) 1 else 0),
                    style = TextStyle(color = Color(0xFF90A4AE), fontSize = 9.sp)
                )
                drawText(lbl, topLeft = Offset(x - lbl.size.width / 2f, size.height - 19f))
            }

            // Clip inner plot canvas so zoomed shapes stay strictly inside the axes
            clipRect(
                left = leftPad,
                top = topPad,
                right = leftPad + plotW,
                bottom = topPad + plotH
            ) {
                // Mould risk upper band (> 68% RH)
                val mouldTopLeft = mapPt(baseMinTempC, baseMaxHum)
                val mouldBottomRight = mapPt(baseMaxTempC, 68f)
                drawRect(
                    color = HumidViolet.copy(alpha = 0.16f),
                    topLeft = mouldTopLeft,
                    size = Size(
                        (mouldBottomRight.x - mouldTopLeft.x).coerceAtLeast(0f),
                        (mouldBottomRight.y - mouldTopLeft.y).coerceAtLeast(0f)
                    )
                )
                drawLine(
                    color = HumidViolet.copy(alpha = 0.55f),
                    start = Offset(mouldTopLeft.x, mouldBottomRight.y),
                    end = Offset(mouldBottomRight.x, mouldBottomRight.y),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
                val mouldLbl = textMeasurer.measure(
                    text = "Mould risk (>68% RH)",
                    style = TextStyle(color = Color(0xFFD8B4FE), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                )
                drawText(
                    mouldLbl,
                    topLeft = Offset(
                        (mouldTopLeft.x + 8f).coerceAtLeast(leftPad + 6f),
                        (mouldBottomRight.y - mouldLbl.size.height - 4f).coerceAtLeast(topPad + 4f)
                    )
                )

                // ASHRAE 55 Optimal Comfort Polygon (18.5..24.2°C, 38..58% RH)
                val c1 = mapPt(18.5f, 38f)
                val c2 = mapPt(24.2f, 38f)
                val c3 = mapPt(23.5f, 58f)
                val c4 = mapPt(18.0f, 58f)
                val comfortPoly = Path().apply {
                    moveTo(c1.x, c1.y)
                    lineTo(c2.x, c2.y)
                    lineTo(c3.x, c3.y)
                    lineTo(c4.x, c4.y)
                    close()
                }
                drawPath(comfortPoly, color = ComfortEmerald.copy(alpha = 0.22f))
                drawPath(comfortPoly, color = ComfortEmerald, style = Stroke(width = 2.4f))

                val zoneLabel = textMeasurer.measure(
                    text = "Optimal comfort zone",
                    style = TextStyle(color = ComfortEmerald, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                )
                drawText(
                    zoneLabel,
                    topLeft = Offset(
                        (c4.x + 6f).coerceIn(leftPad + 4f, leftPad + plotW - zoneLabel.size.width - 4f),
                        (c4.y + 6f).coerceIn(topPad + 4f, topPad + plotH - zoneLabel.size.height - 4f)
                    )
                )

                // Track placed label boxes for smart collision avoidance
                val placedLabelRects = mutableListOf<Rect>()

                // Draw 24h trails & live position for each room
                rooms.forEachIndexed { idx, room ->
                    val isDimmed = focusedRoomIdx != null && focusedRoomIdx != idx
                    val alphaMul = if (isDimmed) 0.25f else 1f
                    val color = SERIES_COLORS[idx % SERIES_COLORS.size]
                    val recent24h = room.history.takeLast(32)
                    if (recent24h.size >= 2) {
                        val trailPath = Path()
                        recent24h.forEachIndexed { ptIdx, pt ->
                            val p = mapPt(pt.temperatureC, pt.humidityPct)
                            if (ptIdx == 0) trailPath.moveTo(p.x, p.y) else trailPath.lineTo(p.x, p.y)
                            if (zoomScale >= 1.6f && !isDimmed && ptIdx % 2 == 0) {
                                drawCircle(
                                    color = color.copy(alpha = 0.55f),
                                    radius = 3.2f,
                                    center = p
                                )
                            }
                        }
                        drawPath(
                            path = trailPath,
                            color = color.copy(alpha = 0.52f * alphaMul),
                            style = Stroke(width = if (focusedRoomIdx == idx) 3.8f else 2.6f, cap = StrokeCap.Round)
                        )
                    }

                    val livePos = mapPt(room.temperatureC, room.humidityPct)
                    drawCircle(color = color.copy(alpha = 0.32f * alphaMul), radius = 15f, center = livePos)
                    drawCircle(color = Color(0xFF0E1828), radius = 8.5f, center = livePos)
                    drawCircle(color = color.copy(alpha = alphaMul), radius = 6.5f, center = livePos)

                    if (!isDimmed) {
                        val shortName = room.roomName.substringBefore(" (")
                        val labelText = if (zoomScale >= 1.6f) {
                            "$shortName (${tempUnit.format(room.temperatureC)}, ${room.humidityPct.toInt()}%)"
                        } else {
                            shortName
                        }
                        val tag = textMeasurer.measure(
                            text = labelText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        val padH = 6f
                        val padV = 3f
                        val boxW = tag.size.width + padH * 2f
                        val boxH = tag.size.height + padV * 2f

                        // Candidate positions around the dot (right, left, above, below)
                        val candidates = listOf(
                            Offset(livePos.x + 12f, livePos.y - boxH / 2f),
                            Offset(livePos.x - boxW - 12f, livePos.y - boxH / 2f),
                            Offset(livePos.x - boxW / 2f, livePos.y - boxH - 12f),
                            Offset(livePos.x - boxW / 2f, livePos.y + 12f)
                        )

                        val chosenTopLeft = candidates
                            .map { cand ->
                                Offset(
                                    cand.x.coerceIn(leftPad + 4f, leftPad + plotW - boxW - 4f),
                                    cand.y.coerceIn(topPad + 4f, topPad + plotH - boxH - 4f)
                                )
                            }
                            .firstOrNull { clamped ->
                                val rect = Rect(clamped, Size(boxW, boxH))
                                placedLabelRects.none { it.overlaps(rect) }
                            } ?: Offset(
                            (livePos.x + 12f).coerceIn(leftPad + 4f, leftPad + plotW - boxW - 4f),
                            (livePos.y - boxH / 2f + idx * 18f).coerceIn(topPad + 4f, topPad + plotH - boxH - 4f)
                        )

                        val finalRect = Rect(chosenTopLeft, Size(boxW, boxH))
                        placedLabelRects.add(finalRect)

                        drawRoundRect(
                            color = Color(0xCC0B1524),
                            topLeft = chosenTopLeft,
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = color.copy(alpha = 0.7f),
                            topLeft = chosenTopLeft,
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 1.2f)
                        )
                        drawText(
                            tag,
                            topLeft = Offset(chosenTopLeft.x + padH, chosenTopLeft.y + padV)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyRangeBarsCard(
    room: SensorRoomState,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    val dayBuckets = remember(room.history) {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("EEE d MMM", Locale.UK)
        room.history
            .groupBy { pt ->
                cal.timeInMillis = pt.timestampMs
                val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
                dayKey to fmt.format(Date(pt.timestampMs))
            }
            .entries
            .sortedBy { it.key.first }
            .takeLast(7)
    }

    val allTemps = room.history.map { it.temperatureC }
    val globalMinT = (allTemps.minOrNull() ?: 14f) - 0.5f
    val globalMaxT = (allTemps.maxOrNull() ?: 26f) + 0.5f
    val tempSpan = (globalMaxT - globalMinT).coerceAtLeast(2f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "7-day temperature and humidity range bars (${room.roomName.substringBefore(" (")})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))

        dayBuckets.forEach { (keyPair, pts) ->
            val dayLabel = keyPair.second
            val minT = pts.minOf { it.temperatureC }
            val maxT = pts.maxOf { it.temperatureC }
            val minH = pts.minOf { it.humidityPct }
            val maxH = pts.maxOf { it.humidityPct }

            val startFrac = ((minT - globalMinT) / tempSpan).coerceIn(0f, 0.9f)
            val endFrac = ((maxT - globalMinT) / tempSpan).coerceIn(startFrac + 0.08f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCFD8DC),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(86.dp)
                )
                Text(
                    text = tempUnit.format(minT),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(56.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0E1828))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barX = size.width * startFrac
                        val barW = (size.width * (endFrac - startFrac)).coerceAtLeast(14f)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    temperatureAccentColor(minT),
                                    temperatureAccentColor(maxT)
                                ),
                                startX = barX,
                                endX = barX + barW
                            ),
                            topLeft = Offset(barX, 0f),
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tempUnit.format(maxT),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = temperatureAccentColor(maxT),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(56.dp)
                )
                Text(
                    text = "${minH.toInt()}–${maxH.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF90CAF9),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(62.dp)
                )
            }
        }
    }
}

@Composable
fun HourlyThermalHeatmapCard(
    room: SensorRoomState,
    showHumidity: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (showHumidity) {
                "7-day × 24-hour humidity calendar heatmap"
            } else {
                "7-day × 24-hour thermal calendar heatmap"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Columns represent hours of the day (00:00 to 23:00) across the past 7 days.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0BEC5)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0E1828))
                .padding(8.dp)
        ) {
            val rows = 7
            val cols = 24
            val leftLabelW = 42f
            val cellW = (size.width - leftLabelW) / cols
            val cellH = (size.height - 20f) / rows
            val dayFmt = SimpleDateFormat("EEE", Locale.UK)
            val now = System.currentTimeMillis()

            for (r in 0 until rows) {
                val dayStartMs = now - (rows - 1 - r) * 24L * 3600_000L
                val dayLbl = textMeasurer.measure(
                    text = dayFmt.format(Date(dayStartMs)),
                    style = TextStyle(color = Color(0xFF90A4AE), fontSize = 10.sp)
                )
                drawText(dayLbl, topLeft = Offset(2f, r * cellH + 2f))

                for (c in 0 until cols) {
                    val idx = (r * cols + c).coerceIn(0, (room.history.size - 1).coerceAtLeast(0))
                    val pt = room.history.getOrNull(idx)
                    val cellColor = if (pt != null) {
                        if (showHumidity) {
                            humidityAccentColor(pt.humidityPct).copy(
                                alpha = ((pt.humidityPct - 25f) / 65f).coerceIn(0.25f, 0.95f)
                            )
                        } else {
                            temperatureAccentColor(pt.temperatureC).copy(
                                alpha = ((pt.temperatureC - 8f) / 22f).coerceIn(0.30f, 0.95f)
                            )
                        }
                    } else {
                        Color(0xFF1A273B)
                    }
                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(leftLabelW + c * cellW + 1.5f, r * cellH + 1.5f),
                        size = Size((cellW - 3f).coerceAtLeast(2f), (cellH - 3f).coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // Hour markers along bottom
            for (h in listOf(0, 6, 12, 18, 23)) {
                val hLbl = textMeasurer.measure(
                    text = String.format(Locale.UK, "%02d:00", h),
                    style = TextStyle(color = Color(0xFF78909C), fontSize = 9.sp)
                )
                drawText(
                    hLbl,
                    topLeft = Offset(leftLabelW + h * cellW - 4f, rows * cellH + 2f)
                )
            }
        }
    }
}
