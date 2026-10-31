package com.dariopittera.ecowittpixel.ui.shader

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.model.MouldRiskLevel
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.TempUnit
import com.dariopittera.ecowittpixel.ui.theme.humidityAccentColor
import com.dariopittera.ecowittpixel.ui.theme.temperatureAccentColor
import kotlin.math.cos
import kotlin.math.sin

private const val ATMOSPHERE_AGSL_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float tempNorm;     // 0.0 (cold) .. 1.0 (hot)
    uniform float humidityNorm; // 0.0 (dry)  .. 1.0 (humid)
    uniform float tiltX;
    uniform float tiltY;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 p = uv - float2(0.5 + tiltX * 0.08, 0.48 + tiltY * 0.08);
        float dist = length(p);

        // Thermal convection wave + humidity mist ripple
        float wave1 = sin(uv.x * 9.0 + time * 1.4 + uv.y * 4.0) * 0.5 + 0.5;
        float wave2 = cos(uv.y * 11.0 - time * 1.1 + uv.x * 6.0) * 0.5 + 0.5;
        float mist = wave1 * wave2 * humidityNorm;

        // Cold cyan/blue to warm amber/coral radial aura
        half3 coldCol = half3(0.08, 0.28, 0.48);
        half3 comfortCol = half3(0.07, 0.36, 0.35);
        half3 warmCol = half3(0.55, 0.24, 0.14);

        half3 baseTone = mix(coldCol, comfortCol, smoothstep(0.0, 0.55, tempNorm));
        baseTone = mix(baseTone, warmCol, smoothstep(0.5, 1.0, tempNorm));

        float radialGlow = smoothstep(0.72, 0.05, dist);
        half3 bg = half3(0.04, 0.07, 0.13) + baseTone * radialGlow * 0.72;
        bg += half3(0.18, 0.32, 0.44) * mist * 0.24 * radialGlow;

        return half4(bg, 1.0);
    }
