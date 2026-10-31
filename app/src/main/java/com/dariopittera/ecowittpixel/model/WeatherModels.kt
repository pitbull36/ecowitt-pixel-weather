package com.dariopittera.ecowittpixel.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class RoomArchetype(
    val id: String,
    val title: String,
    val subtitle: String,
    val isOutdoor: Boolean,
    val idealTempMinC: Float,
    val idealTempMaxC: Float,
    val idealHumidityMin: Float,
    val idealHumidityMax: Float
) {
    LIVING_ROOM(
        id = "living_room",
        title = "Living room",
        subtitle = "Lounge with sofa, lamp, plant, and window",
        isOutdoor = false,
        idealTempMinC = 19.5f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 58f
    ),
    BEDROOM(
        id = "bedroom",
        title = "Master bedroom",
        subtitle = "Sleep sanctuary optimised for cooler night air",
        isOutdoor = false,
        idealTempMinC = 16.5f,
        idealTempMaxC = 20.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 55f
    ),
    STUDY(
        id = "study",
        title = "Home office",
        subtitle = "Study desk with dual monitors and bookshelf",
        isOutdoor = false,
        idealTempMinC = 20.0f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 38f,
        idealHumidityMax = 55f
    ),
    KITCHEN(
        id = "kitchen",
        title = "Kitchen and dining",
        subtitle = "Island counter with higher heat and steam variance",
        isOutdoor = false,
        idealTempMinC = 18.5f,
        idealTempMaxC = 24.0f,
        idealHumidityMin = 38f,
        idealHumidityMax = 62f
    ),
    BATHROOM(
        id = "bathroom",
        title = "Bathroom",
        subtitle = "Tiled suite with mirror condensation tracking",
        isOutdoor = false,
        idealTempMinC = 20.5f,
        idealTempMaxC = 25.0f,
        idealHumidityMin = 40f,
        idealHumidityMax = 65f
    ),
    BALCONY_GARDEN(
        id = "balcony_garden",
        title = "Balcony and garden",
        subtitle = "Outdoor terrace with sky dome and foliage",
        isOutdoor = true,
        idealTempMinC = 10.0f,
        idealTempMaxC = 26.0f,
        idealHumidityMin = 35f,
        idealHumidityMax = 75f
    );

    companion object {
        fun fromId(id: String): RoomArchetype =
            entries.firstOrNull { it.id == id } ?: LIVING_ROOM
    }
}

enum class ConnectionSource(val label: String) {
    LOCAL_LAN("GW1200 local LAN"),
    ECOWITT_CLOUD("Ecowitt cloud API"),
    DEMO_SIMULATION("Interactive simulation"),
    OFFLINE_CACHE("Cached snapshot")
}

enum class TempUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)");

    fun format(tempC: Float, decimals: Int = 1): String {
        val value = convert(tempC)
        return if (decimals == 0) {
            "${value.roundToInt()}$symbol"
        } else {
            String.format("%.${decimals}f%s", value, symbol)
        }
    }

    fun convert(tempC: Float): Float =
        if (this == CELSIUS) tempC else (tempC * 9f / 5f) + 32f
}

enum class PressureUnit(val symbol: String, val label: String) {
    HPA("hPa", "Hectopascals (hPa)"),
    INHG("inHg", "Inches of mercury (inHg)");

    fun format(pressureHpa: Float): String {
        return if (this == HPA) {
            String.format("%.1f %s", pressureHpa, symbol)
        } else {
            String.format("%.2f %s", pressureHpa * 0.02953f, symbol)
        }
    }
}

enum class ComfortStatus(val label: String, val summary: String) {
    OPTIMAL("Optimal comfort", "Temperature and humidity are right in the ideal zone"),
    WARM("Warm", "Air feels warmer than the target comfort zone"),
    COOL("Cool", "Air is cooler than the target comfort zone"),
    HUMID("Muggy and humid", "Elevated moisture reduces evaporative cooling"),
    DRY("Dry air", "Low humidity may cause dry skin and static")
}

