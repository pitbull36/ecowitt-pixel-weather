package com.dariopittera.ecowittpixel.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PixelSkyNavy = Color(0xFF0B1320)
val PixelSurfaceCard = Color(0xFF131F33)
val PixelSurfaceElevated = Color(0xFF1B2B44)
val ThermalCoral = Color(0xFFEF5350)
val ThermalAmber = Color(0xFFFFD54F)
val ThermalOrange = Color(0xFFFF9800)
val ComfortEmerald = Color(0xFF26A69A)
val CoolCyan = Color(0xFF4DD0E1)
val FrostBlue = Color(0xFF64B5F6)
val HumidViolet = Color(0xFF9575CD)

private val AtmosphericColorScheme = darkColorScheme(
    primary = Color(0xFF80DEEA),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFFFB59D),
    onSecondary = Color(0xFF5C1900),
    secondaryContainer = Color(0xFF7D2C0D),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA6D39A),
    background = PixelSkyNavy,
    onBackground = Color(0xFFE3E8F2),
    surface = PixelSurfaceCard,
    onSurface = Color(0xFFE3E8F2),
    surfaceVariant = PixelSurfaceElevated,
    onSurfaceVariant = Color(0xFFBAC6DC)
)

/**
 * Thermometer colour scale:
 * - below 19°C: White
 * - 19°C to <23°C: Yellow
 * - 23°C to <26°C: Orange
 * - 26°C and above: Red
 */
fun temperatureAccentColor(tempC: Float): Color {
    return when {
        tempC >= 26.0f -> Color(0xFFEF5350) // Red
        tempC >= 23.0f -> Color(0xFFFF9800) // Orange
        tempC >= 19.0f -> Color(0xFFFFD54F) // Yellow
        else -> Color(0xFFF8FAFC)           // White
    }
}

/**
 * Scientific indoor humidity bands (ASHRAE 55 / CIBSE / EPA / ASHRAE 160):
 * - below 35%: Dry air (Amber)
 * - 35% to 59%: Optimal comfort range (Calm Emerald-Aqua)
 * - 60% to 64%: Slightly elevated moisture (Warning Cyan-Blue)
 * - 65% and above: High humidity / mould risk threshold (Damp Electric Blue-Violet)
 */
fun humidityAccentColor(humidityPct: Float): Color {
    return when {
        humidityPct < 35f -> ThermalAmber
        humidityPct < 60f -> ComfortEmerald
        humidityPct < 65f -> Color(0xFF29B6F6)
        else -> Color(0xFF6366F1)
    }
}

@Composable
fun EcowittPixelTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = PixelSkyNavy,
            surface = PixelSurfaceCard
        )
    } else {
        AtmosphericColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
