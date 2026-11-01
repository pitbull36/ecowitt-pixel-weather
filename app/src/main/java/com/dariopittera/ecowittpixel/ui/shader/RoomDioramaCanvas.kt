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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariopittera.ecowittpixel.model.MouldRiskLevel
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.TempUnit
import com.dariopittera.ecowittpixel.ui.theme.humidityAccentColor
import com.dariopittera.ecowittpixel.ui.theme.temperatureAccentColor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val ATMOSPHERE_AGSL_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float tempNorm;
    uniform float humidityNorm;
    uniform float tiltX;
    uniform float tiltY;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 p = uv - float2(0.5 + tiltX * 0.08, 0.48 + tiltY * 0.08);
        float dist = length(p);

        float wave1 = sin(uv.x * 9.0 + time * 1.4 + uv.y * 4.0) * 0.5 + 0.5;
        float wave2 = cos(uv.y * 11.0 - time * 1.1 + uv.x * 6.0) * 0.5 + 0.5;
        float mist = wave1 * wave2 * humidityNorm;

        half3 coldCol = half3(0.10, 0.22, 0.38);
        half3 comfortCol = half3(0.09, 0.28, 0.36);
        half3 warmCol = half3(0.38, 0.22, 0.14);

        half3 baseTone = mix(coldCol, comfortCol, smoothstep(0.0, 0.55, tempNorm));
        baseTone = mix(baseTone, warmCol, smoothstep(0.5, 1.0, tempNorm));

        float radialGlow = smoothstep(0.76, 0.05, dist);
        half3 bg = half3(0.04, 0.07, 0.13) + baseTone * radialGlow * 0.65;
        bg += half3(0.16, 0.30, 0.42) * mist * 0.22 * radialGlow;

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
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val rx = x * cosY - y * sinY
        val ry = x * sinY + y * cosY
        val sx = centerX + rx * scale
        val sy = centerY + (ry * sin(pitchRad) - z * cos(pitchRad)) * scale
        return Offset(sx, sy)
    }
}

private enum class StackGestureMode {
    MOVE_ROOM,
    TILT_ROOM
}

private data class RoomSpatialPose(
    val xNorm: Float,
    val yNorm: Float,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f
)

