package com.dariopittera.ecowittpixel.watch.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

val WatchOledBlack = Color(0xFF000000)
val WatchDeepNavy = Color(0xFF070E1A)
val WatchSurfaceCard = Color(0xFF111D30)
val WatchThermalCoral = Color(0xFFEF5350)
val WatchThermalAmber = Color(0xFFFFD54F)
val WatchThermalOrange = Color(0xFFFF9800)
val WatchComfortEmerald = Color(0xFF38BDF8)
val WatchCoolCyan = Color(0xFF60A5FA)
val WatchCobaltDrop = Color(0xFF3B82F6)

private val WatchColorScheme = darkColorScheme(
    primary = WatchCoolCyan,
    onPrimary = Color(0xFF002B33),
    secondary = WatchComfortEmerald,
    background = WatchOledBlack,
    onBackground = Color(0xFFE2E8F0),
    surface = WatchSurfaceCard,
    onSurface = Color(0xFFE2E8F0)
)

fun watchTemperatureColor(tempC: Float): Color {
    return when {
        tempC >= 26.0f -> WatchThermalCoral
        tempC >= 23.0f -> WatchThermalOrange
        tempC >= 19.0f -> WatchThermalAmber
        else -> Color(0xFFF8FAFC)
    }
}

fun watchHumidityColor(humidityPct: Float): Color {
    return when {
        humidityPct < 35f -> Color(0xFF60A5FA)
        humidityPct < 60f -> Color(0xFF38BDF8)
        humidityPct < 65f -> Color(0xFF0EA5E9)
        else -> Color(0xFF3B82F6)
    }
}

private const val CANONICAL_WATCH_WIDTH_DP = 192f

fun createNormalizedWatchContext(base: Context): Context {
    val metrics = base.resources.displayMetrics
    val shortestPx = minOf(metrics.widthPixels, metrics.heightPixels)
    if (shortestPx <= 0) return base
    val targetDensity = (shortestPx / CANONICAL_WATCH_WIDTH_DP).coerceIn(1.2f, 3.5f)
    val targetDpi = (targetDensity * 160f).roundToInt()
    val config = Configuration(base.resources.configuration).apply {
        fontScale = 1.0f
        densityDpi = targetDpi
    }
    return base.createConfigurationContext(config)
}

@Composable
fun EcowittWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentDensity = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics
    val shortestWidthPx = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels).toFloat()
    val normalizedDensity = if (shortestWidthPx > 0f) {
        (shortestWidthPx / CANONICAL_WATCH_WIDTH_DP).coerceIn(1.2f, 3.5f)
    } else {
        currentDensity.density
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = normalizedDensity,
            fontScale = 1.0f
        )
    ) {
        MaterialTheme(
            colorScheme = WatchColorScheme,
            content = content
        )
    }
}
