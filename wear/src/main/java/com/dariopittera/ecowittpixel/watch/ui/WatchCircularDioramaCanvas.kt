package com.dariopittera.ecowittpixel.watch.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.dariopittera.ecowittpixel.model.MouldRiskLevel
import com.dariopittera.ecowittpixel.model.RoomAlertBreach
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.SensorRoomState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class WatchIsoCamera(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val yawRad: Float,
    val pitchRad: Float,
    val wireframeOnly: Boolean
) {
    fun project(x: Float, y: Float, z: Float): Offset {
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val rx = x * cosY - y * sinY
        val ry = x * sinY + y * cosY
        val sx = centerX + rx * scale
        val sy = centerY + (ry * sin(pitchRad) - z * cos(pitchRad)) * scale
        return Offset(sx, sy)
    }
}

/**
 * Circular Wear OS canvas rendering:
 * 1. Top curved bezel arc for Temperature (205°..335°):
 *    - White (<19°C with frost crystals), Yellow (19-22.9°C), Orange (23-25.9°C), Red (26-29.9°C),
 *      and animated molten lava blobs (>=30°C) moving inside the curved glass tube.
 * 2. Bottom curved bezel arc for Humidity (155°..25° via 90° bottom):
 *    - Uncluttered dots-to-drops progression travelling along the bottom curve:
 *      <35% RH (5 amber micro-dots), 35-59% RH (8 emerald micro-dots),
 *      60-64% RH (10 small cyan teardrops), >=65% RH (14 glossy cobalt-indigo teardrops with specular highlights).
 * 3. Central 3D isometric room diorama with archetype furniture, <19°C icicles/freeze effects,
 *    wrist-tilt gyroscope parallax, and Always-On Display (Ambient Mode) neon wireframe rendering.
 */
@Composable
fun WatchCircularDioramaCanvas(
    room: SensorRoomState,
    breaches: List<RoomAlertBreach>,
    gyroEnabled: Boolean,
    isAmbient: Boolean,
    ambientMinuteTick: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var gyroTiltX by remember { mutableFloatStateOf(0f) }
    var gyroTiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(gyroEnabled, isAmbient) {
        if (!gyroEnabled || isAmbient) {
            gyroTiltX = 0f
            gyroTiltY = 0f
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val gx = (event.values.getOrNull(0) ?: 0f) / 9.81f
                    val gy = (event.values.getOrNull(1) ?: 0f) / 9.81f
                    gyroTiltX = gyroTiltX * 0.84f + (-gx.coerceIn(-0.6f, 0.6f)) * 0.16f
                    gyroTiltY = gyroTiltY * 0.84f + ((gy - 0.35f).coerceIn(-0.5f, 0.5f)) * 0.16f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (gravitySensor != null) {
                sensorManager?.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "watch_diorama_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "watch_phase"
    )
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "watch_particles"
    )

    val phase = if (isAmbient) 0f else animPhase
    val particles = if (isAmbient) 0.35f else particleProgress

    val hasTopAlert = breaches.any { it.affectsTopTempArc }
    val hasBottomAlert = breaches.any { it.affectsBottomHumidityArc }

    // Burn-in protection shift in Ambient Mode (+/- 2px)
    val burnInDx = if (isAmbient) ((ambientMinuteTick % 3) - 1) * 2f else 0f
    val burnInDy = if (isAmbient) (((ambientMinuteTick / 3) % 3) - 1) * 2f else 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        val diameter = min(size.width, size.height)
        val cx = size.width / 2f + burnInDx
        val cy = size.height / 2f + burnInDy
        val tempColor = watchTemperatureColor(room.temperatureC)
        val humColor = watchHumidityColor(room.humidityPct)

        // 0. Background radial glow in interactive mode; pure pitch black in Ambient Mode
        if (isAmbient) {
            drawRect(color = WatchOledBlack)
        } else {
            drawRect(color = WatchOledBlack)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tempColor.copy(alpha = 0.14f),
                        humColor.copy(alpha = 0.08f),
                        WatchOledBlack
                    ),
                    center = Offset(cx, cy),
                    radius = diameter * 0.48f
                ),
                radius = diameter * 0.48f,
                center = Offset(cx, cy)
            )
        }

        // 1. Central 3D Isometric Room Diorama
        val baseYaw = (0.78f + gyroTiltX * 0.25f).coerceIn(0.20f, 1.38f)
        val basePitch = (0.52f + gyroTiltY * 0.16f).coerceIn(0.30f, 0.78f)
        val cam = WatchIsoCamera(
            centerX = cx,
            centerY = cy + diameter * 0.055f,
            scale = diameter * 0.235f,
            yawRad = baseYaw,
            pitchRad = basePitch,
            wireframeOnly = isAmbient
        )

        drawWatchRoomDiorama(
            cam = cam,
            room = room,
            tempColor = tempColor,
            humColor = humColor,
            animPhase = phase,
            particleProgress = particles,
            isAmbient = isAmbient
        )

        // 2. Top curved bezel arc for Temperature (205° to 335°, centered at 270° top)
        drawTopTemperatureBezelArc(
            cx = cx,
            cy = cy,
            diameter = diameter,
            temperatureC = room.temperatureC,
            tempColor = tempColor,
            hasAlert = hasTopAlert,
            animPhase = phase,
            particleProgress = particles,
            isAmbient = isAmbient
        )

        // 3. Bottom curved bezel arc for Humidity (155° down through 90° to 25°)
        drawBottomHumidityBezelArc(
            cx = cx,
            cy = cy,
            diameter = diameter,
            humidityPct = room.humidityPct,
            humColor = humColor,
            hasAlert = hasBottomAlert,
            animPhase = phase,
            particleProgress = particles,
            isAmbient = isAmbient
        )
    }
}