enum class MouldRiskLevel(val label: String, val detail: String) {
    LOW("Low mould risk", "Surface condensation is unlikely"),
    MODERATE("Moderate mould risk", "Watch cold corners and window frames"),
    HIGH("High mould and condensation risk", "Dehumidify or ventilate soon")
}

data class HistoricalPoint(
    val timestampMs: Long,
    val temperatureC: Float,
    val humidityPct: Float,
    val pressureHpa: Float? = null
)

data class RoomConfig(
    val channelId: String,
    val customName: String,
    val archetype: RoomArchetype,
    val alertTempMinC: Float = 15.0f,
    val alertTempMaxC: Float = 27.5f,
    val alertHumidityMaxPct: Float = 68.0f,
    val alertsEnabled: Boolean = true
)

data class SensorRoomState(
    val channelId: String,
    val hardwareLabel: String,
    val roomName: String,
    val archetype: RoomArchetype,
    val temperatureC: Float,
    val humidityPct: Float,
    val relativePressureHpa: Float? = null,
    val absolutePressureHpa: Float? = null,
    val batteryLow: Boolean = false,
    val batteryVoltage: Float? = null,
    val signalQualityPct: Int = 100,
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    val tempTrend1hC: Float = 0f,
    val humidityTrend1hPct: Float = 0f,
    val pressureTrend3hHpa: Float? = null,
    val history: List<HistoricalPoint> = emptyList()
) {
    val dewPointC: Float
        get() = Psychrometrics.computeDewPointC(temperatureC, humidityPct)

    val feelsLikeC: Float
        get() = Psychrometrics.computeFeelsLikeC(temperatureC, humidityPct)

    val absoluteHumidityGm3: Float
        get() = Psychrometrics.computeAbsoluteHumidityGm3(temperatureC, humidityPct)

    val vapourPressureDeficitKpa: Float
        get() = Psychrometrics.computeVpdKpa(temperatureC, humidityPct)

    val comfortStatus: ComfortStatus
        get() = Psychrometrics.evaluateComfort(temperatureC, humidityPct, archetype)

    val mouldRisk: MouldRiskLevel
        get() = Psychrometrics.evaluateMouldRisk(temperatureC, humidityPct, dewPointC)

    val comfortScore: Int
        get() = Psychrometrics.computeComfortScore(temperatureC, humidityPct, archetype)
}

data class HomeAtmosphereSnapshot(
    val rooms: List<SensorRoomState>,
    val source: ConnectionSource,
    val gatewayModel: String = "GW1200",
    val gatewayFirmware: String = "GW1200C_V1.3.4",
    val gatewayIp: String = "",
    val lastSyncMs: Long = System.currentTimeMillis(),
    val syncMessage: String = "Live telemetry active"
) {
    val primaryGatewayRoom: SensorRoomState?
        get() = rooms.firstOrNull { it.channelId == "gateway" } ?: rooms.firstOrNull()

    val outdoorRoom: SensorRoomState?
        get() = rooms.firstOrNull { it.archetype.isOutdoor } ?: rooms.lastOrNull()

    val pressureHpa: Float?
        get() = rooms.firstNotNullOfOrNull { it.relativePressureHpa }

    val pressureTrend3hHpa: Float?
        get() = rooms.firstNotNullOfOrNull { it.pressureTrend3hHpa }

    val pressureForecastText: String
        get() = Psychrometrics.describePressureForecast(pressureHpa, pressureTrend3hHpa)
}

data class SimulationParameters(
    val enabled: Boolean = true,
    val timeOfDayHours: Float = 14.5f,
    val livingRoomTempOffsetC: Float = 0f,
    val livingRoomHumidityOffsetPct: Float = 0f,
    val bedroomTempOffsetC: Float = 0f,
    val bedroomHumidityOffsetPct: Float = 0f,
    val outdoorTempOffsetC: Float = 0f,
    val outdoorHumidityOffsetPct: Float = 0f,
    val pressureOffsetHpa: Float = 0f,
    val gyroscopeTiltEnabled: Boolean = true
)