private fun defaultRoomPose(idx: Int, totalCount: Int): RoomSpatialPose {
    val count = totalCount.coerceAtLeast(1)
    val yNorm = if (count > 1) 0.22f + idx * (0.60f / (count - 1).toFloat()) else 0.52f
    val xNorm = 0.34f + (idx % 2) * 0.10f
    return RoomSpatialPose(xNorm = xNorm, yNorm = yNorm, yawOffset = 0f, pitchOffset = 0f)
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
    val prefs = remember {
        context.getSharedPreferences("ecowitt_room_spatial_poses", Context.MODE_PRIVATE)
    }

    var gyroTiltX by remember { mutableFloatStateOf(0f) }
    var gyroTiltY by remember { mutableFloatStateOf(0f) }
    var userDragYaw by remember { mutableFloatStateOf(0f) }
    var userDragPitch by remember { mutableFloatStateOf(0f) }

    var stackGestureMode by remember { mutableStateOf(StackGestureMode.MOVE_ROOM) }
    var activeDraggedChannelId by remember { mutableStateOf<String?>(null) }

    // Per-room spatial position (drag & drop) and individual 3D tilt (yaw & pitch)
    val roomPoses = remember { mutableStateMapOf<String, RoomSpatialPose>() }

    // Populate initial poses from SharedPreferences or defaults
    allRooms.forEachIndexed { idx, r ->
        if (!roomPoses.containsKey(r.channelId)) {
            val saved = prefs.getString("pose_${r.channelId}", null)
            val parsed = saved?.split(",")?.mapNotNull { it.toFloatOrNull() }
            roomPoses[r.channelId] = if (parsed != null && parsed.size == 4) {
                RoomSpatialPose(parsed[0], parsed[1], parsed[2], parsed[3])
            } else {
                defaultRoomPose(idx, allRooms.size)
            }
        }
    }

    fun savePoseToPrefs(channelId: String, pose: RoomSpatialPose) {
        prefs.edit()
            .putString(
                "pose_$channelId",
                "${pose.xNorm},${pose.yNorm},${pose.yawOffset},${pose.pitchOffset}"
            )
            .apply()
    }

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

    val tempNorm = ((room.temperatureC - 12f) / 18f).coerceIn(0f, 1f)
    val humidityNorm = ((room.humidityPct - 25f) / 65f).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(wholeHomeOverview, allRooms.size) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (wholeHomeOverview) {
                                allRooms.forEachIndexed { idx, r ->
                                    val defPose = defaultRoomPose(idx, allRooms.size)
                                    roomPoses[r.channelId] = defPose
                                    savePoseToPrefs(r.channelId, defPose)
                                }
                            } else {
                                userDragYaw = 0f
                                userDragPitch = 0f
                            }
                        },
                        onTap = { tapOffset ->
                            if (wholeHomeOverview && allRooms.isNotEmpty()) {
                                val nearestIdx = allRooms.indices.minByOrNull { idx ->
                                    val r = allRooms[idx]
                                    val pose = roomPoses[r.channelId] ?: defaultRoomPose(idx, allRooms.size)
                                    hypot(
                                        tapOffset.x - pose.xNorm * size.width,
                                        tapOffset.y - pose.yNorm * size.height
                                    )
                                } ?: 0
                                onSelectRoomIndex(nearestIdx)
                            }
                        }
                    )
                }
                .pointerInput(wholeHomeOverview, stackGestureMode, allRooms.size) {
                    detectDragGestures(
                        onDragStart = { downOffset ->
                            if (wholeHomeOverview && allRooms.isNotEmpty()) {
                                val nearestIdx = allRooms.indices.minByOrNull { idx ->
                                    val r = allRooms[idx]
                                    val pose = roomPoses[r.channelId] ?: defaultRoomPose(idx, allRooms.size)
                                    hypot(
                                        downOffset.x - pose.xNorm * size.width,
                                        downOffset.y - pose.yNorm * size.height
                                    )
                                } ?: 0
                                activeDraggedChannelId = allRooms[nearestIdx].channelId
                                onSelectRoomIndex(nearestIdx)
                            } else {
                                activeDraggedChannelId = null
                            }
                        },
                        onDragEnd = {
                            activeDraggedChannelId?.let { chId ->
                                roomPoses[chId]?.let { savePoseToPrefs(chId, it) }
                            }
                            activeDraggedChannelId = null
                        },
                        onDragCancel = {
                            activeDraggedChannelId = null
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        if (wholeHomeOverview && allRooms.isNotEmpty()) {
                            val chId = activeDraggedChannelId ?: room.channelId
                            val currentIdx = allRooms.indexOfFirst { it.channelId == chId }.coerceAtLeast(0)
                            val curPose = roomPoses[chId] ?: defaultRoomPose(currentIdx, allRooms.size)
                            if (stackGestureMode == StackGestureMode.MOVE_ROOM) {
                                val nextX = (curPose.xNorm + dragAmount.x / size.width.toFloat().coerceAtLeast(1f))
                                    .coerceIn(0.18f, 0.82f)
                                val nextY = (curPose.yNorm + dragAmount.y / size.height.toFloat().coerceAtLeast(1f))
                                    .coerceIn(0.16f, 0.86f)
                                roomPoses[chId] = curPose.copy(xNorm = nextX, yNorm = nextY)
                            } else {
                                val nextYaw = (curPose.yawOffset + dragAmount.x * 0.0055f)
                                    .coerceIn(-0.65f, 0.65f)
                                val nextPitch = (curPose.pitchOffset + dragAmount.y * 0.0040f)
                                    .coerceIn(-0.24f, 0.24f)
                                roomPoses[chId] = curPose.copy(yawOffset = nextYaw, pitchOffset = nextPitch)
                            }
                        } else {
                            userDragYaw = (userDragYaw + dragAmount.x * 0.0045f).coerceIn(-0.55f, 0.55f)
                            userDragPitch = (userDragPitch + dragAmount.y * 0.003f).coerceIn(-0.22f, 0.22f)
                        }
                    }
                }
        ) {
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
                            temperatureAccentColor(room.temperatureC).copy(alpha = 0.20f),
                            Color(0xFF0A121E)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.48f),
                        radius = size.minDimension * 0.72f
                    ),
                    size = size
                )
            }

            val baseYaw = 0.785398f + gyroTiltX * 0.28f + userDragYaw
            val basePitch = (0.54f + gyroTiltY * 0.18f + userDragPitch).coerceIn(0.32f, 0.78f)

            if (wholeHomeOverview && allRooms.isNotEmpty()) {
                drawWholeHomeIsoStack(
                    rooms = allRooms,
                    roomPoses = roomPoses,
                    selectedChannelId = room.channelId,
                    baseYaw = baseYaw,
                    basePitch = basePitch,
                    animPhase = animPhase,
                    particleProgress = particleProgress,
                    timeOfDayHours = timeOfDayHours,
                    tempUnit = tempUnit,
                    textMeasurer = textMeasurer
                )
            } else {
                val scale = size.minDimension * 0.35f
                val cam = IsoCamera(
                    centerX = size.width * 0.47f,
                    centerY = size.height * 0.63f,
                    scale = scale,
                    yawRad = baseYaw,
                    pitchRad = basePitch
                )
                drawSingleRoomDiorama(
                    cam = cam,
                    room = room,
                    animPhase = animPhase,
                    particleProgress = particleProgress,
                    timeOfDayHours = timeOfDayHours,
                    tempUnit = tempUnit,
                    textMeasurer = textMeasurer,
                    compactMode = false
                )
            }
        }

        if (wholeHomeOverview && allRooms.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color(0xD90B1424), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isMove = stackGestureMode == StackGestureMode.MOVE_ROOM
                Text(
                    text = "Move rooms",
                    color = if (isMove) Color(0xFF0A121E) else Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            if (isMove) Color(0xFF38BDF8) else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { stackGestureMode = StackGestureMode.MOVE_ROOM }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
                Text(
                    text = "Tilt room",
                    color = if (!isMove) Color(0xFF0A121E) else Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            if (!isMove) Color(0xFFFFB74D) else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { stackGestureMode = StackGestureMode.TILT_ROOM }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
                Text(
                    text = "Reset",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            allRooms.forEachIndexed { idx, r ->
                                val defPose = defaultRoomPose(idx, allRooms.size)
                                roomPoses[r.channelId] = defPose
                                savePoseToPrefs(r.channelId, defPose)
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawWholeHomeIsoStack(
    rooms: List<SensorRoomState>,
    roomPoses: Map<String, RoomSpatialPose>,
    selectedChannelId: String,
    baseYaw: Float,
    basePitch: Float,
    animPhase: Float,
    particleProgress: Float,
    timeOfDayHours: Float,
    tempUnit: TempUnit,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val count = rooms.size.coerceAtLeast(1)
    val scale = size.minDimension * 0.22f

    rooms.forEachIndexed { idx, r ->
        val pose = roomPoses[r.channelId] ?: defaultRoomPose(idx, count)
        val cx = pose.xNorm * size.width
        val cy = pose.yNorm * size.height
        val roomYaw = (baseYaw + pose.yawOffset).coerceIn(0.18f, 1.40f)
        val roomPitch = (basePitch + pose.pitchOffset).coerceIn(0.28f, 0.80f)
        val isSelected = r.channelId == selectedChannelId
        val cam = IsoCamera(
            centerX = cx,
            centerY = cy,
            scale = if (isSelected) scale * 1.06f else scale * 0.95f,
            yawRad = roomYaw,
            pitchRad = roomPitch
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

        // Floating badge anchored directly to each room's spatial position
        val badgeColor = temperatureAccentColor(r.temperatureC)
        val roomTopAnchor = cam.project(0.82f, -0.72f, 1.06f)
        val labelResult = textMeasurer.measure(
            text = "${r.roomName.substringBefore(" (")}  ${tempUnit.format(r.temperatureC)} • ${r.humidityPct.toInt()}%",
            style = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            )
        )
        val boxW = labelResult.size.width + 42f
        val boxH = labelResult.size.height + 14f
        val boxX = (roomTopAnchor.x - boxW * 0.35f).coerceIn(10f, size.width - boxW - 10f)
        val boxY = (roomTopAnchor.y - boxH - 4f).coerceIn(10f, size.height - boxH - 10f)

        drawLine(
            color = badgeColor.copy(alpha = if (isSelected) 0.85f else 0.45f),
            start = cam.project(0.83f, -0.77f, 0.98f),
            end = Offset(boxX + boxW * 0.35f, boxY + boxH),
            strokeWidth = if (isSelected) 2.6f else 1.6f
        )

        drawRoundRect(
            color = Color(0xD90E192B),
            topLeft = Offset(boxX, boxY),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = badgeColor.copy(alpha = if (isSelected) 0.95f else 0.55f),
            topLeft = Offset(boxX, boxY),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = if (isSelected) 2.4f else 1.4f)
        )

        drawThermometerIcon(
            centerX = boxX + 15f,
            centerY = boxY + boxH / 2f,
            iconHeight = boxH * 1.05f,
            thermometerColor = badgeColor,
            temperatureC = r.temperatureC,
            animPhase = animPhase
        )

        drawText(
            labelResult,
            topLeft = Offset(boxX + 29f, boxY + 7f)
        )
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

    // 1. Floor slab
    val floorTopColor = if (room.archetype.isOutdoor) Color(0xFF284238) else Color(0xFF253248)
    drawIsoPrism(
        cam = cam,
        x0 = -1f, x1 = 1f,
        y0 = -1f, y1 = 1f,
        z0 = -0.14f, z1 = 0f,
        topColor = floorTopColor,
        leftColor = Color(0xFF162030),
        rightColor = Color(0xFF1D293D)
    )

    // Subtle floor grid tiles
    for (i in -3..3) {
        val t = i / 4f
        drawLine(
            color = Color.White.copy(alpha = 0.05f),
            start = cam.project(t, -0.96f, 0.005f),
            end = cam.project(t, 0.96f, 0.005f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.05f),
            start = cam.project(-0.96f, t, 0.005f),
            end = cam.project(0.96f, t, 0.005f),
            strokeWidth = 1.2f
        )
    }

    // 2. Back cutaway walls
    val wallHeight = if (compactMode) 1.05f else 1.22f
    if (!room.archetype.isOutdoor) {
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = -0.88f,
            y0 = -1f, y1 = 1f,
            z0 = 0f, z1 = wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF1E2B40),
            rightColor = Color(0xFF2B3B55)
        )
        drawIsoPrism(
            cam = cam,
            x0 = -1f, x1 = 1f,
            y0 = -1f, y1 = -0.88f,
            z0 = 0f, z1 = wallHeight,
            topColor = Color(0xFF394B68),
            leftColor = Color(0xFF2E3F5B),
            rightColor = Color(0xFF223149)
        )

        if (room.archetype != RoomArchetype.LIVING_ROOM) {
            drawIsoWindow(cam = cam, isDaytime = isDaytime, room = room)
        } else {
            drawLivingRoomSideWindow(cam = cam, isDaytime = isDaytime, room = room)
        }
    } else {
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

    // 3. Archetype-specific 3D furniture
    when (room.archetype) {
        RoomArchetype.LIVING_ROOM -> drawLivingRoomFurniture(cam)
        RoomArchetype.BEDROOM -> drawBedroomFurniture(cam, tempColor, animPhase)
        RoomArchetype.STUDY -> drawStudyFurniture(cam, tempColor)
        RoomArchetype.KITCHEN -> drawKitchenFurniture(cam, tempColor, animPhase)
        RoomArchetype.BATHROOM -> drawBathroomFurniture(cam, humColor)
        RoomArchetype.BALCONY_GARDEN -> drawBalconyGardenScene(cam, isDaytime, animPhase)
    }

    // 3b. Room-specific cold / freeze visualisation when temperature < 19.0°C
    if (room.temperatureC < 19.0f) {
        drawRoomFreezeEffects(cam = cam, room = room, animPhase = animPhase)
    }

    // 4. Tall 3D glass thermometer column on the front-right wall corner (as in the first preview)
    val maxPillarZ = if (compactMode) 0.88f else 0.98f
    val isLavaMode = room.temperatureC >= 30.0f
    drawIsoPrism(
        cam = cam,
        x0 = 0.76f, x1 = 0.90f,
        y0 = -0.84f, y1 = -0.70f,
        z0 = 0.10f, z1 = maxPillarZ,
        topColor = Color(0x55B0BEC5),
        leftColor = Color(0x38CFD8DC),
        rightColor = Color(0x4890A4AE)
    )
    val fillNorm = ((room.temperatureC - 10f) / 22f).coerceIn(0.10f, 1f)
    val mercuryTop = 0.14f + fillNorm * (maxPillarZ - 0.18f)
    val columnBaseColor = if (isLavaMode) Color(0xFFB71C1C) else tempColor
    drawIsoPrism(
        cam = cam,
        x0 = 0.79f, x1 = 0.87f,
        y0 = -0.81f, y1 = -0.73f,
        z0 = 0.12f, z1 = mercuryTop,
        topColor = if (isLavaMode) Color(0xFFFF6D00) else columnBaseColor,
        leftColor = columnBaseColor.copy(alpha = 0.92f),
        rightColor = columnBaseColor.copy(alpha = 0.84f)
    )

    // Molten lava lamp blobs moving inside the 3D thermometer when >= 30.0°C
    if (isLavaMode) {
        val lavaSpan = (mercuryTop - 0.16f).coerceAtLeast(0.25f)
        for (b in 0 until 6) {
            val blobProgress = (particleProgress * 1.35f + b * 0.17f) % 1f
            // Lava blobs rise and slowly sink back in a continuous convection loop inside the glass tube
            val cycleZ = if (b % 3 == 2) (1f - blobProgress) else blobProgress
            val bz = 0.15f + cycleZ * lavaSpan
            val wobble = sin(animPhase * 2.4f + b * 1.3f) * 0.018f
            val blobPt = cam.project(0.83f + wobble, -0.77f - wobble, bz)
            val pulse = 0.82f + 0.28f * sin(animPhase * 3.1f + b * 1.7f)
            val blobRadius = cam.scale * 0.042f * pulse
            val blobColor = when (b % 3) {
                0 -> Color(0xFFFFEA00) // Incandescent molten gold
                1 -> Color(0xFFFF6D00) // Bright magma orange
                else -> Color(0xFFFF1744) // Volcanic scarlet
            }
            drawOval(
                color = blobColor.copy(alpha = 0.92f),
                topLeft = Offset(blobPt.x - blobRadius * 0.85f, blobPt.y - blobRadius * 1.25f),
                size = Size(blobRadius * 1.7f, blobRadius * 2.5f)
            )
        }
    }

    val bulbPt = cam.project(0.83f, -0.77f, 0.12f)
    val bulbRadius = cam.scale * 0.075f
    if (isLavaMode) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFEA00),
                    Color(0xFFFF6D00),
                    Color(0xFFD50000)
                ),
                center = Offset(
                    bulbPt.x + sin(animPhase * 2.5f) * 2.5f,
                    bulbPt.y + cos(animPhase * 2.2f) * 2.5f
                ),
                radius = bulbRadius * 1.1f
            ),
            radius = bulbRadius,
            center = bulbPt
        )
    } else {
        drawCircle(
            color = tempColor,
            radius = bulbRadius,
            center = bulbPt
        )
    }
    drawCircle(
        color = Color(0xCCCFD8DC),
        radius = bulbRadius,
        center = bulbPt,
        style = Stroke(width = 1.6f)
    )

    // 5. Humidity water vapour droplets animation (no thermal wave lines!)
    drawHumidityDroplets(
        cam = cam,
        room = room,
        humColor = humColor,
        animPhase = animPhase,
        particleProgress = particleProgress
    )

    // 6. Floating pill badge above the tall 3D thermometer in single-room view
    if (!compactMode) {
        val anchor = cam.project(0.85f, -0.75f, 1.06f)
        val badgeText = "${tempUnit.format(room.temperatureC)}  •  ${room.humidityPct.toInt()}% RH"
        val measured = textMeasurer.measure(
            text = badgeText,
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        val pillW = measured.size.width + 28f
        val pillH = measured.size.height + 16f
        val bx = (anchor.x - pillW / 2f).coerceIn(16f, size.width - pillW - 16f)
        val by = (anchor.y - pillH / 2f).coerceIn(14f, size.height - pillH - 14f)

        drawRoundRect(
            color = Color(0xE60D1829),
            topLeft = Offset(bx, by),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(20f, 20f)
        )
        drawRoundRect(
            color = tempColor,
            topLeft = Offset(bx, by),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(20f, 20f),
            style = Stroke(width = 2.4f)
        )

        drawText(measured, topLeft = Offset(bx + 14f, by + 8f))
    }
}

/**
 * Draws a tall, slender vector thermometer icon whose liquid level and bulb are coloured
 * white (<19°C), yellow (19–22.9°C), orange (23–25.9°C), red (26–29.9°C), or animated molten lava (>=30°C).
 */
private fun DrawScope.drawThermometerIcon(
    centerX: Float,
    centerY: Float,
    iconHeight: Float,
    thermometerColor: Color,
    temperatureC: Float,
    animPhase: Float = 0f
) {
    val isLavaMode = temperatureC >= 30.0f
    val tubeW = iconHeight * 0.20f
    val tubeH = iconHeight * 0.82f
    val bulbR = iconHeight * 0.19f
    val topY = centerY - iconHeight * 0.50f
    val bulbCenterY = topY + tubeH

    // Glass tube outline
    drawRoundRect(
        color = Color(0xFF0A121E),
        topLeft = Offset(centerX - tubeW / 2f, topY),
        size = Size(tubeW, tubeH),
        cornerRadius = CornerRadius(tubeW / 2f, tubeW / 2f)
    )
    drawRoundRect(
        color = Color(0xFFCFD8DC),
        topLeft = Offset(centerX - tubeW / 2f, topY),
        size = Size(tubeW, tubeH),
        cornerRadius = CornerRadius(tubeW / 2f, tubeW / 2f),
        style = Stroke(width = 2f)
    )

    // Mercury fill proportional to temperature
    val fillNorm = ((temperatureC - 12f) / 18f).coerceIn(0.18f, 1f)
    val fillH = (tubeH - 4f) * fillNorm
    val innerW = (tubeW - 4f).coerceAtLeast(3f)
    drawRoundRect(
        color = if (isLavaMode) Color(0xFFB71C1C) else thermometerColor,
        topLeft = Offset(centerX - innerW / 2f, bulbCenterY - fillH),
        size = Size(innerW, fillH + 3f),
        cornerRadius = CornerRadius(innerW / 2f, innerW / 2f)
    )

    if (isLavaMode) {
        for (b in 0 until 3) {
            val t = ((animPhase * 0.45f + b * 0.33f) % 1f)
            val by = bulbCenterY - t * (fillH - 4f)
            val blobColor = if (b % 2 == 0) Color(0xFFFFEA00) else Color(0xFFFF6D00)
            drawOval(
                color = blobColor,
                topLeft = Offset(centerX - innerW * 0.42f, by - innerW * 0.65f),
                size = Size(innerW * 0.84f, innerW * 1.3f)
            )
        }
    }

    // Thermometer bottom bulb
    drawCircle(
        color = if (isLavaMode) Color(0xFFD50000) else thermometerColor,
        radius = bulbR,
        center = Offset(centerX, bulbCenterY + 2f)
    )
    if (isLavaMode) {
        drawCircle(
            color = Color(0xFFFFEA00),
            radius = bulbR * (0.48f + 0.12f * sin(animPhase * 3f)),
            center = Offset(centerX, bulbCenterY + 2f)
        )
    }
    drawCircle(
        color = Color(0xFFCFD8DC),
        radius = bulbR,
        center = Offset(centerX, bulbCenterY + 2f),
        style = Stroke(width = 1.8f)
    )
}

private fun DrawScope.drawIsoWindow(
    cam: IsoCamera,
    isDaytime: Boolean,
    room: SensorRoomState
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

    if (room.temperatureC < 19.0f) {
        // Crystalline ice glaze over the window when < 19.0°C
        drawPath(path = windowPath, color = Color(0x80B3E5FC))
        // Diagonal frost fern lines in window corners
        drawLine(
            color = Color(0xEEFFFFFF),
            start = cam.project(-0.33f, -0.85f, 0.38f),
            end = cam.project(-0.14f, -0.85f, 0.56f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xEEFFFFFF),
            start = cam.project(0.52f, -0.85f, 0.38f),
            end = cam.project(0.32f, -0.85f, 0.56f),
            strokeWidth = 2f
        )
        // Row of glistening icicles hanging from top window frame
        for (i in 0..6) {
            val ix = -0.30f + i * 0.135f
            val len = if (i % 2 == 0) 0.17f else 0.11f
            drawIcicle3D(cam, ix, -0.85f, 0.98f, len, 0.032f)
        }
    } else if (room.mouldRisk != MouldRiskLevel.LOW || room.humidityPct >= 62f) {
        drawPath(path = windowPath, color = Color(0x66B2EBF2))
        for (i in 0..6) {
            val frac = (i + 0.5f) / 7f
            val dx = -0.30f + frac * 0.80f
            val dz = 0.40f + ((i * 37) % 10) * 0.03f
            val dropPos = cam.project(dx, -0.85f, dz)
            drawCircle(color = Color(0xCCFFFFFF), radius = 3.2f, center = dropPos)
        }
    }

    drawPath(windowPath, color = Color(0xFF90A4AE), style = Stroke(width = 3f))
    drawLine(
        color = Color(0xFF90A4AE),
        start = cam.project(0.10f, -0.86f, 0.35f),
        end = cam.project(0.10f, -0.86f, 0.98f),
        strokeWidth = 2.2f
    )
}

/**
 * Panoramic side window on the left wall of the Living Room (beside the library bookcase).
 * Freezes over with crystalline frost and hanging icicles when temperature < 19.0°C.
 */
private fun DrawScope.drawLivingRoomSideWindow(
    cam: IsoCamera,
    isDaytime: Boolean,
    room: SensorRoomState
) {
    val p1 = cam.project(-0.86f, 0.36f, 0.36f)
    val p2 = cam.project(-0.86f, 0.90f, 0.36f)
    val p3 = cam.project(-0.86f, 0.90f, 0.96f)
    val p4 = cam.project(-0.86f, 0.36f, 0.96f)

    val windowPath = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }
    val skyColor = if (isDaytime) Color(0xFF3876A8) else Color(0xFF101D36)
    drawPath(windowPath, color = skyColor)

    if (room.temperatureC < 19.0f) {
        drawPath(path = windowPath, color = Color(0x88B3E5FC))
        // Frost veins in the window corners
        drawLine(
            color = Color(0xEEFFFFFF),
            start = cam.project(-0.85f, 0.39f, 0.39f),
            end = cam.project(-0.85f, 0.56f, 0.58f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xEEFFFFFF),
            start = cam.project(-0.85f, 0.87f, 0.39f),
            end = cam.project(-0.85f, 0.70f, 0.58f),
            strokeWidth = 2f
        )
        // Icicles hanging down from the top frame of the living room window
        for (i in 0..4) {
            val iy = 0.40f + i * 0.11f
            val len = if (i % 2 == 0) 0.17f else 0.11f
            drawIcicleOnLeftWall3D(cam, -0.85f, iy, 0.96f, len, 0.032f)
        }
    }

    drawPath(windowPath, color = Color(0xFF90A4AE), style = Stroke(width = 2.8f))
}

