package com.dariopittera.ecowittpixel.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dariopittera.ecowittpixel.MainActivity
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.Psychrometrics
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.model.TempUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetPreferences {
    private const val PREFS_NAME = "ecowitt_widget_room_prefs"
    val KEY_SELECT_CHANNEL_ID = ActionParameters.Key<String>(MainActivity.EXTRA_SELECT_CHANNEL_ID)
    val KEY_APP_WIDGET_ID = ActionParameters.Key<Int>("app_widget_id")

    val GLANCE_KEY_CHANNEL_ID = stringPreferencesKey("selected_channel_id")
    val GLANCE_KEY_ROOM_INDEX = intPreferencesKey("selected_room_index")
    val GLANCE_KEY_ROOM_NAME = stringPreferencesKey("selected_room_name")
    val GLANCE_KEY_UPDATE_NONCE = longPreferencesKey("update_nonce")

    /**
     * Synchronously persists the selected room channel, index, and name for [appWidgetId],
     * plus stores a fallback last-selected room in case the launcher queries before [appWidgetId]
     * is mapped.
     */
    fun saveWidgetRoomSelection(
        context: Context,
        appWidgetId: Int,
        channelId: String,
        roomIndex: Int = -1,
        roomName: String = ""
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            editor.putString("widget_room_$appWidgetId", channelId)
            editor.putInt("widget_room_idx_$appWidgetId", roomIndex)
            editor.putString("widget_room_name_$appWidgetId", roomName)
        }
        editor.putString("last_selected_channel_id", channelId)
        editor.putInt("last_selected_room_idx", roomIndex)
        editor.putString("last_selected_room_name", roomName)
        editor.commit()
    }

    fun getWidgetRoomChannel(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val direct = prefs.getString("widget_room_$appWidgetId", null)
            if (!direct.isNullOrBlank()) return direct
        }
        return prefs.getString("last_selected_channel_id", null)
    }

    fun getWidgetRoomIndex(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            prefs.contains("widget_room_idx_$appWidgetId")
        ) {
            return prefs.getInt("widget_room_idx_$appWidgetId", -1)
        }
        return prefs.getInt("last_selected_room_idx", -1)
    }

    /**
     * Resolves the user's selected room from both Glance [Preferences] and synchronous
     * [SharedPreferences], matching by channelId (case-insensitive), roomIndex, or custom roomName.
     */
    fun resolveSelectedRoom(
        context: Context,
        appWidgetId: Int,
        glancePrefs: Preferences?,
        snapshot: HomeAtmosphereSnapshot
    ): SensorRoomState? {
        val rooms = snapshot.rooms
        if (rooms.isEmpty()) return null

        val candidateChannelId = glancePrefs?.get(GLANCE_KEY_CHANNEL_ID)
            ?.takeIf { it.isNotBlank() }
            ?: getWidgetRoomChannel(context, appWidgetId)

        if (!candidateChannelId.isNullOrBlank()) {
            rooms.firstOrNull { it.channelId.equals(candidateChannelId, ignoreCase = true) }
                ?.let { return it }
        }

        val candidateIndex = glancePrefs?.get(GLANCE_KEY_ROOM_INDEX)
            ?: getWidgetRoomIndex(context, appWidgetId)
        if (candidateIndex in rooms.indices) {
            return rooms[candidateIndex]
        }

        val candidateName = glancePrefs?.get(GLANCE_KEY_ROOM_NAME)
        if (!candidateName.isNullOrBlank()) {
            rooms.firstOrNull { it.roomName.equals(candidateName, ignoreCase = true) }
                ?.let { return it }
        }

        return snapshot.primaryGatewayRoom ?: rooms.firstOrNull()
    }

    /**
     * Updates both synchronous SharedPreferences and Glance's per-widget DataStore state for
     * [appWidgetId], then triggers an immediate Glance recomposition.
     */
    suspend fun applyRoomSelectionAndRefresh(
        context: Context,
        appWidgetId: Int,
        channelId: String,
        roomIndex: Int,
        roomName: String
    ) {
        saveWidgetRoomSelection(context, appWidgetId, channelId, roomIndex, roomName)
        val manager = GlanceAppWidgetManager(context)
        val nonce = System.currentTimeMillis()

        // Update matching single-room widget instances
        val singleIds = runCatching {
            manager.getGlanceIds(EcowittSingleRoomWidget::class.java)
        }.getOrDefault(emptyList())
        for (gId in singleIds) {
            val idInt = runCatching { manager.getAppWidgetId(gId) }
                .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
                idInt == appWidgetId ||
                singleIds.size == 1
            ) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, gId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[GLANCE_KEY_CHANNEL_ID] = channelId
                            this[GLANCE_KEY_ROOM_INDEX] = roomIndex
                            this[GLANCE_KEY_ROOM_NAME] = roomName
                            this[GLANCE_KEY_UPDATE_NONCE] = nonce
                        }
                    }
                    EcowittSingleRoomWidget().update(context, gId)
                }
            }
        }

        // Also update multi-room / resizable widget instances if the user configured one
        val multiIds = runCatching {
            manager.getGlanceIds(EcowittGlanceWidget::class.java)
        }.getOrDefault(emptyList())
        for (gId in multiIds) {
            val idInt = runCatching { manager.getAppWidgetId(gId) }
                .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
            if (idInt == appWidgetId) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, gId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[GLANCE_KEY_CHANNEL_ID] = channelId
                            this[GLANCE_KEY_ROOM_INDEX] = roomIndex
                            this[GLANCE_KEY_ROOM_NAME] = roomName
                            this[GLANCE_KEY_UPDATE_NONCE] = nonce
                        }
                    }
                    EcowittGlanceWidget().update(context, gId)
                }
            }
        }
    }
}