data class AppSettings(
    val gatewayIp: String = "192.168.1.120",
    val cloudApplicationKey: String = "",
    val cloudApiKey: String = "",
    val cloudDeviceMac: String = "",
    val preferLocalLan: Boolean = true,
    val demoModeEnabled: Boolean = true,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val pollingIntervalMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val ventilationAdviceNotifications: Boolean = true,
    val gyroscopeParallaxEnabled: Boolean = true,
    val roomConfigs: Map<String, RoomConfig> = defaultRoomConfigs()
) {
    companion object {
        fun defaultRoomConfigs(): Map<String, RoomConfig> = mapOf(
            "gateway" to RoomConfig(
                channelId = "gateway",
                customName = "Living room (GW1200)",
                archetype = RoomArchetype.LIVING_ROOM,
                alertTempMinC = 18.0f,
                alertTempMaxC = 26.5f,
                alertHumidityMaxPct = 65.0f
            ),
            "ch1" to RoomConfig(
                channelId = "ch1",
                customName = "Master bedroom (Ch 1)",
                archetype = RoomArchetype.BEDROOM,
                alertTempMinC = 16.0f,
                alertTempMaxC = 24.5f,
                alertHumidityMaxPct = 62.0f
            ),
            "ch2" to RoomConfig(
                channelId = "ch2",
                customName = "Balcony garden (Ch 2)",
                archetype = RoomArchetype.BALCONY_GARDEN,
                alertTempMinC = 2.0f,
                alertTempMaxC = 34.0f,
                alertHumidityMaxPct = 88.0f
            )
        )
    }
}