/**
 * Room-specific freeze / frost visualisations when temperature < 19.0°C:
 * - Living room: icicles along library bookcase & TV bezel, frost crystals on TV screen & coffee table
 * - Bedroom: icicles on headboard, frosted ice-blue quilt with snow crystals, chilly breath mist
 * - Study: icicles hanging from dual monitors, ice-glazed desk surface
 * - Kitchen: icicles along counter & island edges, frozen ice sheet over induction hob
 * - Bathroom: frozen solid ice-crusted bathtub water with fracture lines, icicles on vanity
 * - Balcony / Garden: snow-capped trees & planter, icicles along the glass railing, drifting snowflakes
 */
private fun DrawScope.drawRoomFreezeEffects(
    cam: IsoCamera,
    room: SensorRoomState,
    animPhase: Float
) {
    val scaleFactor = (cam.scale / 115f).coerceAtLeast(0.55f)
    when (room.archetype) {
        RoomArchetype.LIVING_ROOM -> {
            // Icicles hanging from the top of the widescreen TV bezel
            for (i in 0..5) {
                val ix = -0.24f + i * 0.13f
                val len = if (i % 2 == 0) 0.14f else 0.09f
                drawIcicle3D(cam, ix, -0.74f, 0.84f, len, 0.028f)
            }
            // Icicles hanging along the top cornice of the library bookcase
            for (i in 0..4) {
                val iy = -0.70f + i * 0.22f
                val len = if (i % 2 == 0) 0.15f else 0.10f
                drawIcicleOnLeftWall3D(cam, -0.63f, iy, 1.02f, len, 0.030f)
            }
            // Frost glaze & six-point ice crystals on the TV screen and coffee table
            drawSixPointIceCrystal(cam.project(0.10f, -0.72f, 0.56f), 8.5f * scaleFactor)
            drawSixPointIceCrystal(cam.project(0.06f, -0.18f, 0.18f), 7.5f * scaleFactor)
        }
        RoomArchetype.BEDROOM -> {
            // Glacial frost glaze over the bed duvet + snow crystals
            drawIsoPrism(
                cam, -0.63f, 0.33f, -0.35f, 0.16f, 0.245f, 0.31f,
                topColor = Color(0xCCB3E5FC),
                leftColor = Color(0x9981D4FA),
                rightColor = Color(0xAAE1F5FE)
            )
            // Icicles hanging from the headboard
            for (i in 0..5) {
                val ix = -0.58f + i * 0.16f
                val len = if (i % 2 == 0) 0.14f else 0.09f
                drawIcicle3D(cam, ix, -0.73f, 0.60f, len, 0.028f)
            }
            drawSixPointIceCrystal(cam.project(-0.15f, -0.10f, 0.33f), 8.5f * scaleFactor)
            // Chilly breath mist cloud hovering above the pillows
            val breathCenter = cam.project(-0.15f + sin(animPhase) * 0.04f, -0.52f, 0.46f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x88E1F5FE), Color.Transparent),
                    center = breathCenter,
                    radius = cam.scale * 0.28f
                ),
                radius = cam.scale * 0.28f,
                center = breathCenter
            )
        }
        RoomArchetype.STUDY -> {
            // Icicles hanging from both computer monitors
            for (i in 0..5) {
                val ix = -0.38f + i * 0.14f
                val len = if (i % 2 == 0) 0.13f else 0.08f
                drawIcicle3D(cam, ix, -0.57f, 0.64f, len, 0.026f)
            }
            drawSixPointIceCrystal(cam.project(-0.22f, -0.56f, 0.50f), 7.5f * scaleFactor)
            drawSixPointIceCrystal(cam.project(0.20f, -0.56f, 0.50f), 7.5f * scaleFactor)
        }
        RoomArchetype.KITCHEN -> {
            // Icicles hanging along the kitchen counter and island edges
            for (i in 0..6) {
                val ix = -0.70f + i * 0.22f
                drawIcicle3D(cam, ix, -0.39f, 0.38f, 0.13f, 0.028f)
            }
            // Frozen ice patch over the induction hob
            val hobCenter = cam.project(0.0f, 0.18f, 0.41f)
            drawCircle(color = Color(0xCCE1F5FE), radius = cam.scale * 0.11f, center = hobCenter)
            drawSixPointIceCrystal(hobCenter, 8f * scaleFactor)
        }
        RoomArchetype.BATHROOM -> {
            // Frozen solid bathtub surface with white ice fracture lines
            drawIsoPrism(
                cam, -0.70f, 0.16f, -0.73f, -0.22f, 0.31f, 0.33f,
                topColor = Color(0xFFE1F5FE),
                leftColor = Color(0xFFB3E5FC),
                rightColor = Color(0xFF81D4FA)
            )
            drawLine(
                color = Color.White,
                start = cam.project(-0.62f, -0.65f, 0.335f),
                end = cam.project(0.08f, -0.30f, 0.335f),
                strokeWidth = 2f
            )
            for (i in 0..3) {
                drawIcicle3D(cam, 0.38f + i * 0.11f, -0.27f, 0.36f, 0.13f, 0.028f)
            }
            drawSixPointIceCrystal(cam.project(-0.26f, -0.48f, 0.35f), 8.5f * scaleFactor)
        }
        RoomArchetype.BALCONY_GARDEN -> {
            // Snow caps on the garden trees
            val tree1 = cam.project(-0.45f, -0.60f, 0.62f)
            val tree2 = cam.project(0.02f, -0.60f, 0.56f)
            drawCircle(color = Color(0xFFF8FAFC), radius = cam.scale * 0.15f, center = tree1)
            drawCircle(color = Color(0xFFE1F5FE), radius = cam.scale * 0.13f, center = tree2)
            // Row of icicles along the glass balcony balustrade
            for (i in 0..7) {
                val ix = -0.82f + i * 0.22f
                val len = if (i % 2 == 0) 0.16f else 0.10f
                drawIcicle3D(cam, ix, -0.90f, 0.45f, len, 0.030f)
            }
            // Drifting six-point snowflakes
            for (s in 0..3) {
                val sx = -0.55f + s * 0.38f + sin(animPhase + s) * 0.05f
                val sz = 0.35f + ((s * 0.23f - animPhase * 0.08f).mod(0.55f))
                drawSixPointIceCrystal(cam.project(sx, -0.15f, sz), 7f * scaleFactor)
            }
        }
    }
}

