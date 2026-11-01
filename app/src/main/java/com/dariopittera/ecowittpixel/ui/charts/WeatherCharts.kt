package com.dariopittera.ecowittpixel.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Psychrometric comfort matrix (ASHRAE 55 zone)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Plots each sensor's 24-hour trail and current position against the ideal indoor comfort zone and the mould threshold.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0BEC5)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0E1828))
        ) {
            val leftPad = 42f
            val rightPad = 18f
            val topPad = 18f
            val bottomPad = 28f
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad

            val minTempC = 6f
            val maxTempC = 32f
            val minHum = 20f
            val maxHum = 95f

            fun mapPt(tempC: Float, humPct: Float): Offset {
                val nx = ((tempC - minTempC) / (maxTempC - minTempC)).coerceIn(0f, 1f)
                val ny = ((humPct - minHum) / (maxHum - minHum)).coerceIn(0f, 1f)
                return Offset(leftPad + nx * plotW, topPad + (1f - ny) * plotH)
            }

            // Grid lines
            for (i in 0..4) {
                val frac = i / 4f
                val y = topPad + plotH * (1f - frac)
                val humVal = minHum + (maxHum - minHum) * frac
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1f
                )
                val lbl = textMeasurer.measure(
                    text = "${humVal.toInt()}%",
                    style = TextStyle(color = Color(0xFF78909C), fontSize = 9.sp)
                )
                drawText(lbl, topLeft = Offset(6f, y - lbl.size.height / 2f))
            }

            // Mould risk upper band (> 68% RH)
            val mouldTopLeft = mapPt(minTempC, maxHum)
            val mouldBottomRight = mapPt(maxTempC, 68f)
            drawRect(
                color = HumidViolet.copy(alpha = 0.14f),
                topLeft = mouldTopLeft,
                size = Size(mouldBottomRight.x - mouldTopLeft.x, mouldBottomRight.y - mouldTopLeft.y)
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
            drawPath(comfortPoly, color = ComfortEmerald.copy(alpha = 0.24f))
            drawPath(comfortPoly, color = ComfortEmerald, style = Stroke(width = 2.2f))

            val zoneLabel = textMeasurer.measure(
                text = "Optimal comfort zone",
                style = TextStyle(color = ComfortEmerald, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            )
            drawText(zoneLabel, topLeft = Offset(c4.x + 6f, c4.y + 6f))

            // Draw 24h trails & live position for each room
            rooms.forEachIndexed { idx, room ->
                val color = SERIES_COLORS[idx % SERIES_COLORS.size]
                val recent24h = room.history.takeLast(24)
                if (recent24h.size >= 2) {
                    val trailPath = Path()
                    recent24h.forEachIndexed { ptIdx, pt ->
                        val p = mapPt(pt.temperatureC, pt.humidityPct)
                        if (ptIdx == 0) trailPath.moveTo(p.x, p.y) else trailPath.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = trailPath,
                        color = color.copy(alpha = 0.42f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                val livePos = mapPt(room.temperatureC, room.humidityPct)
                drawCircle(color = color.copy(alpha = 0.35f), radius = 13f, center = livePos)
                drawCircle(color = color, radius = 6.5f, center = livePos)

                val tag = textMeasurer.measure(
                    text = room.roomName.substringBefore(" ("),
                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                )
                val tx = (livePos.x + 10f).coerceAtMost(size.width - tag.size.width - 10f)
                val ty = (livePos.y - tag.size.height / 2f).coerceIn(8f, size.height - 24f)
                drawText(tag, topLeft = Offset(tx, ty))
            }

            // X-axis temperature labels
            for (tC in listOf(10f, 16f, 20f, 24f, 28f)) {
                val p = mapPt(tC, minHum)
                val lbl = textMeasurer.measure(
                    text = tempUnit.format(tC, decimals = 0),
                    style = TextStyle(color = Color(0xFF78909C), fontSize = 9.sp)
                )
                drawText(lbl, topLeft = Offset(p.x - lbl.size.width / 2f, size.height - 18f))
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