"""

private class IsoCamera(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val yawRad: Float,
    val pitchRad: Float
) {
    fun project(x: Float, y: Float, z: Float): Offset {
        // Rotate around vertical Z axis by yawRad
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val rx = x * cosY - y * sinY
        val ry = x * sinY + y * cosY

        // Isometric screen projection modulated by pitchRad
        val sx = centerX + rx * scale
        val sy = centerY + (ry * sin(pitchRad) - z * cos(pitchRad)) * scale
        return Offset(sx, sy)
    }
}

@Composable
fun RoomDioramaCanvas(
    room: SensorRoomState,
    allRooms: List<SensorRoomState>,
    wholeHomeOverview: Boolean,
    tempUnit: TempUnit,
    gyroEnabled: Boolean,
    timeOfDayHours: Float,
    onSelectRoomIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    var gyroTiltX by remember { mutableFloatStateOf(0f) }
    var gyroTiltY by remember { mutableFloatStateOf(0f) }
    var userDragYaw by remember { mutableFloatStateOf(0f) }
    var userDragPitch by remember { mutableFloatStateOf(0f) }

    DisposableEffect(gyroEnabled) {
        if (!gyroEnabled) {
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
                    // Smooth low-pass filter for buttery 120Hz Pixel display motion
                    gyroTiltX = gyroTiltX * 0.85f + (-gx.coerceIn(-0.6f, 0.6f)) * 0.15f
                    gyroTiltY = gyroTiltY * 0.85f + ((gy - 0.5f).coerceIn(-0.5f, 0.5f)) * 0.15f
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

    val infiniteTransition = rememberInfiniteTransition(label = "diorama_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(ATMOSPHERE_AGSL_SHADER) }.getOrNull()
        } else null
    }

    val tempNorm = ((room.temperatureC - 8f) / 24f).coerceIn(0f, 1f)
    val humidityNorm = ((room.humidityPct - 25f) / 65f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(wholeHomeOverview, allRooms.size) {
                detectTapGestures(
                    onDoubleTap = {
                        userDragYaw = 0f
                        userDragPitch = 0f
                    },
                    onTap = { tapOffset ->
                        if (wholeHomeOverview && allRooms.isNotEmpty()) {
                            val sliceHeight = size.height / allRooms.size.toFloat()
                            val tappedIdx = (tapOffset.y / sliceHeight).toInt()
                                .coerceIn(0, allRooms.lastIndex)
                            onSelectRoomIndex(tappedIdx)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    userDragYaw = (userDragYaw + dragAmount.x * 0.0045f).coerceIn(-0.55f, 0.55f)
                    userDragPitch = (userDragPitch + dragAmount.y * 0.003f).coerceIn(-0.22f, 0.22f)
                }
            }
    ) {
        // 1. Render AGSL shader or radial gradient atmosphere
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null) {
            agslShader.setFloatUniform("resolution", size.width, size.height)
            agslShader.setFloatUniform("time", animPhase)
            agslShader.setFloatUniform("tempNorm", tempNorm)
            agslShader.setFloatUniform("humidityNorm", humidityNorm)
            agslShader.setFloatUniform("tiltX", gyroTiltX + userDragYaw)
            agslShader.setFloatUniform("tiltY", gyroTiltY + userDragPitch)
            drawRect(brush = ShaderBrush(agslShader), size = size)
        } else {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        temperatureAccentColor(room.temperatureC).copy(alpha = 0.28f),
                        Color(0xFF0A121E)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.48f),
                    radius = size.minDimension * 0.72f
                ),
                size = size
            )
        }

        val yaw = 0.785398f + gyroTiltX * 0.28f + userDragYaw
        val pitch = (0.54f + gyroTiltY * 0.18f + userDragPitch).coerceIn(0.32f, 0.78f)

        if (wholeHomeOverview && allRooms.isNotEmpty()) {
            drawWholeHomeIsoStack(
                rooms = allRooms,
                selectedChannelId = room.channelId,
                yaw = yaw,
                pitch = pitch,
                animPhase = animPhase,
                particleProgress = particleProgress,
                timeOfDayHours = timeOfDayHours,
                tempUnit = tempUnit,
                textMeasurer = textMeasurer
            )
        } else {
            val scale = size.minDimension * 0.34f
            val cam = IsoCamera(
                centerX = size.width * 0.5f,
                centerY = size.height * 0.62f,
                scale = scale,
                yawRad = yaw,
                pitchRad = pitch
            )
            drawSingleRoomDiorama(
                cam = cam,
                room = room,
                animPhase = animPhase,
                particleProgress = particleProgress,
                timeOfDayHours = timeOfDayHours,
                tempUnit = tempUnit,
                textMeasurer = textMeasurer
            )
        }
    }
}

private fun DrawScope.drawWholeHomeIsoStack(
    rooms: List<SensorRoomState>,
    selectedChannelId: String,
    yaw: Float,
    pitch: Float,
    animPhase: Float,
    particleProgress: Float,
    timeOfDayHours: Float,
    tempUnit: TempUnit,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val count = rooms.size.coerceAtLeast(1)
    val scale = size.minDimension * (0.21f / (count / 3f).coerceAtLeast(0.85f))
    val verticalSpacing = size.height / (count + 0.65f)

    rooms.forEachIndexed { idx, r ->
        val cy = verticalSpacing * (idx + 0.85f)
        val isSelected = r.channelId == selectedChannelId
        val cam = IsoCamera(
            centerX = size.width * 0.46f,
            centerY = cy,
            scale = if (isSelected) scale * 1.07f else scale * 0.94f,
            yawRad = yaw,
            pitchRad = pitch
        )

        drawSingleRoomDiorama(
            cam = cam,
            room = r,
            animPhase = animPhase,
            particleProgress = particleProgress,
            timeOfDayHours = timeOfDayHours,
            tempUnit = tempUnit,
            textMeasurer = textMeasurer,
            compactMode = true
        )

        // Floating floor callout badge on the right
        val anchor = cam.project(1.05f, -0.5f, 0.45f)
        val badgeColor = temperatureAccentColor(r.temperatureC)
        drawLine(
            color = badgeColor.copy(alpha = if (isSelected) 0.95f else 0.55f),
            start = cam.project(0.7f, -0.2f, 0.35f),
            end = Offset(anchor.x.coerceAtMost(size.width * 0.70f), anchor.y),
            strokeWidth = if (isSelected) 3.5f else 2f
        )
        val labelResult = textMeasurer.measure(
            text = "${r.roomName.take(16)}\n${tempUnit.format(r.temperatureC)}  •  ${r.humidityPct.toInt()}%",
            style = TextStyle(
                color = Color.White,
                fontSize = if (isSelected) 12.sp else 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        )
        val boxX = (size.width - labelResult.size.width - 28f).coerceAtLeast(size.width * 0.58f)
        val boxY = (anchor.y - labelResult.size.height / 2f).coerceIn(12f, size.height - 56f)
        drawRoundRect(
            color = Color(0xCC101C2E),
            topLeft = Offset(boxX - 12f, boxY - 6f),
            size = Size(labelResult.size.width + 24f, labelResult.size.height + 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
        )
        if (isSelected) {
            drawRoundRect(
                color = badgeColor,
                topLeft = Offset(boxX - 12f, boxY - 6f),
                size = Size(labelResult.size.width + 24f, labelResult.size.height + 12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(width = 2.5f)
            )
        }
        drawText(labelResult, topLeft = Offset(boxX, boxY))
    }
}

private fun DrawScope.drawSingleRoomDiorama(
    cam: IsoCamera,
    room: SensorRoomState,
    animPhase: Float,
    particleProgress: Float,
    timeOfDayHours: Float,
    tempUnit: TempUnit,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    compactMode: Boolean = false
) {
    val tempColor = temperatureAccentColor(room.temperatureC)
    val humColor = humidityAccentColor(room.humidityPct)
    val isDaytime = timeOfDayHours in 6.5f..19.5f

    // 1. Floor slab (-1..1 in X and Y, thickness -0.14..0 in Z)
    val floorTopColor = if (room.archetype.isOutdoor) {
        Color(0xFF284238)
    } else {
        Color(0xFF253248)
    }
    drawIsoPrism(
        cam = cam,
        x0 = -1f, x1 = 1f,
        y0 = -1f, y1 = 1f,
        z0 = -0.14f, z1 = 0f,
        topColor = floorTopColor,
        leftColor = Color(0xFF162030),
        rightColor = Color(0xFF1D293D)
    )

    // Floor grid tiles + thermal floor aura
    for (i in -3..3) {
        val t = i / 4f
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = cam.project(t, -0.96f, 0.005f),
            end = cam.project(t, 0.96f, 0.005f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = cam.project(-0.96f, t, 0.005f),
            end = cam.project(0.96f, t, 0.005f),
            strokeWidth = 1.2f
        )
    }

    // Thermal floor glow polygon
    val floorGlowCenter = cam.project(0f, 0f, 0.01f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                tempColor.copy(alpha = 0.30f + 0.08f * sin(animPhase)),
                Color.Transparent
            ),
            center = floorGlowCenter,
            radius = cam.scale * 1.15f
        ),
        radius = cam.scale * 1.15f,
        center = floorGlowCenter
    )

    // 2. Back cutaway walls (or outdoor balustrade + sky backdrop)
    val wallHeight = if (compactMode) 1.05f else 1.28f
    if (!room.archetype.isOutdoor) {
        // Left back wall (x = -1..-0.88, y = -1..1, z = 0..wallHeight)
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = -0.88f,
            y0 = -1f, y1 = 1f,
            z0 = 0f, z1 = wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF1E2B40),
            rightColor = Color(0xFF2B3B55)
        )
        // Right back wall (x = -1..1, y = -1..-0.88, z = 0..wallHeight)
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = 1f,
            y0 = -1f, y1 = -0.88f,
            z0 = 0f, z1 = wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF2E3F5B),
            rightColor = Color(0xFF223149)
        )

        // Window on right back wall with sky + condensation/frost
        drawIsoWindow(
            cam = cam,
            isDaytime = isDaytime,
            room = room,
            animPhase = animPhase
        )
    } else {
        // Outdoor balcony glass balustrade along back edges
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = -0.92f,
            y0 = -1f, y1 = 1f,
            z0 = 0f, z1 = 0.45f,
            topColor = Color(0x9980DEEA),
            leftColor = Color(0x444DD0E1),
            rightColor = Color(0x554DD0E1)
        )
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = 1f,
            y0 = -1f, y1 = -0.92f,
            z0 = 0f, z1 = 0.45f,
            topColor = Color(0x9980DEEA),
            leftColor = Color(0x554DD0E1),
            rightColor = Color(0x444DD0E1)
        )
    }

    // 3. Archetype-specific 3D furniture & props
    when (room.archetype) {
        RoomArchetype.LIVING_ROOM -> drawLivingRoomFurniture(cam, tempColor, animPhase)
        RoomArchetype.BEDROOM -> drawBedroomFurniture(cam, tempColor, animPhase)
        RoomArchetype.STUDY -> drawStudyFurniture(cam, tempColor, animPhase)
        RoomArchetype.KITCHEN -> drawKitchenFurniture(cam, tempColor, animPhase)
        RoomArchetype.BATHROOM -> drawBathroomFurniture(cam, humColor, animPhase)
        RoomArchetype.BALCONY_GARDEN -> drawBalconyGardenScene(cam, isDaytime, animPhase)
    }

    // 4. Wall-mounted Ecowitt GW1200 / WH31 Sensor module with pulsing telemetry beacon
    val sensorPos = cam.project(-0.82f, 0.25f, 0.68f)
    drawIsoPrism(
        cam = cam,
        x0 = -0.87f, x1 = -0.76f,
        y0 = 0.16f, y1 = 0.34f,
        z0 = 0.56f, z1 = 0.78f,
        topColor = Color(0xFFF5F7FA),
        leftColor = Color(0xFFCFD8DC),
        rightColor = Color(0xFFE2E8F0)
    )
    val pulseRadius = (4f + 3f * (sin(animPhase * 2f) * 0.5f + 0.5f)) * (cam.scale / 110f)
    drawCircle(
        color = tempColor.copy(alpha = 0.45f),
        radius = pulseRadius * 2.2f,
        center = sensorPos
    )
    drawCircle(
        color = tempColor,
        radius = pulseRadius.coerceAtLeast(3.5f),
        center = sensorPos
    )

    // 5. Dynamic Humidity Vapour Droplets & Thermal Convection Waves
    drawEnvironmentalParticles(
        cam = cam,
        room = room,
        tempColor = tempColor,
        humColor = humColor,
        animPhase = animPhase,
        particleProgress = particleProgress
    )

    // 6. Floating 3D Sensor Pill Callout inside single-room view
    if (!compactMode) {
        val calloutAnchor = cam.project(-0.80f, 0.25f, 0.92f)
        val badgeText = "${tempUnit.format(room.temperatureC)}  •  ${room.humidityPct.toInt()}% RH"
        val measured = textMeasurer.measure(
            text = badgeText,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        val bx = (calloutAnchor.x - measured.size.width / 2f).coerceIn(16f, size.width - measured.size.width - 28f)
        val by = (calloutAnchor.y - 36f).coerceIn(14f, size.height - 48f)
        drawLine(
            color = tempColor.copy(alpha = 0.8f),
            start = sensorPos,
            end = Offset(bx + measured.size.width / 2f, by + measured.size.height + 6f),
            strokeWidth = 2.2f
        )
        drawRoundRect(
            color = Color(0xD9111E32),
            topLeft = Offset(bx - 12f, by - 6f),
            size = Size(measured.size.width + 24f, measured.size.height + 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
        drawRoundRect(
            color = tempColor.copy(alpha = 0.85f),
            topLeft = Offset(bx - 12f, by - 6f),
            size = Size(measured.size.width + 24f, measured.size.height + 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
            style = Stroke(width = 2f)
        )
        drawText(measured, topLeft = Offset(bx, by))
    }
}

private fun DrawScope.drawIsoWindow(
    cam: IsoCamera,
    isDaytime: Boolean,
    room: SensorRoomState,
    animPhase: Float
) {
    val p1 = cam.project(-0.35f, -0.86f, 0.35f)
    val p2 = cam.project(0.55f, -0.86f, 0.35f)
    val p3 = cam.project(0.55f, -0.86f, 0.98f)
    val p4 = cam.project(-0.35f, -0.86f, 0.98f)

    val windowPath = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }
    val skyColor = if (isDaytime) Color(0xFF3876A8) else Color(0xFF101D36)
    drawPath(windowPath, color = skyColor)

    // Window condensation mist or frost crystals if mould/condensation risk is elevated or temp is low
    if (room.mouldRisk != MouldRiskLevel.LOW || room.humidityPct >= 62f) {
        drawPath(
            path = windowPath,
            color = Color(0x66B2EBF2)
        )
        // Condensation droplets along bottom of window pane
        for (i in 0..6) {
            val frac = (i + 0.5f) / 7f
            val dx = -0.30f + frac * 0.80f
            val dz = 0.40f + ((i * 37) % 10) * 0.03f
            val dropPos = cam.project(dx, -0.85f, dz)
            drawCircle(color = Color(0xCCFFFFFF), radius = 3.2f, center = dropPos)
        }
    }

    // Window frame mullions
    drawPath(windowPath, color = Color(0xFF90A4AE), style = Stroke(width = 3f))
    drawLine(
        color = Color(0xFF90A4AE),
        start = cam.project(0.10f, -0.86f, 0.35f),
        end = cam.project(0.10f, -0.86f, 0.98f),
        strokeWidth = 2.2f
    )
}

private fun DrawScope.drawLivingRoomFurniture(
    cam: IsoCamera,
    tempColor: Color,
    animPhase: Float
) {
    // Soft area rug
    drawIsoPrism(
        cam, -0.55f, 0.55f, -0.45f, 0.55f, 0.0f, 0.02f,
        topColor = Color(0xFF31445E),
        leftColor = Color(0xFF243449),
        rightColor = Color(0xFF243449)
    )
    // Sofa base & backrest
    drawIsoPrism(
        cam, -0.55f, 0.35f, -0.68f, -0.22f, 0.02f, 0.24f,
        topColor = Color(0xFF4A6382),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF3F5570)
    )
    drawIsoPrism(
        cam, -0.55f, 0.35f, -0.68f, -0.52f, 0.24f, 0.50f,
        topColor = Color(0xFF587496),
        leftColor = Color(0xFF3B4F69),
        rightColor = Color(0xFF475E7B)
    )
    // Sofa armrests
    drawIsoPrism(
        cam, -0.62f, -0.48f, -0.68f, -0.20f, 0.02f, 0.36f,
        topColor = Color(0xFF526D8E),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF425975)
    )
    drawIsoPrism(
        cam, 0.28f, 0.42f, -0.68f, -0.20f, 0.02f, 0.36f,
        topColor = Color(0xFF526D8E),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF425975)
    )
    // Oak coffee table
    drawIsoPrism(
        cam, -0.35f, 0.22f, 0.02f, 0.38f, 0.02f, 0.19f,
        topColor = Color(0xFFB88655),
        leftColor = Color(0xFF8A613B),
        rightColor = Color(0xFF9E7146)
    )
    // Floor lamp with warm thermal aura
    drawIsoPrism(
        cam, 0.56f, 0.72f, -0.65f, -0.48f, 0.28f, 0.56f,
        topColor = Color(0xFFFFF3E0),
        leftColor = tempColor.copy(alpha = 0.85f),
        rightColor = tempColor.copy(alpha = 0.72f)
    )
    val lampGlowCenter = cam.project(0.64f, -0.56f, 0.44f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                tempColor.copy(alpha = 0.42f + 0.1f * sin(animPhase * 1.5f)),
                Color.Transparent
            ),
            center = lampGlowCenter,
            radius = cam.scale * 0.55f
        ),
        radius = cam.scale * 0.55f,
        center = lampGlowCenter
    )
    // Potted plant in front left corner
    drawIsoPrism(
        cam, -0.75f, -0.52f, 0.48f, 0.72f, 0.02f, 0.22f,
        topColor = Color(0xFFC97A57),
        leftColor = Color(0xFF9C5B3E),
        rightColor = Color(0xFFB36A4A)
    )
    val plantTop = cam.project(-0.63f, 0.60f, 0.36f)
    drawCircle(color = Color(0xFF2E7D32), radius = cam.scale * 0.14f, center = plantTop)
    drawCircle(
        color = Color(0xFF4CAF50),
        radius = cam.scale * 0.10f,
        center = Offset(plantTop.x + sin(animPhase) * 3f, plantTop.y - 6f)
    )
}

private fun DrawScope.drawBedroomFurniture(
    cam: IsoCamera,
    tempColor: Color,
    animPhase: Float
) {
    // Bed frame & mattress
    drawIsoPrism(
        cam, -0.65f, 0.35f, -0.78f, 0.18f, 0.02f, 0.24f,
        topColor = Color(0xFFD7CCC8),
        leftColor = Color(0xFF5D4037),
        rightColor = Color(0xFF6D4C41)
    )
    // Duvet cover tinted softly with room comfort color
    drawIsoPrism(
        cam, -0.63f, 0.33f, -0.35f, 0.16f, 0.24f, 0.30f,
        topColor = tempColor.copy(alpha = 0.65f),
        leftColor = tempColor.copy(alpha = 0.45f),
        rightColor = tempColor.copy(alpha = 0.55f)
    )
    // Headboard
    drawIsoPrism(
        cam, -0.65f, 0.35f, -0.84f, -0.74f, 0.02f, 0.60f,
        topColor = Color(0xFF6D4C41),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF5D4037)
    )
    // Two pillows
    drawIsoPrism(
        cam, -0.54f, -0.18f, -0.70f, -0.46f, 0.24f, 0.33f,
        topColor = Color(0xFFF5F7FA),
        leftColor = Color(0xFFCFD8DC),
        rightColor = Color(0xFFE2E8F0)
    )
    drawIsoPrism(
        cam, -0.08f, 0.26f, -0.70f, -0.46f, 0.24f, 0.33f,
        topColor = Color(0xFFF5F7FA),
        leftColor = Color(0xFFCFD8DC),
        rightColor = Color(0xFFE2E8F0)
    )
    // Nightstand + warm bedside lamp
    drawIsoPrism(
        cam, 0.46f, 0.74f, -0.78f, -0.50f, 0.02f, 0.28f,
        topColor = Color(0xFF8D6E63),
        leftColor = Color(0xFF5D4037),
        rightColor = Color(0xFF6D4C41)
    )
    val lampCenter = cam.project(0.60f, -0.64f, 0.42f)
    drawCircle(
        color = Color(0xFFFFD54F).copy(alpha = 0.85f + 0.1f * sin(animPhase)),
        radius = cam.scale * 0.09f,
        center = lampCenter
    )
}

private fun DrawScope.drawStudyFurniture(
    cam: IsoCamera,
    tempColor: Color,
    animPhase: Float
) {
    // Desk surface
    drawIsoPrism(
        cam, -0.55f, 0.48f, -0.75f, -0.25f, 0.26f, 0.32f,
        topColor = Color(0xFFA1887F),
        leftColor = Color(0xFF6D4C41),
        rightColor = Color(0xFF795548)
    )
    // Dual glowing monitors
    drawIsoPrism(
        cam, -0.42f, -0.04f, -0.66f, -0.58f, 0.36f, 0.64f,
        topColor = Color(0xFF90CAF9),
        leftColor = Color(0xFF1E88E5),
        rightColor = Color(0xFF42A5F5)
    )
    drawIsoPrism(
        cam, 0.02f, 0.38f, -0.66f, -0.58f, 0.36f, 0.64f,
        topColor = Color(0xFF80DEEA),
        leftColor = tempColor.copy(alpha = 0.85f),
        rightColor = Color(0xFF26C6DA)
    )
    // Office chair
    drawIsoPrism(
        cam, -0.18f, 0.18f, -0.10f, 0.24f, 0.06f, 0.42f,
        topColor = Color(0xFF455A64),
        leftColor = Color(0xFF263238),
        rightColor = Color(0xFF37474F)
    )
}

private fun DrawScope.drawKitchenFurniture(
    cam: IsoCamera,
    tempColor: Color,
    animPhase: Float
) {
    // Back counter
    drawIsoPrism(
        cam, -0.78f, 0.72f, -0.82f, -0.40f, 0.02f, 0.38f,
        topColor = Color(0xFFECEFF1),
        leftColor = Color(0xFF455A64),
        rightColor = Color(0xFF546E7A)
    )
    // Kitchen island
    drawIsoPrism(
        cam, -0.45f, 0.48f, -0.05f, 0.42f, 0.02f, 0.36f,
        topColor = Color(0xFFCFD8DC),
        leftColor = Color(0xFF37474F),
        rightColor = Color(0xFF455A64)
    )
    // Warm kettle / hob glow on island
    val hobCenter = cam.project(0.0f, 0.18f, 0.40f)
    drawCircle(
        color = tempColor.copy(alpha = 0.75f + 0.15f * sin(animPhase * 2f)),
        radius = cam.scale * 0.10f,
        center = hobCenter
    )
}

private fun DrawScope.drawBathroomFurniture(
    cam: IsoCamera,
    humColor: Color,
    animPhase: Float
) {
    // Bathtub with water surface
    drawIsoPrism(
        cam, -0.72f, 0.18f, -0.75f, -0.20f, 0.02f, 0.32f,
        topColor = Color(0xFF4DD0E1).copy(alpha = 0.75f),
        leftColor = Color(0xFFCFD8DC),
        rightColor = Color(0xFFE0E6ED)
    )
    // Vanity unit & sink
    drawIsoPrism(
        cam, 0.32f, 0.78f, -0.75f, -0.28f, 0.02f, 0.36f,
        topColor = Color(0xFFF5F7FA),
        leftColor = humColor.copy(alpha = 0.65f),
        rightColor = Color(0xFF90A4AE)
    )
}

private fun DrawScope.drawBalconyGardenScene(
    cam: IsoCamera,
    isDaytime: Boolean,
    animPhase: Float
) {
    // Wooden planter box
    drawIsoPrism(
        cam, -0.75f, 0.30f, -0.80f, -0.42f, 0.02f, 0.25f,
        topColor = Color(0xFF5D4037),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF6D4C41)
    )
    // Swaying trees & garden foliage spheres
    val sway = sin(animPhase * 1.4f) * 0.04f
    val tree1 = cam.project(-0.48f + sway, -0.60f, 0.56f)
    val tree2 = cam.project(-0.08f - sway, -0.58f, 0.48f)
    drawCircle(color = Color(0xFF2E7D32), radius = cam.scale * 0.21f, center = tree1)
    drawCircle(color = Color(0xFF43A047), radius = cam.scale * 0.16f, center = Offset(tree1.x - 6f, tree1.y - 8f))
    drawCircle(color = Color(0xFF388E3C), radius = cam.scale * 0.17f, center = tree2)

    // Sun or Moon orb in upper sky
    val orbPos = cam.project(0.55f, -0.75f, 0.92f)
    val orbColor = if (isDaytime) Color(0xFFFFD54F) else Color(0xFFB3E5FC)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(orbColor.copy(alpha = 0.65f), Color.Transparent),
            center = orbPos,
            radius = cam.scale * 0.34f
        ),
        radius = cam.scale * 0.34f,
        center = orbPos
    )
    drawCircle(color = orbColor, radius = cam.scale * 0.11f, center = orbPos)
}

private fun DrawScope.drawEnvironmentalParticles(
    cam: IsoCamera,
    room: SensorRoomState,
    tempColor: Color,
    humColor: Color,
    animPhase: Float,
    particleProgress: Float
) {
    // 1. Water vapour micro-droplets scaled by relative humidity (more droplets when humid)
    val dropletCount = ((room.humidityPct - 20f) / 3.2f).toInt().coerceIn(5, 26)
    for (i in 0 until dropletCount) {
        val seed = i * 1.618034f
        val px = ((seed * 3.7f) % 1.6f) - 0.8f + sin(animPhase + seed) * 0.06f
        val py = ((seed * 2.3f) % 1.6f) - 0.8f + cos(animPhase * 0.8f + seed) * 0.06f
        val pz = ((particleProgress + (seed % 1f)) % 1f) * 0.95f + 0.08f
        val screenPt = cam.project(px, py, pz)
        val alpha = sin(((pz / 1.03f).coerceIn(0f, 1f)) * Math.PI.toFloat()) * 0.65f
        drawCircle(
            color = humColor.copy(alpha = alpha.coerceIn(0.1f, 0.75f)),
            radius = (2.5f + (room.humidityPct / 35f)) * (cam.scale / 115f),
            center = screenPt
        )
    }

    // 2. Thermal convection wave lines rising from floor
    for (i in -2..2) {
        val baseX = i * 0.32f
        val waveZ0 = 0.08f + ((particleProgress + (i + 2) * 0.2f) % 0.65f)
        val waveZ1 = waveZ0 + 0.18f
        val pStart = cam.project(baseX + sin(animPhase + i) * 0.05f, 0.1f, waveZ0)
        val pEnd = cam.project(baseX - sin(animPhase + i) * 0.05f, 0.1f, waveZ1)
        drawLine(
            color = tempColor.copy(alpha = 0.38f),
            start = pStart,
            end = pEnd,
            strokeWidth = 2.6f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawIsoPrism(
    cam: IsoCamera,
    x0: Float,
    x1: Float,
    y0: Float,
    y1: Float,
    z0: Float,
    z1: Float,
    topColor: Color,
    leftColor: Color,
    rightColor: Color
) {
    val t00 = cam.project(x0, y0, z1)
    val t10 = cam.project(x1, y0, z1)
    val t11 = cam.project(x1, y1, z1)
    val t01 = cam.project(x0, y1, z1)

    val b10 = cam.project(x1, y0, z0)
    val b11 = cam.project(x1, y1, z0)
    val b01 = cam.project(x0, y1, z0)

    // Left front face (y = y1, x0..x1)
    val leftFace = Path().apply {
        moveTo(t01.x, t01.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b01.x, b01.y)
        close()
    }
    drawPath(leftFace, color = leftColor)

    // Right front face (x = x1, y0..y1)
    val rightFace = Path().apply {
        moveTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b10.x, b10.y)
        close()
    }
    drawPath(rightFace, color = rightColor)

    // Top face (z = z1)
    val topFace = Path().apply {
        moveTo(t00.x, t00.y)
        lineTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(t01.x, t01.y)
        close()
    }
    drawPath(topFace, color = topColor)
}