private fun DrawScope.drawIcicle3D(
    cam: IsoCamera,
    x: Float,
    y: Float,
    topZ: Float,
    lengthZ: Float,
    halfWidthX: Float
) {
    val pLeft = cam.project(x - halfWidthX, y, topZ)
    val pRight = cam.project(x + halfWidthX, y, topZ)
    val pTip = cam.project(x, y, topZ - lengthZ)
    val path = Path().apply {
        moveTo(pLeft.x, pLeft.y)
        lineTo(pRight.x, pRight.y)
        lineTo(pTip.x, pTip.y)
        close()
    }
    drawPath(path, color = Color(0xE6E1F5FE))
    drawPath(path, color = Color(0xFF81D4FA), style = Stroke(width = 1f))
}

private fun DrawScope.drawIcicleOnLeftWall3D(
    cam: IsoCamera,
    x: Float,
    y: Float,
    topZ: Float,
    lengthZ: Float,
    halfWidthY: Float
) {
    val pLeft = cam.project(x, y - halfWidthY, topZ)
    val pRight = cam.project(x, y + halfWidthY, topZ)
    val pTip = cam.project(x, y, topZ - lengthZ)
    val path = Path().apply {
        moveTo(pLeft.x, pLeft.y)
        lineTo(pRight.x, pRight.y)
        lineTo(pTip.x, pTip.y)
        close()
    }
    drawPath(path, color = Color(0xE6E1F5FE))
    drawPath(path, color = Color(0xFF81D4FA), style = Stroke(width = 1f))
}