private fun DrawScope.drawTopTemperatureBezelArc(
    cx: Float,
    cy: Float,
    diameter: Float,
    temperatureC: Float,
    tempColor: Color,
    hasAlert: Boolean,
    animPhase: Float,
    particleProgress: Float,
    isAmbient: Boolean
) {
    val inset = diameter * 0.052f
    val arcRectSize = Size(diameter - inset * 2f, diameter - inset * 2f)
    val arcTopLeft = Offset(cx - arcRectSize.width / 2f, cy - arcRectSize.height / 2f)
    val arcRadius = arcRectSize.width / 2f

    val startAngle = 205f
    val maxSweep = 130f
    val fillNorm = ((temperatureC - 8f) / 26f).coerceIn(0.10f, 1f)
    val fillSweep = maxSweep * fillNorm
    val isLavaMode = temperatureC >= 30.0f
    val isFreezeMode = temperatureC < 19.0f

    val trackThickness = if (isAmbient) diameter * 0.016f else diameter * 0.040f
    val fillThickness = if (isAmbient) diameter * 0.012f else diameter * 0.026f

    // Alert pulse halo on the top bezel arc when a temperature criterion is breached
    if (hasAlert && !isAmbient) {
        val pulseAlpha = 0.25f + 0.22f * (0.5f + 0.5f * sin(animPhase * 4f))
        drawArc(
            color = WatchThermalCoral.copy(alpha = pulseAlpha),
            startAngle = startAngle - 2f,
            sweepAngle = maxSweep + 4f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness * 1.85f, cap = StrokeCap.Round)
        )
    }

    // Outer glass tube track
    drawArc(
        color = if (isAmbient) Color(0xFF334155) else Color(0xFF162438),
        startAngle = startAngle,
        sweepAngle = maxSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcRectSize,
        style = Stroke(width = trackThickness, cap = StrokeCap.Round)
    )
    if (!isAmbient) {
        drawArc(
            color = Color(0x88B0BEC5),
            startAngle = startAngle,
            sweepAngle = maxSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness + 2.2f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF0A1322),
            startAngle = startAngle,
            sweepAngle = maxSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness - 2.2f, cap = StrokeCap.Round)
        )
    }

    // Mercury fill arc
    val mercuryColor = if (isLavaMode && !isAmbient) Color(0xFFD50000) else tempColor
    drawArc(
        color = if (isAmbient) tempColor.copy(alpha = 0.85f) else mercuryColor,
        startAngle = startAngle,
        sweepAngle = fillSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcRectSize,
        style = Stroke(width = fillThickness, cap = StrokeCap.Round)
    )

    if (!isAmbient) {
        // Thermometer bulb at left anchor of the top arc (205°)
        val bulbRad = Math.toRadians(startAngle.toDouble()).toFloat()
        val bulbCenter = Offset(
            cx + cos(bulbRad) * arcRadius,
            cy + sin(bulbRad) * arcRadius
        )
        val bulbRadius = trackThickness * 0.68f
        drawCircle(
            color = if (isLavaMode) Color(0xFFFF6D00) else tempColor,
            radius = bulbRadius,
            center = bulbCenter
        )
        drawCircle(
            color = Color(0xDDFFFFFF),
            radius = bulbRadius,
            center = bulbCenter,
            style = Stroke(width = 1.6f)
        )

        // Molten lava blobs moving inside the top temperature bezel arc when >= 30°C
        if (isLavaMode) {
            for (b in 0 until 6) {
                val blobT = (particleProgress * 1.25f + b * 0.16f) % 1f
                val cycle = if (b % 2 == 0) blobT else (1f - blobT)
                val deg = startAngle + cycle * (fillSweep - 4f).coerceAtLeast(10f)
                val rad = Math.toRadians(deg.toDouble()).toFloat()
                val wobble = sin(animPhase * 2.8f + b * 1.4f) * (fillThickness * 0.16f)
                val pt = Offset(
                    cx + cos(rad) * (arcRadius + wobble),
                    cy + sin(rad) * (arcRadius + wobble)
                )
                val blobColor = when (b % 3) {
                    0 -> Color(0xFFFFEA00)
                    1 -> Color(0xFFFF6D00)
                    else -> Color(0xFFFF1744)
                }
                drawCircle(
                    color = blobColor,
                    radius = fillThickness * (0.38f + 0.10f * sin(animPhase * 3f + b)),
                    center = pt
                )
            }
        } else if (isFreezeMode) {
            // Frost crystals sparkling along the white <19°C top bezel arc
            for (c in 0..4) {
                val deg = startAngle + 14f + c * 24f
                val rad = Math.toRadians(deg.toDouble()).toFloat()
                val pt = Offset(cx + cos(rad) * arcRadius, cy + sin(rad) * arcRadius)
                drawSixPointCrystal(pt, radius = fillThickness * 0.44f)
            }
        }
    }
}