/**
 * Renders a crisp, high-DPI circular climate gauge Bitmap with:
 * - Upper curved bezel arc for Temperature (white <19°C, yellow 19–22.9°C, orange 23–25.9°C,
 *   red 26–29.9°C, and molten lava droplets >=30°C).
 * - Lower curved bezel arc for Humidity (water-blue palette #60A5FA / #38BDF8 / #0EA5E9 / #3B82F6).
 * - Clean room name, bold temperature, humidity % RH, and sync timestamp / dew point inside the dial.
 */
object WidgetCircularGaugeRenderer {

    fun renderRoomCircleBitmap(
        room: SensorRoomState,
        tempUnit: TempUnit,
        diameterPx: Int = 320,
        showSubtitle: Boolean = true,
        syncTimeStr: String? = null
    ): Bitmap {
        val bmp = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = diameterPx / 2f
        val cy = diameterPx / 2f
        val outerRadius = diameterPx * 0.485f

        // 1. Deep midnight dark-glass circular disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy - outerRadius * 0.18f,
                outerRadius,
                intArrayOf(
                    0xFF172C4A.toInt(),
                    0xFF0E1B30.toInt(),
                    0xFF081120.toInt()
                ),
                floatArrayOf(0f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, outerRadius, discPaint)

        // Subtle outer glass rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF28466E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = (diameterPx * 0.008f).coerceAtLeast(1.5f)
        }
        canvas.drawCircle(cx, cy, outerRadius - rimPaint.strokeWidth * 0.5f, rimPaint)