private fun DrawScope.drawSixPointIceCrystal(
    center: Offset,
    radius: Float
) {
    for (angleDeg in listOf(0f, 60f, 120f)) {
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val dx = cos(rad) * radius
        val dy = sin(rad) * radius
        drawLine(
            color = Color(0xEEFFFFFF),
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = 2f
        )
    }
    drawCircle(color = Color(0xFF81D4FA), radius = radius * 0.28f, center = center)
}

/**
 * Redesigned Living Room featuring:
 * 1. Floor-to-ceiling library bookcase with colourful books along the left wall
 * 2. Widescreen TV on a media console along the right back wall
 * 3. Plush sofa and coffee table facing the TV
 */
private fun DrawScope.drawLivingRoomFurniture(cam: IsoCamera) {
    // 1. Floor-to-ceiling library bookcase on the left wall (x = -0.87..-0.64, y = -0.78..0.32, z = 0.02..1.02)
    drawIsoPrism(
        cam, -0.87f, -0.64f, -0.78f, 0.32f, 0.02f, 1.02f,
        topColor = Color(0xFF6D4C41),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF5D4037)
    )
    val bookPalette = listOf(
        Color(0xFFEF5350),
        Color(0xFF42A5F5),
        Color(0xFFFFCA28),
        Color(0xFF66BB6A),
        Color(0xFFAB47BC)
    )
    for (shelf in 0..3) {
        val sz = 0.12f + shelf * 0.22f
        // Wooden shelf plank
        drawIsoPrism(
            cam, -0.85f, -0.63f, -0.74f, 0.28f, sz, sz + 0.035f,
            topColor = Color(0xFF8D6E63),
            leftColor = Color(0xFF5D4037),
            rightColor = Color(0xFF6D4C41)
        )
        // Row of colourful books on each shelf
        for (b in 0..3) {
            val by = -0.68f + b * 0.22f
            val bookCol = bookPalette[(shelf + b) % bookPalette.size]
            drawIsoPrism(
                cam, -0.82f, -0.65f, by, by + 0.14f, sz + 0.035f, sz + 0.18f,
                topColor = bookCol,
                leftColor = bookCol.copy(alpha = 0.8f),
                rightColor = bookCol.copy(alpha = 0.9f)
            )
        }
    }

    // 2. Widescreen TV + low oak media console on the right back wall (x = -0.38..0.58, y = -0.86..-0.58)
    drawIsoPrism(
        cam, -0.38f, 0.58f, -0.86f, -0.58f, 0.02f, 0.22f,
        topColor = Color(0xFF8D6E63),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF5D4037)
    )
    // Sleek dark TV bezel
    drawIsoPrism(
        cam, -0.30f, 0.50f, -0.84f, -0.76f, 0.28f, 0.84f,
        topColor = Color(0xFF263238),
        leftColor = Color(0xFF11171A),
        rightColor = Color(0xFF1C262B)
    )
    // Illuminated TV screen panel
    drawIsoPrism(
        cam, -0.26f, 0.46f, -0.76f, -0.73f, 0.32f, 0.80f,
        topColor = Color(0xFF4DD0E1),
        leftColor = Color(0xFF00ACC1),
        rightColor = Color(0xFF26C6DA)
    )

    // 3. Soft area rug + Plush sofa facing the TV + Coffee table
    drawIsoPrism(
        cam, -0.42f, 0.54f, -0.36f, 0.58f, 0.0f, 0.02f,
        topColor = Color(0xFF31445E),
        leftColor = Color(0xFF243449),
        rightColor = Color(0xFF243449)
    )
    // Coffee table between sofa and TV
    drawIsoPrism(
        cam, -0.20f, 0.34f, -0.32f, -0.04f, 0.02f, 0.17f,
        topColor = Color(0xFFB88655),
        leftColor = Color(0xFF8A613B),
        rightColor = Color(0xFF9E7146)
    )
    // Sofa seat & cushions facing the TV
    drawIsoPrism(
        cam, -0.36f, 0.48f, 0.12f, 0.56f, 0.02f, 0.24f,
        topColor = Color(0xFF4A6382),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF3F5570)
    )
    // Sofa backrest
    drawIsoPrism(
        cam, -0.36f, 0.48f, 0.42f, 0.58f, 0.24f, 0.48f,
        topColor = Color(0xFF587496),
        leftColor = Color(0xFF3B4F69),
        rightColor = Color(0xFF475E7B)
    )
    // Sofa armrests
    drawIsoPrism(
        cam, -0.42f, -0.28f, 0.12f, 0.56f, 0.02f, 0.35f,
        topColor = Color(0xFF526D8E),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF425975)
    )
    drawIsoPrism(
        cam, 0.42f, 0.54f, 0.12f, 0.56f, 0.02f, 0.35f,
        topColor = Color(0xFF526D8E),
        leftColor = Color(0xFF364961),
        rightColor = Color(0xFF425975)
    )
}