private fun DrawScope.drawBottomHumidityBezelArc(
    cx: Float,
    cy: Float,
    diameter: Float,
    humidityPct: Float,
    humColor: Color,
    hasAlert: Boolean,
    animPhase: Float,
    particleProgress: Float,
    isAmbient: Boolean
) {
    val inset = diameter * 0.052f
    val arcRectSize = Size(diameter - inset * 2f, diameter - inset * 2f)
    val arcTopLeft = Offset(cx - arcRectSize.width / 2f, cy - arcRectSize.height / 2f)
    val arcRadius = arcRectSize.width / 2f

    // Bottom arc spans from left-bottom (155°) counter-clockwise through 90° (bottom) to right-bottom (25°)
    val startAngle = 155f
    val maxSweep = -130f
    val fillNorm = (humidityPct / 100f).coerceIn(0.08f, 1f)
    val fillSweep = maxSweep * fillNorm

    val trackThickness = if (isAmbient) diameter * 0.016f else diameter * 0.040f
    val fillThickness = if (isAmbient) diameter * 0.012f else diameter * 0.022f

    // Alert pulse halo on the bottom bezel arc when a humidity/mould/ventilation criterion is breached
    if (hasAlert && !isAmbient) {
        val pulseAlpha = 0.25f + 0.22f * (0.5f + 0.5f * sin(animPhase * 4f + 1.2f))
        drawArc(
            color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
            startAngle = startAngle + 2f,
            sweepAngle = maxSweep - 4f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness * 1.85f, cap = StrokeCap.Round)
        )
    }

    // Bottom curved channel track
    drawArc(
        color = if (isAmbient) Color(0xFF1E293B) else Color(0xFF0E1B2E),
        startAngle = startAngle,
        sweepAngle = maxSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcRectSize,
        style = Stroke(width = trackThickness, cap = StrokeCap.Round)
    )
    if (!isAmbient) {
        drawArc(
            color = humColor.copy(alpha = 0.45f),
            startAngle = startAngle,
            sweepAngle = maxSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness + 1.8f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF091322),
            startAngle = startAngle,
            sweepAngle = maxSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRectSize,
            style = Stroke(width = trackThickness - 2f, cap = StrokeCap.Round)
        )
    }

    // Subtle base fill arc
    drawArc(
        color = humColor.copy(alpha = if (isAmbient) 0.78f else 0.34f),
        startAngle = startAngle,
        sweepAngle = fillSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcRectSize,
        style = Stroke(width = fillThickness, cap = StrokeCap.Round)
    )

    if (!isAmbient) {
        // Dots-to-drops progression travelling smoothly along the bottom humidity arc
        val count = when {
            humidityPct < 35f -> 5
            humidityPct < 60f -> 8
            humidityPct < 65f -> 10
            else -> 14
        }
        val speed = when {
            humidityPct < 35f -> 0.42f
            humidityPct < 60f -> 0.60f
            humidityPct < 65f -> 0.78f
            else -> 0.94f
        }

        for (i in 0 until count) {
            val t = ((particleProgress * speed) + (i.toFloat() / count.toFloat())) % 1f
            val deg = startAngle + t * maxSweep
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val radialJitter = sin(animPhase * 1.6f + i * 1.3f) * (trackThickness * 0.10f)
            val pt = Offset(
                cx + cos(rad) * (arcRadius + radialJitter),
                cy + sin(rad) * (arcRadius + radialJitter)
            )
            if (humidityPct < 60f) {
                val dotRadius = if (humidityPct < 35f) trackThickness * 0.20f else trackThickness * 0.25f
                drawCircle(
                    color = humColor.copy(alpha = 0.88f),
                    radius = dotRadius,
                    center = pt
                )
            } else {
                val dropRadius = if (humidityPct >= 65f) trackThickness * 0.33f else trackThickness * 0.26f
                val dropColor = if (humidityPct >= 65f) {
                    if (i % 2 == 0) Color(0xFF38BDF8) else Color(0xFF818CF8)
                } else {
                    Color(0xFF29B6F6)
                }
                drawWatchTeardrop(
                    center = pt,
                    radius = dropRadius,
                    color = dropColor,
                    withHighlight = humidityPct >= 65f
                )
            }
        }
    }
}

