package com.dariopittera.ecowittpixel.watch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WatchOledBlack = Color(0xFF000000)
val WatchDeepNavy = Color(0xFF070E1A)
val WatchSurfaceCard = Color(0xFF111D30)
val WatchThermalCoral = Color(0xFFEF5350)
val WatchThermalAmber = Color(0xFFFFD54F)
val WatchThermalOrange = Color(0xFFFF9800)
val WatchComfortEmerald = Color(0xFF26A69A)
val WatchCoolCyan = Color(0xFF4DD0E1)
val WatchCobaltDrop = Color(0xFF6366F1)

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
        humidityPct < 35f -> WatchThermalAmber
        humidityPct < 60f -> WatchComfortEmerald
        humidityPct < 65f -> Color(0xFF29B6F6)
        else -> WatchCobaltDrop
    }
}

@Composable
fun EcowittWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchColorScheme,
        content = content
    )
}