private fun DrawScope.drawBedroomFurniture(
    cam: IsoCamera,
    tempColor: Color,
    animPhase: Float
) {
    drawIsoPrism(
        cam, -0.65f, 0.35f, -0.78f, 0.18f, 0.02f, 0.24f,
        topColor = Color(0xFFD7CCC8),
        leftColor = Color(0xFF5D4037),
        rightColor = Color(0xFF6D4C41)
    )
    drawIsoPrism(
        cam, -0.63f, 0.33f, -0.35f, 0.16f, 0.24f, 0.30f,
        topColor = tempColor.copy(alpha = 0.65f),
        leftColor = tempColor.copy(alpha = 0.45f),
        rightColor = tempColor.copy(alpha = 0.55f)
    )
    drawIsoPrism(
        cam, -0.65f, 0.35f, -0.84f, -0.74f, 0.02f, 0.60f,
        topColor = Color(0xFF6D4C41),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF5D4037)
    )
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
    tempColor: Color
) {
    drawIsoPrism(
        cam, -0.55f, 0.48f, -0.75f, -0.25f, 0.26f, 0.32f,
        topColor = Color(0xFFA1887F),
        leftColor = Color(0xFF6D4C41),
        rightColor = Color(0xFF795548)
    )
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
    drawIsoPrism(
        cam, -0.78f, 0.72f, -0.82f, -0.40f, 0.02f, 0.38f,
        topColor = Color(0xFFECEFF1),
        leftColor = Color(0xFF455A64),
        rightColor = Color(0xFF546E7A)
    )
    drawIsoPrism(
        cam, -0.45f, 0.48f, -0.05f, 0.42f, 0.02f, 0.36f,
        topColor = Color(0xFFCFD8DC),
        leftColor = Color(0xFF37474F),
        rightColor = Color(0xFF455A64)
    )
    val hobCenter = cam.project(0.0f, 0.18f, 0.40f)
    drawCircle(
        color = tempColor.copy(alpha = 0.75f + 0.15f * sin(animPhase * 2f)),
        radius = cam.scale * 0.10f,
        center = hobCenter
    )
}