private fun DrawScope.drawWatchRoomDiorama(
    cam: WatchIsoCamera,
    room: SensorRoomState,
    tempColor: Color,
    humColor: Color,
    animPhase: Float,
    particleProgress: Float,
    isAmbient: Boolean
) {
    // 1. Floor slab
    val floorTop = if (room.archetype.isOutdoor) Color(0xFF284238) else Color(0xFF253248)
    drawWatchIsoPrism(
        cam = cam,
        x0 = -1f, x1 = 1f,
        y0 = -1f, y1 = 1f,
        z0 = -0.14f, z1 = 0f,
        topColor = floorTop,
        leftColor = Color(0xFF162030),
        rightColor = Color(0xFF1D293D),
        wireColor = tempColor.copy(alpha = 0.55f)
    )

    // 2. Back cutaway walls
    val wallHeight = 1.12f
    if (!room.archetype.isOutdoor) {
        drawWatchIsoPrism(
            cam, -1f, -0.88f, -1f, 1f, 0f, wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF1E2B40),
            rightColor = Color(0xFF2B3B55),
            wireColor = Color(0xFF64748B)
        )
        drawWatchIsoPrism(
            cam, -1f, 1f, -1f, -0.88f, 0f, wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF2E3F5B),
            rightColor = Color(0xFF223149),
            wireColor = Color(0xFF64748B)
        )
        drawWatchWindow(cam, room, isAmbient)
    } else {
        drawWatchIsoPrism(
            cam, -1f, -0.92f, -1f, 1f, 0f, 0.45f,
            topColor = Color(0x9980DEEA),
            leftColor = Color(0x444DD0E1),
            rightColor = Color(0x554DD0E1),
            wireColor = WatchCoolCyan.copy(alpha = 0.65f)
        )
        drawWatchIsoPrism(
            cam, -1f, 1f, -1f, -0.92f, 0f, 0.45f,
            topColor = Color(0x9980DEEA),
            leftColor = Color(0x554DD0E1),
            rightColor = Color(0x444DD0E1),
            wireColor = WatchCoolCyan.copy(alpha = 0.65f)
        )
    }

    // 3. Archetype furniture
    when (room.archetype) {
        RoomArchetype.LIVING_ROOM -> {
            // Library bookcase with open wooden shelves, sparse neutral books, and an orb ornament + TV + Sofa + Coffee Table
            drawWatchIsoPrism(
                cam, -0.87f, -0.64f, -0.78f, 0.32f, 0.02f, 0.98f,
                Color(0xFF6D4C41), Color(0xFF4E342E), Color(0xFF3E2723), WatchThermalAmber.copy(alpha = 0.7f)
            )
            for (shelf in 0..2) {
                val sz = 0.22f + shelf * 0.26f
                drawWatchIsoPrism(
                    cam, -0.85f, -0.63f, -0.74f, 0.28f, sz, sz + 0.03f,
                    Color(0xFF8D6E63), Color(0xFF5D4037), Color(0xFF6D4C41), WatchThermalAmber.copy(alpha = 0.45f)
                )
            }
            // Sparse neutral books on middle shelf + small ceramic orb on top shelf
            drawWatchIsoPrism(
                cam, -0.80f, -0.66f, -0.64f, -0.55f, 0.51f, 0.66f,
                Color(0xFFCFD8DC), Color(0xFF78909C), Color(0xFF90A4AE), Color(0xFFB0BEC5)
            )
            drawCircle(
                color = Color(0xFFD7CCC8),
                radius = cam.scale * 0.038f,
                center = cam.project(-0.72f, 0.06f, 0.82f)
            )
            drawWatchIsoPrism(
                cam, -0.30f, 0.50f, -0.84f, -0.75f, 0.28f, 0.82f,
                Color(0xFF4DD0E1), Color(0xFF00ACC1), Color(0xFF26C6DA), WatchCoolCyan
            )
            drawWatchIsoPrism(
                cam, -0.36f, 0.48f, 0.12f, 0.56f, 0.02f, 0.26f,
                Color(0xFF4A6382), Color(0xFF364961), Color(0xFF3F5570), tempColor.copy(alpha = 0.75f)
            )
            drawWatchIsoPrism(
                cam, -0.20f, 0.34f, -0.32f, -0.04f, 0.02f, 0.17f,
                Color(0xFFB88655), Color(0xFF8A613B), Color(0xFF9E7146), WatchThermalAmber.copy(alpha = 0.6f)
            )
        }
        RoomArchetype.BEDROOM -> {
            drawWatchIsoPrism(
                cam, -0.65f, 0.35f, -0.78f, 0.18f, 0.02f, 0.26f,
                Color(0xFFD7CCC8), Color(0xFF5D4037), Color(0xFF6D4C41), tempColor.copy(alpha = 0.75f)
            )
            drawWatchIsoPrism(
                cam, -0.63f, 0.33f, -0.35f, 0.16f, 0.25f, 0.31f,
                tempColor.copy(alpha = 0.68f), tempColor.copy(alpha = 0.48f), tempColor.copy(alpha = 0.58f), tempColor
            )
            drawWatchIsoPrism(
                cam, -0.65f, 0.35f, -0.84f, -0.74f, 0.02f, 0.58f,
                Color(0xFF6D4C41), Color(0xFF4E342E), Color(0xFF5D4037), WatchThermalAmber.copy(alpha = 0.7f)
            )
        }
        RoomArchetype.STUDY -> {
            drawWatchIsoPrism(
                cam, -0.55f, 0.48f, -0.75f, -0.25f, 0.24f, 0.32f,
                Color(0xFFA1887F), Color(0xFF6D4C41), Color(0xFF795548), WatchThermalAmber.copy(alpha = 0.7f)
            )
            drawWatchIsoPrism(
                cam, -0.42f, -0.04f, -0.66f, -0.58f, 0.35f, 0.64f,
                Color(0xFF90CAF9), Color(0xFF1E88E5), Color(0xFF42A5F5), WatchCoolCyan
            )
            drawWatchIsoPrism(
                cam, 0.02f, 0.38f, -0.66f, -0.58f, 0.35f, 0.64f,
                Color(0xFF80DEEA), tempColor.copy(alpha = 0.85f), Color(0xFF26C6DA), WatchCoolCyan
            )
        }
        RoomArchetype.KITCHEN -> {
            drawWatchIsoPrism(
                cam, -0.78f, 0.72f, -0.82f, -0.40f, 0.02f, 0.38f,
                Color(0xFFECEFF1), Color(0xFF455A64), Color(0xFF546E7A), Color(0xFF94A3B8)
            )
            drawWatchIsoPrism(
                cam, -0.45f, 0.48f, -0.05f, 0.42f, 0.02f, 0.36f,
                Color(0xFFCFD8DC), Color(0xFF37474F), Color(0xFF455A64), tempColor
            )
        }
        RoomArchetype.BATHROOM -> {
            drawWatchIsoPrism(
                cam, -0.72f, 0.18f, -0.75f, -0.20f, 0.02f, 0.32f,
                Color(0xFF4DD0E1).copy(alpha = 0.75f), Color(0xFFCFD8DC), Color(0xFFE0E6ED), WatchCoolCyan
            )
            drawWatchIsoPrism(
                cam, 0.32f, 0.78f, -0.75f, -0.28f, 0.02f, 0.36f,
                Color(0xFFF5F7FA), humColor.copy(alpha = 0.65f), Color(0xFF90A4AE), humColor
            )
        }
        RoomArchetype.BALCONY_GARDEN -> {
            drawWatchIsoPrism(
                cam, -0.75f, 0.30f, -0.80f, -0.42f, 0.02f, 0.25f,
                Color(0xFF5D4037), Color(0xFF4E342E), Color(0xFF6D4C41), WatchComfortEmerald
            )
            val tree1 = cam.project(-0.46f, -0.60f, 0.56f)
            val tree2 = cam.project(-0.06f, -0.58f, 0.48f)
            if (isAmbient) {
                drawCircle(
                    color = WatchComfortEmerald,
                    radius = cam.scale * 0.20f,
                    center = tree1,
                    style = Stroke(width = 1.6f)
                )
                drawCircle(
                    color = WatchComfortEmerald,
                    radius = cam.scale * 0.16f,
                    center = tree2,
                    style = Stroke(width = 1.6f)
                )
            } else {
                drawCircle(color = Color(0xFF2E7D32), radius = cam.scale * 0.20f, center = tree1)
                drawCircle(color = Color(0xFF388E3C), radius = cam.scale * 0.16f, center = tree2)
                if (room.temperatureC < 19.0f) {
                    drawCircle(color = Color(0xFFF8FAFC), radius = cam.scale * 0.13f, center = tree1)
                    drawCircle(color = Color(0xFFE1F5FE), radius = cam.scale * 0.11f, center = tree2)
                }
            }
        }
    }

    // 4. Freeze icicles and crystals inside the room when < 19°C
    if (room.temperatureC < 19.0f && !isAmbient) {
        for (i in 0..4) {
            val ix = -0.32f + i * 0.18f
            val pL = cam.project(ix - 0.03f, -0.84f, 0.88f)
            val pR = cam.project(ix + 0.03f, -0.84f, 0.88f)
            val pTip = cam.project(ix, -0.84f, 0.72f)
            val icicle = Path().apply {
                moveTo(pL.x, pL.y)
                lineTo(pR.x, pR.y)
                lineTo(pTip.x, pTip.y)
                close()
            }
            drawPath(icicle, color = Color(0xE6E1F5FE))
        }
        drawSixPointCrystal(cam.project(0.05f, -0.15f, 0.28f), radius = cam.scale * 0.075f)
    }

    // 5. Tall 3D glass thermometer pillar on the right corner
    val fillNorm = ((room.temperatureC - 10f) / 22f).coerceIn(0.12f, 1f)
    val mercuryTop = 0.14f + fillNorm * 0.74f
    val isLavaMode = room.temperatureC >= 30.0f
    drawWatchIsoPrism(
        cam, 0.75f, 0.89f, -0.84f, -0.70f, 0.10f, 0.90f,
        Color(0x50B0BEC5), Color(0x38CFD8DC), Color(0x4590A4AE), tempColor.copy(alpha = 0.7f)
    )
    if (!isAmbient) {
        val colColor = if (isLavaMode) Color(0xFFD50000) else tempColor
        drawWatchIsoPrism(
            cam, 0.78f, 0.86f, -0.81f, -0.73f, 0.12f, mercuryTop,
            colColor, colColor.copy(alpha = 0.9f), colColor.copy(alpha = 0.82f), colColor
        )
        if (isLavaMode) {
            for (b in 0..3) {
                val bt = (particleProgress * 1.3f + b * 0.25f) % 1f
                val bz = 0.15f + bt * (mercuryTop - 0.16f).coerceAtLeast(0.2f)
                val pt = cam.project(0.82f, -0.77f, bz)
                drawCircle(
                    color = if (b % 2 == 0) Color(0xFFFFEA00) else Color(0xFFFF6D00),
                    radius = cam.scale * 0.04f,
                    center = pt
                )
            }
        }
        // Rising room humidity dots / teardrops
        val dropCount = when {
            room.humidityPct < 35f -> 4
            room.humidityPct < 60f -> 6
            room.humidityPct < 65f -> 8
            else -> 11
        }
        for (i in 0 until dropCount) {
            val seed = i * 1.618f
            val px = ((seed * 3.1f) % 1.2f) - 0.6f
            val py = ((seed * 2.1f) % 1.2f) - 0.6f
            val pz = ((particleProgress + (seed % 1f)) % 1f) * 0.82f + 0.12f
            val pt = cam.project(px, py, pz)
            if (room.humidityPct < 60f) {
                drawCircle(
                    color = humColor.copy(alpha = 0.55f),
                    radius = cam.scale * 0.026f,
                    center = pt
                )
            } else {
                drawWatchTeardrop(
                    center = pt,
                    radius = cam.scale * (if (room.humidityPct >= 65f) 0.045f else 0.035f),
                    color = if (room.humidityPct >= 65f) Color(0xFF38BDF8) else Color(0xFF29B6F6),
                    withHighlight = room.humidityPct >= 65f
                )
            }
        }
    }
}

