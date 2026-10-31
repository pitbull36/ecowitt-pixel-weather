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
val ThermalCoral = Color(0xFFFF8A65)
val ThermalAmber = Color(0xFFFFCA28)
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

fun temperatureAccentColor(tempC: Float): Color {
    return when {
        tempC <= 8f -> FrostBlue
        tempC <= 17.5f -> CoolCyan
        tempC <= 23.2f -> ComfortEmerald
        tempC <= 26.5f -> ThermalAmber
        else -> ThermalCoral
    }
}

fun humidityAccentColor(humidityPct: Float): Color {
    return when {
        humidityPct < 35f -> ThermalAmber
        humidityPct <= 58f -> ComfortEmerald
        humidityPct <= 68f -> CoolCyan
        else -> HumidViolet
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