private fun DrawScope.drawBathroomFurniture(
    cam: IsoCamera,
    humColor: Color
) {
    drawIsoPrism(
        cam, -0.72f, 0.18f, -0.75f, -0.20f, 0.02f, 0.32f,
        topColor = Color(0xFF4DD0E1).copy(alpha = 0.75f),
        leftColor = Color(0xFFCFD8DC),
        rightColor = Color(0xFFE0E6ED)
    )
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
    drawIsoPrism(
        cam, -0.75f, 0.30f, -0.80f, -0.42f, 0.02f, 0.25f,
        topColor = Color(0xFF5D4037),
        leftColor = Color(0xFF4E342E),
        rightColor = Color(0xFF6D4C41)
    )
    val sway = sin(animPhase * 1.4f) * 0.04f
    val tree1 = cam.project(-0.48f + sway, -0.60f, 0.56f)
    val tree2 = cam.project(-0.08f - sway, -0.58f, 0.48f)
    drawCircle(color = Color(0xFF2E7D32), radius = cam.scale * 0.21f, center = tree1)
    drawCircle(color = Color(0xFF43A047), radius = cam.scale * 0.16f, center = Offset(tree1.x - 6f, tree1.y - 8f))
    drawCircle(color = Color(0xFF388E3C), radius = cam.scale * 0.17f, center = tree2)

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

/**
 * Clean, uncluttered humidity animation built around the transition from dots (<60%) to drops (>=60%):
 * - < 35% RH (Dry): 5 slow amber micro-dots.
 * - 35% to 59% RH (Optimal comfort, e.g. 50–58%): 8 calm emerald-aqua micro-dots rising gently.
 * - 60% to 64% RH (Slightly humid): 10 small light-cyan water teardrops rising smoothly.
 * - >= 65% RH (High humidity / damp, e.g. 65–70%+): 14 well-spaced cobalt-indigo glossy water teardrops
 *   with crisp white specular highlights, rising smoothly without fog clouds or wall clutter.
 */
private fun DrawScope.drawHumidityDroplets(
    cam: IsoCamera,
    room: SensorRoomState,
    humColor: Color,
    animPhase: Float,
    particleProgress: Float
) {
    val rh = room.humidityPct
    val scaleFactor = (cam.scale / 115f).coerceAtLeast(0.55f)

    val dropletCount = when {
        rh < 35f -> 5
        rh < 60f -> 8                               // 8 calm micro-dots at 35-59%
        rh < 65f -> 10                              // 10 small cyan teardrops at 60-64%
        else -> 14                                  // 14 fuller glossy cobalt-indigo teardrops at >= 65%
    }

    val speedMult = when {
        rh < 35f -> 0.45f
        rh < 60f -> 0.62f
        rh < 65f -> 0.78f
        else -> 0.92f
    }

    for (i in 0 until dropletCount) {
        val seed = i * 1.618034f
        val px = ((seed * 3.7f) % 1.4f) - 0.7f + sin(animPhase * speedMult + seed) * 0.05f
        val py = ((seed * 2.3f) % 1.4f) - 0.7f + cos(animPhase * 0.8f * speedMult + seed) * 0.05f
        val rawZ = (particleProgress * speedMult + (seed % 1f)) % 1f
        val pz = rawZ * 0.90f + 0.10f
        val screenPt = cam.project(px, py, pz)
        val edgeFade = sin(((pz / 1.03f).coerceIn(0f, 1f)) * Math.PI.toFloat())

        if (rh < 60f) {
            // Calm circular micro-dots (emerald for 35-59%, amber for <35%)
            val radius = (if (rh < 35f) 2.2f else 2.8f) * scaleFactor
            val alpha = (edgeFade * if (rh < 35f) 0.45f else 0.58f).coerceIn(0.15f, 0.60f)
            drawCircle(
                color = humColor.copy(alpha = alpha),
                radius = radius,
                center = screenPt
            )
        } else {
            // Clean teardrop water drops for >= 60% RH (small cyan at 60-64%, fuller cobalt-indigo with specular dot at >= 65%)
            val dropSize = (if (rh >= 65f) 4.8f else 3.5f) * scaleFactor
            val alpha = (edgeFade * if (rh >= 65f) 0.86f else 0.74f).coerceIn(0.25f, 0.88f)
            val dropColor = if (rh >= 65f) {
                if (i % 2 == 0) Color(0xFF38BDF8) else Color(0xFF818CF8)
            } else {
                Color(0xFF29B6F6)
            }
            drawWaterTeardrop(
                center = screenPt,
                radius = dropSize,
                color = dropColor.copy(alpha = alpha),
                withHighlight = rh >= 65f
            )
        }
    }
}

private fun DrawScope.drawWaterTeardrop(
    center: Offset,
    radius: Float,
    color: Color,
    withHighlight: Boolean
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius * 1.45f)
        cubicTo(
            center.x + radius * 1.1f, center.y - radius * 0.2f,
            center.x + radius * 1.05f, center.y + radius * 0.95f,
            center.x, center.y + radius
        )
        cubicTo(
            center.x - radius * 1.05f, center.y + radius * 0.95f,
            center.x - radius * 1.1f, center.y - radius * 0.2f,
            center.x, center.y - radius * 1.45f
        )
        close()
    }
    drawPath(path = path, color = color)
    if (withHighlight) {
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = radius * 0.26f,
            center = Offset(center.x - radius * 0.28f, center.y + radius * 0.15f)
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

    val leftFace = Path().apply {
        moveTo(t01.x, t01.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b01.x, b01.y)
        close()
    }
    drawPath(leftFace, color = leftColor)

    val rightFace = Path().apply {
        moveTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(b11.x, b11.y)
        lineTo(b10.x, b10.y)
        close()
    }
    drawPath(rightFace, color = rightColor)

    val topFace = Path().apply {
        moveTo(t00.x, t00.y)
        lineTo(t10.x, t10.y)
        lineTo(t11.x, t11.y)
        lineTo(t01.x, t01.y)
        close()
    }
    drawPath(topFace, color = topColor)
}