private fun DrawScope.drawWatchWindow(
    cam: WatchIsoCamera,
    room: SensorRoomState,
    isAmbient: Boolean
) {
    val p1 = cam.project(-0.35f, -0.86f, 0.36f)
    val p2 = cam.project(0.52f, -0.86f, 0.36f)
    val p3 = cam.project(0.52f, -0.86f, 0.94f)
    val p4 = cam.project(-0.35f, -0.86f, 0.94f)
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }
    if (!isAmbient) {
        drawPath(
            path,
            color = if (room.temperatureC < 19.0f) Color(0x9981D4FA) else Color(0xFF2D608C)
        )
    }
    drawPath(path, color = Color(0xFF90A4AE), style = Stroke(width = if (isAmbient) 1.4f else 2.2f))
}

private fun DrawScope.drawWatchTeardrop(
    center: Offset,
    radius: Float,
    color: Color,
    withHighlight: Boolean
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius * 1.4f)
        cubicTo(
            center.x + radius * 1.1f, center.y - radius * 0.2f,
            center.x + radius * 1.05f, center.y + radius * 0.95f,
            center.x, center.y + radius
        )
        cubicTo(
            center.x - radius * 1.05f, center.y + radius * 0.95f,
            center.x - radius * 1.1f, center.y - radius * 0.2f,
            center.x, center.y - radius * 1.4f
        )
        close()
    }
    drawPath(path = path, color = color)
    if (withHighlight) {
        drawCircle(
            color = Color.White.copy(alpha = 0.72f),
            radius = radius * 0.26f,
            center = Offset(center.x - radius * 0.26f, center.y + radius * 0.14f)
        )
    }
}