object Psychrometrics {
    /**
     * Magnus-Tetens formula for dew point in degrees Celsius.
     */
    fun computeDewPointC(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f) / 100f
        val a = 17.625f
        val b = 243.04f
        val alpha = ln(rh) + (a * tempC) / (b + tempC)
        return (b * alpha) / (a - alpha)
    }

    /**
     * Absolute humidity in grams of water vapour per cubic metre of air (g/m³).
     */
    fun computeAbsoluteHumidityGm3(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f)
        val saturationVapourPressureHpa = 6.112f * exp((17.67f * tempC) / (tempC + 243.5f))
        return (saturationVapourPressureHpa * rh * 2.1674f) / (273.15f + tempC)
    }

    /**
     * Vapour pressure deficit (VPD) in kPa, useful for plant health and air drying power.
     */
    fun computeVpdKpa(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f) / 100f
        val svpKpa = 0.61078f * exp((17.27f * tempC) / (tempC + 237.3f))
        return (svpKpa * (1f - rh)).coerceAtLeast(0f)
    }

    /**
     * Feels-like temperature combining Steadman apparent temperature and humidex adjustment.
     */
    fun computeFeelsLikeC(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f)
        val vapourPressureHpa = (rh / 100f) * 6.105f * exp((17.27f * tempC) / (237.7f + tempC))
        return tempC + 0.33f * vapourPressureHpa - 4.0f
    }

    fun evaluateComfort(tempC: Float, humidityPct: Float, archetype: RoomArchetype): ComfortStatus {
        return when {
            humidityPct > archetype.idealHumidityMax + 7f -> ComfortStatus.HUMID
            humidityPct < archetype.idealHumidityMin - 8f -> ComfortStatus.DRY
            tempC > archetype.idealTempMaxC + 1.2f -> ComfortStatus.WARM
            tempC < archetype.idealTempMinC - 1.2f -> ComfortStatus.COOL
            else -> ComfortStatus.OPTIMAL
        }
    }

    fun evaluateMouldRisk(tempC: Float, humidityPct: Float, dewPointC: Float): MouldRiskLevel {
        val dewDepression = tempC - dewPointC
        return when {
            humidityPct >= 72f || dewDepression < 3.2f -> MouldRiskLevel.HIGH
            humidityPct >= 62f || dewDepression < 5.2f -> MouldRiskLevel.MODERATE
            else -> MouldRiskLevel.LOW
        }
    }

    fun computeComfortScore(tempC: Float, humidityPct: Float, archetype: RoomArchetype): Int {
        val tempMid = (archetype.idealTempMinC + archetype.idealTempMaxC) / 2f
        val humMid = (archetype.idealHumidityMin + archetype.idealHumidityMax) / 2f
        val tempPenalty = ((kotlin.math.abs(tempC - tempMid) - 1.5f).coerceAtLeast(0f) * 9f)
        val humPenalty = ((kotlin.math.abs(humidityPct - humMid) - 6f).coerceAtLeast(0f) * 1.6f)
        return (100f - tempPenalty - humPenalty).roundToInt().coerceIn(15, 100)
    }

    /**
     * Compares an indoor room with an outdoor sensor using absolute humidity (g/m³)
     * and temperature to produce actionable window ventilation advice.
     */
    fun buildVentilationAdvice(
        indoor: SensorRoomState,
        outdoor: SensorRoomState?,
        tempUnit: TempUnit
    ): String {
        if (indoor.archetype.isOutdoor) {
            return "Outdoor reference sensor: ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³ absolute moisture"
        }
        if (outdoor == null || outdoor.channelId == indoor.channelId) {
            return if (indoor.humidityPct > 60f) {
                "Indoor air holds ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³ moisture; ventilate if outdoor air is drier"
            } else {
                "Indoor moisture is balanced at ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³"
            }
        }
        val absDiff = indoor.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3
        val tempDiff = indoor.temperatureC - outdoor.temperatureC

        return when {
            indoor.humidityPct >= 58f && absDiff >= 1.2f ->
                "Open window: outdoor air carries ${String.format("%.1f", absDiff)} g/m³ less water vapour and will dry this room"
            indoor.temperatureC > indoor.archetype.idealTempMaxC && tempDiff >= 1.5f && absDiff >= -0.8f ->
                "Open window: outdoor air is ${tempUnit.format( outdoor.temperatureC )} and will naturally cool the room"
            absDiff < -1.5f && indoor.humidityPct >= 52f ->
                "Keep windows closed: outdoor air has ${String.format("%.1f", -absDiff)} g/m³ more moisture than indoors"
            else ->
                "Indoor and outdoor moisture are well balanced (indoor ${String.format("%.1f", indoor.absoluteHumidityGm3)} vs outdoor ${String.format("%.1f", outdoor.absoluteHumidityGm3)} g/m³)"
        }
    }

    fun describePressureForecast(pressureHpa: Float?, trend3hHpa: Float?): String {
        if (pressureHpa == null) return "Barometric sensor unavailable"
        val trend = trend3hHpa ?: 0f
        return when {
            trend <= -2.2f -> "Pressure falling rapidly (${String.format("%+.1f", trend)} hPa/3h): rain or wind front approaching"
            trend <= -0.8f -> "Pressure easing (${String.format("%+.1f", trend)} hPa/3h): cloud cover increasing"
            trend >= 1.8f -> "Pressure rising briskly (${String.format("%+.1f", trend)} hPa/3h): clearing skies and drier air"
            pressureHpa >= 1020f -> "High pressure (${String.format("%.1f", pressureHpa)} hPa): settled and fair conditions"
            pressureHpa <= 1004f -> "Low pressure (${String.format("%.1f", pressureHpa)} hPa): unsettled weather likely"
            else -> "Steady barometer (${String.format("%.1f", pressureHpa)} hPa): stable local conditions"
        }
    }
}