        // Arc geometry
        val arcStroke = (diameterPx * 0.058f).coerceAtLeast(8f)
        val arcInset = diameterPx * 0.062f
        val arcRect = RectF(
            arcInset,
            arcInset,
            diameterPx - arcInset,
            diameterPx - arcInset
        )
        val arcRadius = arcRect.width() / 2f

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF192D49.toInt()
            style = Paint.Style.STROKE
            strokeWidth = arcStroke
            strokeCap = Paint.Cap.ROUND
        }

        // 2. Top curved bezel arc: Temperature (196° clockwise by 148° to 344°)
        val topStartDeg = 196f
        val topMaxSweepDeg = 148f
        canvas.drawArc(arcRect, topStartDeg, topMaxSweepDeg, false, trackPaint)

        val minT = if (room.archetype.isOutdoor) -2f else 10f
        val maxT = if (room.archetype.isOutdoor) 35f else 32f
        val tempFrac = ((room.temperatureC - minT) / (maxT - minT)).coerceIn(0.08f, 1f)
        val tempSweepDeg = topMaxSweepDeg * tempFrac
        val tempColorInt = temperatureColorInt(room.temperatureC)

        val tempArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = arcStroke
            strokeCap = Paint.Cap.ROUND
            if (room.temperatureC >= 30f) {
                shader = LinearGradient(
                    arcRect.left,
                    arcRect.top,
                    arcRect.right,
                    arcRect.top,
                    intArrayOf(0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFFDE047.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                color = tempColorInt
            }
        }
        canvas.drawArc(arcRect, topStartDeg, tempSweepDeg, false, tempArcPaint)

        // Molten lava blobs (>=30°C) or frost crystal sparkles (<19°C) along the top arc
        if (room.temperatureC >= 30f) {
            val lavaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFDE047.toInt()
                style = Paint.Style.FILL
            }
            for (b in 1..4) {
                val bAngle = Math.toRadians((topStartDeg + tempSweepDeg * (b / 5f)).toDouble())
                val bx = cx + arcRadius * cos(bAngle).toFloat()
                val by = cy + arcRadius * sin(bAngle).toFloat()
                canvas.drawCircle(bx, by, arcStroke * 0.34f, lavaPaint)
            }
        } else if (room.temperatureC < 19f) {
            val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFBAE6FD.toInt()
                style = Paint.Style.FILL
            }
            for (f in 1..3) {
                val fAngle = Math.toRadians((topStartDeg + tempSweepDeg * (f / 4f)).toDouble())
                val fx = cx + arcRadius * cos(fAngle).toFloat()
                val fy = cy + arcRadius * sin(fAngle).toFloat()
                canvas.drawCircle(fx, fy, arcStroke * 0.24f, frostPaint)
            }
        }

        // Glowing indicator dot at tip of temperature arc
        val tempEndRad = Math.toRadians((topStartDeg + tempSweepDeg).toDouble())
        val tempTipX = cx + arcRadius * cos(tempEndRad).toFloat()
        val tempTipY = cy + arcRadius * sin(tempEndRad).toFloat()
        drawGlowingTipDot(canvas, tempTipX, tempTipY, arcStroke * 0.62f, tempColorInt)

        // 3. Bottom curved bezel arc: Humidity (164° counter-clockwise by -148° to 16°)
        val bottomStartDeg = 164f
        val bottomMaxSweepDeg = -148f
        canvas.drawArc(arcRect, bottomStartDeg, bottomMaxSweepDeg, false, trackPaint)

        val humFrac = (room.humidityPct / 100f).coerceIn(0.08f, 1f)
        val humSweepDeg = bottomMaxSweepDeg * humFrac
        val humColorInt = humidityColorInt(room.humidityPct)

        val humArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = humColorInt
            style = Paint.Style.STROKE
            strokeWidth = arcStroke
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(arcRect, bottomStartDeg, humSweepDeg, false, humArcPaint)

        // Water droplet specular accents along bottom arc when humidity >= 60%
        if (room.humidityPct >= 60f) {
            val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE0F2FE.toInt()
                style = Paint.Style.FILL
            }
            val dropCount = if (room.humidityPct >= 65f) 4 else 2
            for (d in 1..dropCount) {
                val dAngle = Math.toRadians((bottomStartDeg + humSweepDeg * (d / (dropCount + 1f))).toDouble())
                val dx = cx + arcRadius * cos(dAngle).toFloat()
                val dy = cy + arcRadius * sin(dAngle).toFloat()
                canvas.drawCircle(dx, dy, arcStroke * 0.25f, dropPaint)
            }
        }

        // Glowing indicator dot at tip of humidity arc
        val humEndRad = Math.toRadians((bottomStartDeg + humSweepDeg).toDouble())
        val humTipX = cx + arcRadius * cos(humEndRad).toFloat()
        val humTipY = cy + arcRadius * sin(humEndRad).toFloat()
        drawGlowingTipDot(canvas, humTipX, humTipY, arcStroke * 0.62f, humColorInt)

        // 4. Typography inside the circular dial
        val cleanName = cleanRoomLabel(room.roomName)
        val roomLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textAlign = Paint.Align.CENTER
            textSize = diameterPx * 0.102f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val maxNameChars = if (diameterPx >= 280) 13 else 11
        val truncatedName = if (cleanName.length > maxNameChars) {
            cleanName.take(maxNameChars - 1).trim() + "…"
        } else {
            cleanName
        }
        canvas.drawText(truncatedName, cx, cy - diameterPx * 0.175f, roomLabelPaint)

        // Hero Temperature in the centre
        val tempText = tempUnit.format(room.temperatureC, decimals = 1)
        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = tempColorInt
            textAlign = Paint.Align.CENTER
            textSize = diameterPx * 0.215f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        canvas.drawText(tempText, cx, cy + diameterPx * 0.050f, tempTextPaint)

        // Humidity badge below temperature
        val humText = "${room.humidityPct.roundToInt()}% RH"
        val humTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = humColorInt
            textAlign = Paint.Align.CENTER
            textSize = diameterPx * 0.115f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        canvas.drawText(humText, cx, cy + diameterPx * 0.205f, humTextPaint)

        // Bottom subtitle: either "↻ HH:mm" sync badge (for 1×1 widget) or Dew Point
        if (showSubtitle) {
            val bottomLabel = if (!syncTimeStr.isNullOrBlank()) {
                "↻ $syncTimeStr"
            } else {
                "DP ${tempUnit.format(room.dewPointC, decimals = 0)}"
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = if (!syncTimeStr.isNullOrBlank()) 0xFF7DD3FC.toInt() else 0xFF94A3B8.toInt()
                textAlign = Paint.Align.CENTER
                textSize = diameterPx * 0.082f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            canvas.drawText(bottomLabel, cx, cy + diameterPx * 0.320f, subPaint)
        }

        return bmp
    }

    private fun drawGlowingTipDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        colorInt: Int
    ) {
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colorInt and 0x00FFFFFF) or 0x55000000
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius * 1.45f, haloPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius * 0.85f, corePaint)

        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius * 0.38f, specPaint)
    }

    fun cleanRoomLabel(raw: String): String {
        return raw
            .replace(" (GW1200)", "", ignoreCase = true)
            .replace(Regex("""\s*\(Ch\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { raw }
    }

    fun temperatureColorInt(tempC: Float): Int = when {
        tempC < 19.0f -> 0xFFF8FAFC.toInt() // Icy white
        tempC < 23.0f -> 0xFFFACC15.toInt() // Solar yellow
        tempC < 26.0f -> 0xFFFB923C.toInt() // Warm orange
        tempC < 30.0f -> 0xFFF87171.toInt() // Thermal red
        else -> 0xFFFB7185.toInt()          // Molten lava coral-red
    }

    fun humidityColorInt(humidityPct: Float): Int = when {
        humidityPct < 35f -> 0xFF60A5FA.toInt() // Light sky blue
        humidityPct < 60f -> 0xFF38BDF8.toInt() // Crisp cyan-blue
        humidityPct < 68f -> 0xFF0EA5E9.toInt() // Deep azure
        else -> 0xFF3B82F6.toInt()              // Rich cobalt water blue
    }
}

/**
 * Shared 1×1 circular room widget composable with:
 * - Main circular dial tapping directly into the selected room in [MainActivity]
 * - Small bottom-right `↻` sync badge button triggering an immediate live sync ([RefreshWidgetAction])
 * - Small top-right `⇄` room-cycle badge button cycling through rooms directly on the home screen ([CycleWidgetRoomAction])
 */
@Composable
private fun SingleCircleRoomWidgetBox(
    room: SensorRoomState,
    circleBmp: Bitmap,
    tempUnit: TempUnit,
    appWidgetId: Int
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main circular temperature & humidity dial
        Image(
            provider = ImageProvider(circleBmp),
            contentDescription = "${room.roomName}: ${tempUnit.format(room.temperatureC)}, ${room.humidityPct.roundToInt()}% RH",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(WidgetPreferences.KEY_SELECT_CHANNEL_ID to room.channelId)
                    )
                )
        )

        // Overlay row at top-right: small room-switch pill (⇄) to cycle rooms right from the widget
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(ColorProvider(Color(0xE6132642)))
                    .clickable(
                        actionRunCallback<CycleWidgetRoomAction>(
                            actionParametersOf(WidgetPreferences.KEY_APP_WIDGET_ID to appWidgetId)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⇄",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFBAE6FD)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // Overlay row at bottom-right: small update/sync icon button (↻) to sync immediately
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(ColorProvider(Color(0xE6132642)))
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF38BDF8)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

/**
 * 4×2 (and responsive) Multi-Room Home Screen Widget upgraded with circular temperature and
 * humidity gauges for each room, plus automatic 1×1 single-circle mode when resized small.
 */
class EcowittGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    companion object {
        private val CIRCLE_1X1_SIZE = DpSize(56.dp, 56.dp)
        private val COMPACT_SIZE = DpSize(130.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 120.dp)
        private val EXPANDED_SIZE = DpSize(260.dp, 210.dp)

        suspend fun updateAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val nonce = System.currentTimeMillis()

            // Increment update nonce in Glance Preferences so active sessions recompose immediately
            val multiIds = runCatching {
                manager.getGlanceIds(EcowittGlanceWidget::class.java)
            }.getOrDefault(emptyList())
            for (gId in multiIds) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, gId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WidgetPreferences.GLANCE_KEY_UPDATE_NONCE] = nonce
                        }
                    }
                    EcowittGlanceWidget().update(context, gId)
                }
            }

            val singleIds = runCatching {
                manager.getGlanceIds(EcowittSingleRoomWidget::class.java)
            }.getOrDefault(emptyList())
            for (gId in singleIds) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, gId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WidgetPreferences.GLANCE_KEY_UPDATE_NONCE] = nonce
                        }
                    }
                    EcowittSingleRoomWidget().update(context, gId)
                }
            }

            runCatching { EcowittGlanceWidget().updateAll(context) }
            runCatching { EcowittSingleRoomWidget().updateAll(context) }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(CIRCLE_1X1_SIZE, COMPACT_SIZE, MEDIUM_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            // Subscribe to nonce and room selection changes inside provideContent so Glance recomposes
            val nonce = prefs[WidgetPreferences.GLANCE_KEY_UPDATE_NONCE] ?: 0L
            val localContext = LocalContext.current
            val glanceId = LocalGlanceId.current

            val store = LocalHistoryStore(localContext)
            val settings = store.loadSettings()
            val cached = store.loadWidgetSnapshotCache()
                ?: settings.applyToSnapshot(
                    EcowittApiClient(store).generateSimulatedSnapshot(settings, SimulationParameters())
                )

            val appWidgetId = runCatching {
                GlanceAppWidgetManager(localContext).getAppWidgetId(glanceId)
            }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

            val selectedRoom = WidgetPreferences.resolveSelectedRoom(
                context = localContext,
                appWidgetId = appWidgetId,
                glancePrefs = prefs,
                snapshot = cached
            )
            val timeStr = SimpleDateFormat("HH:mm", Locale.UK).format(Date(cached.lastSyncMs))

            val singleCircleBmp = if (nonce >= 0L && selectedRoom != null) {
                WidgetCircularGaugeRenderer.renderRoomCircleBitmap(
                    room = selectedRoom,
                    tempUnit = settings.tempUnit,
                    diameterPx = 340,
                    showSubtitle = true,
                    syncTimeStr = timeStr
                )
            } else null

            val roomCircleBitmaps = cached.rooms.take(3).associate { room ->
                room.channelId to WidgetCircularGaugeRenderer.renderRoomCircleBitmap(
                    room = room,
                    tempUnit = settings.tempUnit,
                    diameterPx = 230,
                    showSubtitle = true,
                    syncTimeStr = null
                )
            }

            GlanceTheme {
                WidgetContent(
                    snapshot = cached,
                    selectedRoom = selectedRoom,
                    singleCircleBmp = singleCircleBmp,
                    roomCircleBitmaps = roomCircleBitmaps,
                    tempUnit = settings.tempUnit,
                    appWidgetId = appWidgetId
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        snapshot: HomeAtmosphereSnapshot,
        selectedRoom: SensorRoomState?,
        singleCircleBmp: Bitmap?,
        roomCircleBitmaps: Map<String, Bitmap>,
        tempUnit: TempUnit,
        appWidgetId: Int
    ) {
        val size = LocalSize.current
        val isSingleCircle = size.width < 195.dp
        val isExpanded = size.height >= 195.dp
        val timeStr = SimpleDateFormat("HH:mm", Locale.UK).format(Date(snapshot.lastSyncMs))

        if (isSingleCircle && selectedRoom != null && singleCircleBmp != null) {
            SingleCircleRoomWidgetBox(
                room = selectedRoom,
                circleBmp = singleCircleBmp,
                tempUnit = tempUnit,
                appWidgetId = appWidgetId
            )
        } else {
            // 4×2 / 4×3 Multi-Room Card featuring 3 matching circular dials side by side
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(26.dp)
                    .background(ColorProvider(Color(0xFF0A1424)))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Header row
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Ecowitt atmosphere",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${snapshot.gatewayModel} • synced $timeStr (auto 15m)",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF90CAF9)),
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(ColorProvider(Color(0xFF172C4A)))
                                .clickable(actionRunCallback<RefreshWidgetAction>())
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Sync ↻",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF38BDF8)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Row of 3 circular room gauges
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        snapshot.rooms.take(3).forEachIndexed { idx, room ->
                            if (idx > 0) {
                                Spacer(modifier = GlanceModifier.width(6.dp))
                            }
                            val dialBmp = roomCircleBitmaps[room.channelId]
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .clickable(
                                        actionStartActivity<MainActivity>(
                                            actionParametersOf(WidgetPreferences.KEY_SELECT_CHANNEL_ID to room.channelId)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dialBmp != null) {
                                    Image(
                                        provider = ImageProvider(dialBmp),
                                        contentDescription = "${room.roomName}: ${tempUnit.format(room.temperatureC)}, ${room.humidityPct.roundToInt()}% RH",
                                        contentScale = ContentScale.Fit,
                                        modifier = GlanceModifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        val primaryRoom = snapshot.primaryGatewayRoom
                        val outdoorRoom = snapshot.outdoorRoom
                        val advice = if (primaryRoom != null) {
                            Psychrometrics.buildVentilationAdvice(primaryRoom, outdoorRoom, tempUnit)
                        } else {
                            snapshot.pressureForecastText
                        }
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(14.dp)
                                .background(ColorProvider(Color(0xFF13233B)))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(
                                    text = snapshot.pressureForecastText,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF38BDF8)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = advice,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFCBD5E1)),
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated 1×1 Circular Room Widget (`Ecowitt 1×1 Room Circle`) where the user selects which
 * room (`CH0`, `CH1`, `CH2`, etc.) the circular dial displays, with reactive Glance Preferences
 * state, a bottom-right `↻` sync button, and a top-right `⇄` room cycle button.
 */
class EcowittSingleRoomWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            // Subscribe to nonce and room selection inside provideContent so Glance recomposes immediately
            val nonce = prefs[WidgetPreferences.GLANCE_KEY_UPDATE_NONCE] ?: 0L
            val localContext = LocalContext.current
            val glanceId = LocalGlanceId.current

            val store = LocalHistoryStore(localContext)
            val settings = store.loadSettings()
            val cached = store.loadWidgetSnapshotCache()
                ?: settings.applyToSnapshot(
                    EcowittApiClient(store).generateSimulatedSnapshot(settings, SimulationParameters())
                )

            val appWidgetId = runCatching {
                GlanceAppWidgetManager(localContext).getAppWidgetId(glanceId)
            }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

            val room = WidgetPreferences.resolveSelectedRoom(
                context = localContext,
                appWidgetId = appWidgetId,
                glancePrefs = prefs,
                snapshot = cached
            )
            val timeStr = SimpleDateFormat("HH:mm", Locale.UK).format(Date(cached.lastSyncMs))

            val circleBmp = if (nonce >= 0L && room != null) {
                WidgetCircularGaugeRenderer.renderRoomCircleBitmap(
                    room = room,
                    tempUnit = settings.tempUnit,
                    diameterPx = 340,
                    showSubtitle = true,
                    syncTimeStr = timeStr
                )
            } else null

            GlanceTheme {
                if (room != null && circleBmp != null) {
                    SingleCircleRoomWidgetBox(
                        room = room,
                        circleBmp = circleBmp,
                        tempUnit = settings.tempUnit,
                        appWidgetId = appWidgetId
                    )
                }
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val store = LocalHistoryStore(context)
        val settings = store.loadSettings()
        val api = EcowittApiClient(store)
        val fresh = settings.applyToSnapshot(api.fetchAtmosphere(settings, SimulationParameters()))
        store.saveWidgetSnapshotCache(fresh)
        EcowittGlanceWidget.updateAllWidgets(context)
    }
}

/**
 * Allows the user to tap the small `⇄` button on any 1×1 circular widget to cycle directly
 * through their rooms (`CH0` -> `CH1` -> `CH2` -> `CH0`) right on the home screen.
 */
class CycleWidgetRoomAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val store = LocalHistoryStore(context)
        val settings = store.loadSettings()
        val cached = store.loadWidgetSnapshotCache()
            ?: settings.applyToSnapshot(
                EcowittApiClient(store).generateSimulatedSnapshot(settings, SimulationParameters())
            )
        val rooms = cached.rooms
        if (rooms.isEmpty()) return

        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = parameters[WidgetPreferences.KEY_APP_WIDGET_ID]
            ?: runCatching { manager.getAppWidgetId(glanceId) }
                .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        var currentChannelId: String? = null
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            currentChannelId = prefs[WidgetPreferences.GLANCE_KEY_CHANNEL_ID]
                ?: WidgetPreferences.getWidgetRoomChannel(context, appWidgetId)
            val currentIdx = rooms.indexOfFirst {
                it.channelId.equals(currentChannelId, ignoreCase = true)
            }.let { if (it >= 0) it else 0 }
            val nextIdx = (currentIdx + 1) % rooms.size
            val nextRoom = rooms[nextIdx]

            WidgetPreferences.saveWidgetRoomSelection(
                context = context,
                appWidgetId = appWidgetId,
                channelId = nextRoom.channelId,
                roomIndex = nextIdx,
                roomName = nextRoom.roomName
            )

            prefs.toMutablePreferences().apply {
                this[WidgetPreferences.GLANCE_KEY_CHANNEL_ID] = nextRoom.channelId
                this[WidgetPreferences.GLANCE_KEY_ROOM_INDEX] = nextIdx
                this[WidgetPreferences.GLANCE_KEY_ROOM_NAME] = nextRoom.roomName
                this[WidgetPreferences.GLANCE_KEY_UPDATE_NONCE] = System.currentTimeMillis()
            }
        }

        runCatching { EcowittSingleRoomWidget().update(context, glanceId) }
        runCatching { EcowittGlanceWidget().update(context, glanceId) }
    }
}