private fun DrawScope.drawSixPointCrystal(center: Offset, radius: Float) {
    for (angleDeg in listOf(0f, 60f, 120f)) {
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val dx = cos(rad) * radius
        val dy = sin(rad) * radius
        drawLine(
            color = Color(0xEEFFFFFF),
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = 1.6f
        )
    }
}

private fun DrawScope.drawWatchIsoPrism(
    cam: WatchIsoCamera,
    x0: Float,
    x1: Float,
    y0: Float,
    y1: Float,
    z0: Float,
    z1: Float,
    topColor: Color,
    leftColor: Color,
    rightColor: Color,
    wireColor: Color
) {
    val t00 = cam.project(x0, y0, z1)
    val t10 = cam.project(x1, y0, z1)
    val t11 = cam.project(x1, y1, z1)
    val t01 = cam.project(x0, y1, z1)
    val b10 = cam.project(x1, y0, z0)
    val b11 = cam.project(x1, y1, z0)
    val b01 = cam.project(x0, y1, z0)

    val leftFace = Path().apply {
        moveTo(t01.x, t01.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b01.x, b01.y)
        close()
    }
    val rightFace = Path().apply {
        moveTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b10.x, b10.y)
        close()
    }
    val topFace = Path().apply {
        moveTo(t00.x, t00.y)
        lineTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(t01.x, t01.y)
        close()
    }

    if (cam.wireframeOnly) {
        val stroke = Stroke(width = 1.3f)
        drawPath(leftFace, color = wireColor.copy(alpha = 0.55f), style = stroke)
        drawPath(rightFace, color = wireColor.copy(alpha = 0.65f), style = stroke)
        drawPath(topFace, color = wireColor, style = stroke)
    } else {
        drawPath(leftFace, color = leftColor)
        drawPath(rightFace, color = rightColor)
        drawPath(topFace, color = topColor)
    }
}
