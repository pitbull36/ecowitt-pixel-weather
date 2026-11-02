package com.dariopittera.ecowittpixel.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

enum class RoomArchetype(
    val id: String,
    val title: String,
    val subtitle: String,
    val isOutdoor: Boolean,
    val idealTempMinC: Float,
    val idealTempMaxC: Float,
    val idealHumidityMin: Float,
    val idealHumidityMax: Float,
    val scientificAlertTempMinC: Float,
    val scientificAlertTempMaxC: Float,
    val scientificAlertHumidityMaxPct: Float,
    val laymanRule: String,
    val standardCitation: String,
    val standardRationale: String
) {
    LIVING_ROOM(
        id = "living_room",
        title = "Living room",
        subtitle = "Lounge with sofa, widescreen TV, and library bookcase",
        isOutdoor = false,
        idealTempMinC = 20.0f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 60f,
        scientificAlertTempMinC = 18.0f,
        scientificAlertTempMaxC = 26.0f,
        scientificAlertHumidityMaxPct = 68.0f,
        laymanRule = "Daytime lounge: best kept warm and cosy while relaxing",
        standardCitation = "ASHRAE 55, ASHRAE 160, and WHO",
        standardRationale = "Sitting still in a lounge feels most comfortable when the air is gently warm and neither dry nor muggy (ideal 20–23.5°C, 40–60% RH; WHO min 18°C; ASHRAE 160 mould alert only if >68% RH for 4+ hours)"
    ),
    BEDROOM(
        id = "bedroom",
        title = "Master bedroom",
        subtitle = "Sleep sanctuary optimised for cooler night air",
        isOutdoor = false,
        idealTempMinC = 16.5f,
        idealTempMaxC = 19.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 58f,
        scientificAlertTempMinC = 15.5f,
        scientificAlertTempMaxC = 24.0f,
        scientificAlertHumidityMaxPct = 68.0f,
        laymanRule = "Sleep room: slightly cooler air helps you sleep more deeply",
        standardCitation = "CIBSE Sleep Standard and ASHRAE 160",
        standardRationale = "Your body needs to lower its core temperature to enter deep sleep, and breathing overnight naturally raises room moisture for a few hours (ideal 16.5–19.5°C, 40–58% RH; CIBSE sleep guide; ASHRAE 160 alert only if >68% RH for 4+ hours)"
    ),
    STUDY(
        id = "study",
        title = "Home office",
        subtitle = "Study desk with dual monitors and bookshelf",
        isOutdoor = false,
        idealTempMinC = 20.0f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 58f,
        scientificAlertTempMinC = 18.5f,
        scientificAlertTempMaxC = 25.5f,
        scientificAlertHumidityMaxPct = 68.0f,
        laymanRule = "Desk and study: steady warmth prevents drowsiness and dry eyes",
        standardCitation = "EN 16798-1 Category I and ASHRAE 160",
        standardRationale = "Moderate warmth and balanced moisture keep your eyes comfortable at screens and prevent stuffy afternoon fatigue (ideal 20–23.5°C, 40–58% RH; EN 16798-1 Category I; ASHRAE 160 alert >68% RH for 4+ hours)"
    ),
    KITCHEN(
        id = "kitchen",
        title = "Kitchen and dining",
        subtitle = "Island counter with higher heat and steam variance",
        isOutdoor = false,
        idealTempMinC = 18.0f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 38f,
        idealHumidityMax = 65f,
        scientificAlertTempMinC = 16.5f,
        scientificAlertTempMaxC = 27.0f,
        scientificAlertHumidityMaxPct = 72.0f,
        laymanRule = "Cooking area: tolerates short bursts of heat and steam from meals",
        standardCitation = "CIBSE Guide A Kitchen Standard",
        standardRationale = "Moving around and cooking naturally add heat and steam; only prolonged dampness after cooking needs extra extraction (ideal 18–23.5°C, 38–65% RH; CIBSE Guide A; ASHRAE 160 alert >72% RH for 4+ hours)"
    ),
    BATHROOM(
        id = "bathroom",
        title = "Bathroom",
        subtitle = "Tiled suite with mirror condensation tracking",
        isOutdoor = false,
        idealTempMinC = 21.0f,
        idealTempMaxC = 24.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 70f,
        scientificAlertTempMinC = 19.5f,
        scientificAlertTempMaxC = 27.5f,
        scientificAlertHumidityMaxPct = 78.0f,
        laymanRule = "Bath and shower: warmer air stops steam turning into wall condensation",
        standardCitation = "ASHRAE 160 Mould Germination Model",
        standardRationale = "Keeping the bathroom slightly warmer stops shower steam condensing on cold tiles and mirrors; run the fan if dampness lingers for hours (ideal 21–24.5°C, 40–70% RH; ASHRAE 160 water activity aw < 0.80, alert >78% RH for 4+ hours)"
    ),
    BALCONY_GARDEN(
        id = "balcony_garden",
        title = "Balcony and garden",
        subtitle = "Outdoor terrace with sky dome and foliage",
        isOutdoor = true,
        idealTempMinC = 10.0f,
        idealTempMaxC = 26.0f,
        idealHumidityMin = 35f,
        idealHumidityMax = 75f,
        scientificAlertTempMinC = 2.0f,
        scientificAlertTempMaxC = 32.0f,
        scientificAlertHumidityMaxPct = 88.0f,
        laymanRule = "Outdoor terrace: tracks weather for plants and window ventilation",
        standardCitation = "WMO and RHS Horticultural Standard",
        standardRationale = "Watches outdoor conditions to protect balcony plants from frost or heatwave stress and compare outdoor moisture against indoor rooms (ideal 10–26°C, frost alert <2°C, heat alert >32°C, leaf dampness >88% RH • WMO and RHS)"
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

enum class NotificationMetric(val id: String, val label: String, val unitSuffix: String) {
    TEMPERATURE("temperature", "Temperature", "°C"),
    HUMIDITY("humidity", "Humidity", "% RH"),
    DEW_POINT("dew_point", "Dew point", "°C"),
    PRESSURE("pressure", "Barometric pressure", "hPa"),
    COMFORT_SCORE("comfort_score", "Comfort score", "/100");

    companion object {
        fun fromId(id: String): NotificationMetric =
            entries.firstOrNull { it.id == id } ?: TEMPERATURE
    }
}

enum class ComparisonOperator(val id: String, val symbol: String, val label: String) {
    BELOW("below", "<", "Falls below"),
    ABOVE("above", ">", "Rises above");

    fun matches(actual: Float, threshold: Float): Boolean =
        if (this == BELOW) actual <= threshold else actual >= threshold

    companion object {
        fun fromId(id: String): ComparisonOperator =
            entries.firstOrNull { it.id == id } ?: ABOVE
    }
}

data class CustomAlertCriterion(
    val id: String,
    val channelId: String,
    val metric: NotificationMetric,
    val operator: ComparisonOperator,
    val threshold: Float,
    val customMessage: String,
    val enabled: Boolean = true
)

data class RoomConfig(
    val channelId: String,
    val customName: String,
    val archetype: RoomArchetype,
    val alertTempMinC: Float = archetype.scientificAlertTempMinC,
    val alertTempMaxC: Float = archetype.scientificAlertTempMaxC,
    val alertHumidityMinPct: Float = 30.0f,
    val alertHumidityMaxPct: Float = archetype.scientificAlertHumidityMaxPct,
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
        get() = Psychrometrics.evaluateMouldRisk(temperatureC, humidityPct, dewPointC, history)

    val comfortScore: Int
        get() = Psychrometrics.computeComfortScore(temperatureC, humidityPct, archetype)
}

data class LocalOutdoorWeather(
    val locationName: String,
    val temperatureC: Float,
    val feelsLikeC: Float,
    val humidityPct: Float,
    val dewPointC: Float,
    val pressureHpa: Float,
    val precipitationMm: Float = 0f,
    val windSpeedKmh: Float = 0f,
    val weatherCode: Int = 0,
    val conditionLabel: String = "Clear sky",
    val isRainingOrSnowing: Boolean = false,
    val sourceLabel: String = "Open-Meteo local weather",
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val absoluteHumidityGm3: Float
        get() = Psychrometrics.computeAbsoluteHumidityGm3(temperatureC, humidityPct)
}

data class HomeAtmosphereSnapshot(
    val rooms: List<SensorRoomState>,
    val source: ConnectionSource,
    val gatewayModel: String = "GW1200",
    val gatewayFirmware: String = "GW1200C_V1.3.4",
    val gatewayIp: String = "",
    val lastSyncMs: Long = System.currentTimeMillis(),
    val syncMessage: String = "Live telemetry active",
    val localWeather: LocalOutdoorWeather? = null
) {
    val primaryGatewayRoom: SensorRoomState?
        get() = rooms.firstOrNull { it.channelId == "gateway" } ?: rooms.firstOrNull()

    /**
     * Strictly returns a physical sensor configured as an outdoor archetype (never an indoor room).
     */
    val outdoorRoom: SensorRoomState?
        get() = rooms.firstOrNull { it.archetype.isOutdoor }

    /**
     * Combines any physical outdoor Ecowitt sensor with local meteorological weather (Open-Meteo)
     * and the GW1200 barometer so the app always has accurate outdoor temperature, humidity,
     * pressure, precipitation, and wind context even when all Ecowitt sensors are indoors.
     */
    val effectiveOutdoorWeather: LocalOutdoorWeather?
        get() {
            val hwOutdoor = outdoorRoom
            val met = localWeather
            val baro = rooms.firstNotNullOfOrNull { it.relativePressureHpa } ?: met?.pressureHpa ?: 1013.2f
            return when {
                hwOutdoor != null && met != null -> met.copy(
                    temperatureC = hwOutdoor.temperatureC,
                    feelsLikeC = hwOutdoor.feelsLikeC,
                    humidityPct = hwOutdoor.humidityPct,
                    dewPointC = hwOutdoor.dewPointC,
                    pressureHpa = baro,
                    sourceLabel = "${hwOutdoor.roomName} + ${met.locationName}"
                )
                hwOutdoor != null -> LocalOutdoorWeather(
                    locationName = hwOutdoor.roomName,
                    temperatureC = hwOutdoor.temperatureC,
                    feelsLikeC = hwOutdoor.feelsLikeC,
                    humidityPct = hwOutdoor.humidityPct,
                    dewPointC = hwOutdoor.dewPointC,
                    pressureHpa = baro,
                    conditionLabel = if (hwOutdoor.humidityPct >= 88f) "Humid outdoor air" else "Outdoor sensor",
                    isRainingOrSnowing = false,
                    sourceLabel = hwOutdoor.roomName,
                    updatedAtMs = hwOutdoor.lastUpdatedMs
                )
                met != null -> met.copy(pressureHpa = baro)
                else -> null
            }
        }

    val pressureHpa: Float?
        get() = rooms.firstNotNullOfOrNull { it.relativePressureHpa } ?: localWeather?.pressureHpa

    val pressureTrend3hHpa: Float?
        get() = rooms.firstNotNullOfOrNull { it.pressureTrend3hHpa }

    val pressureForecastText: String
        get() = Psychrometrics.describePressureForecast(pressureHpa, pressureTrend3hHpa)

    val isSimulatedOrPreview: Boolean
        get() = source == ConnectionSource.DEMO_SIMULATION ||
            gatewayFirmware == "GW1200C_V1.3.4" ||
            syncMessage.contains("simulation", ignoreCase = true) ||
            syncMessage.contains("preview", ignoreCase = true)
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
    val weatherLocationQuery: String = "",
    val preferLocalLan: Boolean = true,
    val demoModeEnabled: Boolean = true,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val pollingIntervalMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val ventilationAdviceNotifications: Boolean = true,
    val mouldRiskNotifications: Boolean = true,
    val pressureDropNotifications: Boolean = true,
    val lowBatteryNotifications: Boolean = true,
    val gyroscopeParallaxEnabled: Boolean = true,
    val roomConfigs: Map<String, RoomConfig> = defaultRoomConfigs(),
    val customCriteria: List<CustomAlertCriterion> = defaultCustomCriteria()
) {
    fun applyToSnapshot(snapshot: HomeAtmosphereSnapshot): HomeAtmosphereSnapshot {
        val syncedRooms = snapshot.rooms.map { room ->
            val cfg = roomConfigs[room.channelId]
            if (cfg != null && cfg.customName.isNotBlank()) {
                room.copy(
                    roomName = cfg.customName,
                    archetype = cfg.archetype
                )
            } else {
                room
            }
        }
        return snapshot.copy(rooms = syncedRooms)
    }

    companion object {
        fun defaultRoomConfigs(): Map<String, RoomConfig> = mapOf(
            "gateway" to RoomConfig(
                channelId = "gateway",
                customName = "Living room (GW1200)",
                archetype = RoomArchetype.LIVING_ROOM
            ),
            "ch1" to RoomConfig(
                channelId = "ch1",
                customName = "Master bedroom (Ch 1)",
                archetype = RoomArchetype.BEDROOM
            ),
            "ch2" to RoomConfig(
                channelId = "ch2",
                customName = "Balcony garden (Ch 2)",
                archetype = RoomArchetype.BALCONY_GARDEN
            )
        )

        fun defaultCustomCriteria(): List<CustomAlertCriterion> = listOf(
            CustomAlertCriterion(
                id = "crit_cold_living",
                channelId = "gateway",
                metric = NotificationMetric.TEMPERATURE,
                operator = ComparisonOperator.BELOW,
                threshold = 19.0f,
                customMessage = "Living room below 19°C: check heating or close draughty windows",
                enabled = true
            ),
            CustomAlertCriterion(
                id = "crit_humid_bedroom",
                channelId = "ch1",
                metric = NotificationMetric.HUMIDITY,
                operator = ComparisonOperator.ABOVE,
                threshold = 68.0f,
                customMessage = "Bedroom humidity sustained above 68%: ventilate or run dehumidifier",
                enabled = true
            ),
            CustomAlertCriterion(
                id = "crit_frost_balcony",
                channelId = "ch2",
                metric = NotificationMetric.TEMPERATURE,
                operator = ComparisonOperator.BELOW,
                threshold = 3.0f,
                customMessage = "Outdoor frost warning: protect balcony plants",
                enabled = true
            )
        )
    }
}

object Psychrometrics {
    fun computeDewPointC(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f) / 100f
        val a = 17.625f
        val b = 243.04f
        val alpha = ln(rh) + (a * tempC) / (b + tempC)
        return (b * alpha) / (a - alpha)
    }

    fun computeAbsoluteHumidityGm3(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f)
        val saturationVapourPressureHpa = 6.112f * exp((17.67f * tempC) / (tempC + 243.5f))
        return (saturationVapourPressureHpa * rh * 2.1674f) / (273.15f + tempC)
    }

    fun computeVpdKpa(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f) / 100f
        val svpKpa = 0.61078f * exp((17.27f * tempC) / (tempC + 237.3f))
        return (svpKpa * (1f - rh)).coerceAtLeast(0f)
    }

    fun computeFeelsLikeC(tempC: Float, humidityPct: Float): Float {
        val rh = humidityPct.coerceIn(1f, 100f)
        val vapourPressureHpa = (rh / 100f) * 6.105f * exp((17.27f * tempC) / (237.7f + tempC))
        return tempC + 0.33f * vapourPressureHpa - 4.0f
    }

    fun evaluateComfort(tempC: Float, humidityPct: Float, archetype: RoomArchetype): ComfortStatus {
        return when {
            humidityPct > archetype.idealHumidityMax + 5f -> ComfortStatus.HUMID
            humidityPct < archetype.idealHumidityMin - 8f -> ComfortStatus.DRY
            tempC > archetype.idealTempMaxC + 1.2f -> ComfortStatus.WARM
            tempC < archetype.idealTempMinC - 1.2f -> ComfortStatus.COOL
            else -> ComfortStatus.OPTIMAL
        }
    }

    fun evaluateMouldRisk(
        tempC: Float,
        humidityPct: Float,
        dewPointC: Float,
        history: List<HistoricalPoint> = emptyList()
    ): MouldRiskLevel {
        val dewDepression = tempC - dewPointC
        val sustainedAbove70 = isHumiditySustainedFromHistory(
            currentHumidityPct = humidityPct,
            history = history,
            thresholdPct = 70f,
            minSustainedHours = 6.0f
        )
        return when {
            dewDepression < 2.2f || humidityPct >= 76f || (humidityPct >= 70f && sustainedAbove70) ->
                MouldRiskLevel.HIGH
            humidityPct >= 66f || dewDepression < 4.0f ->
                MouldRiskLevel.MODERATE
            else ->
                MouldRiskLevel.LOW
        }
    }

    /**
     * Checks whether humidity has remained elevated above [thresholdPct] continuously over
     * [minSustainedHours] (per ASHRAE 160 / VTT mould germination models, brief spikes do not
     * cause mould; moisture must persist for hours). If humidity reaches an acute condensation
     * level (>= [thresholdPct] + 10% or >= 78% RH), it triggers immediately.
     */
    fun isHumiditySustained(
        room: SensorRoomState,
        thresholdPct: Float,
        minSustainedHours: Float = 4.0f
    ): Boolean = isHumiditySustainedFromHistory(
        currentHumidityPct = room.humidityPct,
        history = room.history,
        thresholdPct = thresholdPct,
        minSustainedHours = minSustainedHours
    )

    fun isHumiditySustainedFromHistory(
        currentHumidityPct: Float,
        history: List<HistoricalPoint>,
        thresholdPct: Float,
        minSustainedHours: Float = 4.0f
    ): Boolean {
        if (currentHumidityPct < thresholdPct) return false
        // Acute condensation spike (>= +10% RH above threshold or >= 78% RH) alerts immediately
        if (currentHumidityPct >= (thresholdPct + 10f).coerceAtMost(82f) || currentHumidityPct >= 78f) {
            return true
        }
        if (minSustainedHours <= 0.1f) return true

        val nowMs = history.maxOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
        val windowMs = (minSustainedHours * 3600_000f).toLong()
        val windowStartMs = nowMs - windowMs
        val recentPoints = history.filter { it.timestampMs >= windowStartMs }

        // Require history spanning at least 75% of the sustained window before firing a non-acute alert
        val earliestInWindow = recentPoints.minOfOrNull { it.timestampMs } ?: return false
        if (nowMs - earliestInWindow < (windowMs * 0.75f).toLong() || recentPoints.size < 3) {
            return false
        }

        // Allow up to 15% brief dips (e.g. a momentary door opening) with a 1.0% hysteresis band
        val elevatedCount = recentPoints.count { it.humidityPct >= (thresholdPct - 1.0f) }
        return elevatedCount.toFloat() / recentPoints.size.toFloat() >= 0.85f
    }

    fun computeComfortScore(tempC: Float, humidityPct: Float, archetype: RoomArchetype): Int {
        val tempMid = (archetype.idealTempMinC + archetype.idealTempMaxC) / 2f
        val humMid = (archetype.idealHumidityMin + archetype.idealHumidityMax) / 2f
        val tempPenalty = ((kotlin.math.abs(tempC - tempMid) - 1.5f).coerceAtLeast(0f) * 9f)
        val humPenalty = ((kotlin.math.abs(humidityPct - humMid) - 6f).coerceAtLeast(0f) * 1.6f)
        return (100f - tempPenalty - humPenalty).roundToInt().coerceIn(15, 100)
    }

    fun wmoCodeToCondition(code: Int): Pair<String, Boolean> {
        return when (code) {
            0 -> "Clear sky" to false
            1 -> "Mainly clear" to false
            2 -> "Partly cloudy" to false
            3 -> "Overcast" to false
            45, 48 -> "Foggy" to false
            51, 53, 55 -> "Light drizzle" to true
            56, 57 -> "Freezing drizzle" to true
            61, 63 -> "Rain" to true
            65 -> "Heavy rain" to true
            66, 67 -> "Freezing rain" to true
            71, 73, 75, 77 -> "Snowfall" to true
            80, 81, 82 -> "Rain showers" to true
            85, 86 -> "Snow showers" to true
            95, 96, 99 -> "Thunderstorm" to true
            else -> "Cloudy" to false
        }
    }

    /**
     * Checks whether opening a window is actually sensible given the indoor room temperature
     * and local outdoor meteorological conditions (rain/snow, gale wind, freezing cold, or summer heat).
     */
    fun canRecommendOpeningWindow(
        indoor: SensorRoomState,
        outdoor: LocalOutdoorWeather
    ): Boolean {
        if (outdoor.isRainingOrSnowing || outdoor.precipitationMm >= 0.2f) return false
        if (outdoor.windSpeedKmh >= 38f) return false
        if (outdoor.temperatureC < 5.5f) return false
        // Do not tell the user to open windows to dry a room if the room is already cold
        if (indoor.temperatureC < indoor.archetype.idealTempMinC - 0.3f && outdoor.temperatureC < indoor.temperatureC) {
            return false
        }
        // Do not tell the user to open windows if outdoor air is warmer than the room's upper comfort limit
        if (outdoor.temperatureC > indoor.archetype.idealTempMaxC + 0.8f && outdoor.temperatureC > indoor.temperatureC + 0.5f) {
            return false
        }
        return true
    }

    fun buildVentilationAdvice(
        indoor: SensorRoomState,
        outdoor: SensorRoomState?,
        tempUnit: TempUnit,
        localOutdoor: LocalOutdoorWeather? = null
    ): String {
        if (indoor.archetype.isOutdoor) {
            val metNote = if (localOutdoor != null) {
                ", local weather in ${localOutdoor.locationName}: ${localOutdoor.conditionLabel.lowercase()}, ${String.format(java.util.Locale.UK, "%.0f", localOutdoor.pressureHpa)} hPa"
            } else ""
            return "Outdoor sensor reading: ${indoor.roomName} is at ${tempUnit.format(indoor.temperatureC)} with ${indoor.humidityPct.toInt()}% humidity (${String.format(java.util.Locale.UK, "%.1f", indoor.absoluteHumidityGm3)} g/m³ water vapour, dew point ${tempUnit.format(indoor.dewPointC)}$metNote)"
        }

        val effectiveOut: LocalOutdoorWeather? = localOutdoor ?: if (outdoor != null && outdoor.channelId != indoor.channelId) {
            LocalOutdoorWeather(
                locationName = outdoor.roomName,
                temperatureC = outdoor.temperatureC,
                feelsLikeC = outdoor.feelsLikeC,
                humidityPct = outdoor.humidityPct,
                dewPointC = outdoor.dewPointC,
                pressureHpa = outdoor.relativePressureHpa ?: 1013.2f,
                conditionLabel = "Outdoor sensor",
                isRainingOrSnowing = false,
                sourceLabel = outdoor.roomName
            )
        } else null

        val targetTempRange = "${tempUnit.format(indoor.archetype.idealTempMinC)}–${tempUnit.format(indoor.archetype.idealTempMaxC)}"
        val targetHumRange = "${indoor.archetype.idealHumidityMin.toInt()}–${indoor.archetype.idealHumidityMax.toInt()}% RH"

        if (effectiveOut == null) {
            return if (indoor.humidityPct >= indoor.archetype.scientificAlertHumidityMaxPct) {
                "Air feels quite damp: run a dehumidifier or open a window if it is dry and mild outside (indoor ${indoor.humidityPct.toInt()}% RH / ${String.format(java.util.Locale.UK, "%.1f", indoor.absoluteHumidityGm3)} g/m³ water vapour; alert threshold >${indoor.archetype.scientificAlertHumidityMaxPct.toInt()}% RH)"
            } else {
                "Room feels comfortable and moisture is well balanced (indoor ${tempUnit.format(indoor.temperatureC)}, ${indoor.humidityPct.toInt()}% RH / ${String.format(java.util.Locale.UK, "%.1f", indoor.absoluteHumidityGm3)} g/m³; target $targetTempRange, $targetHumRange)"
            }
        }

        val absDiff = indoor.absoluteHumidityGm3 - effectiveOut.absoluteHumidityGm3
        val tempDiff = indoor.temperatureC - effectiveOut.temperatureC
        val outSci = "outdoor ${effectiveOut.locationName}: ${tempUnit.format(effectiveOut.temperatureC)}, ${effectiveOut.humidityPct.toInt()}% RH / ${String.format(java.util.Locale.UK, "%.1f", effectiveOut.absoluteHumidityGm3)} g/m³, ${effectiveOut.conditionLabel.lowercase()}"
        val inSci = "indoor ${tempUnit.format(indoor.temperatureC)}, ${indoor.humidityPct.toInt()}% RH / ${String.format(java.util.Locale.UK, "%.1f", indoor.absoluteHumidityGm3)} g/m³"

        return when {
            // 1. High indoor humidity above the room's scientific threshold
            indoor.humidityPct >= indoor.archetype.scientificAlertHumidityMaxPct -> {
                when {
                    absDiff < 1.2f ->
                        "Keep windows closed and use a dehumidifier: the room is damp, and the outdoor air is just as humid so opening a window will not dry the room ($inSci vs $outSci)"
                    effectiveOut.isRainingOrSnowing || effectiveOut.precipitationMm >= 0.2f ->
                        "Keep windows closed and run a dehumidifier or extractor fan: the room is humid, but it is ${effectiveOut.conditionLabel.lowercase()} outside so opening windows will not help right now ($inSci; $outSci)"
                    indoor.temperatureC < indoor.archetype.idealTempMinC ->
                        "Turn on the heating or a dehumidifier instead of opening windows: the room is both cool and damp, and warming the air to ${tempUnit.format(indoor.archetype.idealTempMinC)} will help dry it without chilling the walls ($inSci; $outSci)"
                    effectiveOut.temperatureC < 6.0f ->
                        "Use a dehumidifier or air the room for just 3 minutes: it is very cold outside, so leaving windows open would chill the walls and cause condensation ($inSci; $outSci)"
                    effectiveOut.temperatureC > indoor.archetype.idealTempMaxC + 0.8f && effectiveOut.temperatureC > indoor.temperatureC ->
                        "Keep windows closed and use air conditioning or a dehumidifier: the room is humid, but it is hot outside ($inSci; $outSci)"
                    effectiveOut.windSpeedKmh >= 38f ->
                        "Use a trickle vent or dehumidifier instead of opening windows wide: it is very windy outside ($inSci; $outSci, wind ${effectiveOut.windSpeedKmh.toInt()} km/h)"
                    effectiveOut.temperatureC < 13.0f ->
                        "Air the room briefly for 5 to 10 minutes: indoor air is damp and outdoor air is drier, so a quick airing will clear the moisture without losing room warmth ($inSci vs $outSci, −${String.format(java.util.Locale.UK, "%.1f", absDiff)} g/m³ drier outside)"
                    else ->
                        "Open a window to let damp air out: this room is humid and the outdoor air is drier, so ventilating will naturally lower moisture ($inSci vs $outSci, −${String.format(java.util.Locale.UK, "%.1f", absDiff)} g/m³ drier outside)"
                }
            }

            // 2. Room is warmer than ideal range for this room type
            indoor.temperatureC > indoor.archetype.idealTempMaxC + 0.5f -> {
                when {
                    tempDiff >= 1.5f && effectiveOut.temperatureC >= 11.0f && !effectiveOut.isRainingOrSnowing && absDiff >= -1.0f ->
                        "Open a window to cool the room down: it is warmer than ideal for a ${indoor.archetype.title.lowercase()}, and the cooler outdoor breeze will bring it into the comfort zone ($inSci vs target $targetTempRange; $outSci)"
                    effectiveOut.isRainingOrSnowing && tempDiff >= 1.5f ->
                        "Room is warmer than ideal for a ${indoor.archetype.title.lowercase()}, but it is ${effectiveOut.conditionLabel.lowercase()} outside: use a fan or a cracked trickle vent ($inSci vs target $targetTempRange; $outSci)"
                    effectiveOut.temperatureC >= indoor.temperatureC ->
                        "Keep windows and blinds closed: the room is warm, and it is even warmer outside so keeping heat out is best ($inSci vs target $targetTempRange; $outSci)"
                    else ->
                        "Room is slightly warmer than the ideal ${indoor.archetype.title.lowercase()} range ($inSci vs target $targetTempRange; $outSci)"
                }
            }

            // 3. Room is cooler than ideal range for this room type
            indoor.temperatureC < indoor.archetype.idealTempMinC - 0.5f -> {
                if (effectiveOut.temperatureC > indoor.temperatureC + 2.0f && !effectiveOut.isRainingOrSnowing && absDiff >= -1.0f) {
                    "Room is cooler than ideal for a ${indoor.archetype.title.lowercase()}, while it is milder outside ($inSci vs target $targetTempRange; $outSci)"
                } else {
                    "Keep windows closed and check the heating: this room is cooler than the ideal ${indoor.archetype.title.lowercase()} range ($inSci vs target $targetTempRange; $outSci)"
                }
            }

            // 4. Indoor air is dry (< 35% RH)
            indoor.humidityPct < 35.0f -> {
                if (absDiff <= -1.5f && effectiveOut.temperatureC in 14f..24f && !effectiveOut.isRainingOrSnowing) {
                    "Open a window briefly to let fresh moisture in: the air in this room is quite dry, and the mild outdoor air carries more natural moisture ($inSci vs $outSci)"
                } else {
                    "Air in this room is quite dry, which can cause dry skin or eyes: consider using a humidifier ($inSci vs target $targetHumRange; $outSci)"
                }
            }

            // 5. Slightly above ideal humidity (e.g. 60-67% RH) — reassure the user in plain language!
            indoor.humidityPct > indoor.archetype.idealHumidityMax -> {
                if (absDiff >= 1.2f && canRecommendOpeningWindow(indoor, effectiveOut)) {
                    "Room feels fine with slightly elevated humidity, which is harmless over a few hours; you can crack a window if you want drier air ($inSci; mould alert only above ${indoor.archetype.scientificAlertHumidityMaxPct.toInt()}% RH for 4h+; $outSci)"
                } else {
                    "Room feels fine with slightly elevated humidity, which is completely safe over short periods; keep windows closed as outdoor air is not drier ($inSci; mould alert only above ${indoor.archetype.scientificAlertHumidityMaxPct.toInt()}% RH for 4h+; $outSci)"
                }
            }

            // 6. Outdoor air is much more humid while indoor humidity is moderate
            absDiff < -1.5f && indoor.humidityPct >= 52f ->
                "Room is comfortable right now; keep windows closed if you want to keep out the damper outdoor air ($inSci vs $outSci, +${String.format(java.util.Locale.UK, "%.1f", -absDiff)} g/m³ more moisture outside)"

            // 7. Everything is right in the ideal comfort zone
            else ->
                "Everything feels comfortable and healthy for your ${indoor.archetype.title.lowercase()}, so no action is needed ($inSci, target $targetTempRange and $targetHumRange; $outSci)"
        }
    }

    fun describePressureForecast(pressureHpa: Float?, trend3hHpa: Float?): String {
        if (pressureHpa == null) return "Barometric sensor unavailable"
        val trend = trend3hHpa ?: 0f
        return when {
            trend <= -2.2f -> "Rain or wind front likely approaching soon (pressure falling rapidly: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa, ${String.format(java.util.Locale.UK, "%+.1f", trend)} hPa/3h)"
            trend <= -0.8f -> "Cloud cover likely increasing (pressure easing: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa, ${String.format(java.util.Locale.UK, "%+.1f", trend)} hPa/3h)"
            trend >= 1.8f -> "Skies clearing with drier, fairer weather ahead (pressure rising briskly: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa, ${String.format(java.util.Locale.UK, "%+.1f", trend)} hPa/3h)"
            pressureHpa >= 1020f -> "Settled, calm, and fair weather outdoors (high pressure: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa)"
            pressureHpa <= 1004f -> "Changeable, unsettled, or wet weather outdoors (low pressure: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa)"
            else -> "Stable local weather conditions outdoors (steady barometer: ${String.format(java.util.Locale.UK, "%.1f", pressureHpa)} hPa)"
        }
    }
}

enum class AlertSeverity(
    val affectsTopTempArc: Boolean,
    val affectsBottomHumidityArc: Boolean
) {
    HIGH_TEMP(affectsTopTempArc = true, affectsBottomHumidityArc = false),
    LOW_TEMP(affectsTopTempArc = true, affectsBottomHumidityArc = false),
    HIGH_HUMIDITY(affectsTopTempArc = false, affectsBottomHumidityArc = true),
    LOW_HUMIDITY(affectsTopTempArc = false, affectsBottomHumidityArc = true),
    MOULD_RISK(affectsTopTempArc = false, affectsBottomHumidityArc = true),
    VENTILATION_ADVICE(affectsTopTempArc = false, affectsBottomHumidityArc = true),
    PRESSURE_DROP(affectsTopTempArc = true, affectsBottomHumidityArc = true),
    LOW_BATTERY(affectsTopTempArc = true, affectsBottomHumidityArc = false),
    CUSTOM_CRITERION(affectsTopTempArc = true, affectsBottomHumidityArc = true)
}

data class RoomAlertBreach(
    val key: String,
    val notifId: Int,
    val channelId: String,
    val roomName: String,
    val severity: AlertSeverity,
    val shortBadge: String,
    val title: String,
    val body: String,
    val affectsTopTempArc: Boolean = severity.affectsTopTempArc,
    val affectsBottomHumidityArc: Boolean = severity.affectsBottomHumidityArc
)

object AlertEvaluator {
    fun evaluateBreaches(
        snapshot: HomeAtmosphereSnapshot,
        settings: AppSettings
    ): List<RoomAlertBreach> {
        val breaches = mutableListOf<RoomAlertBreach>()
        val outdoor = snapshot.effectiveOutdoorWeather

        snapshot.rooms.forEachIndexed { idx, room ->
            if (settings.mouldRiskNotifications && !room.archetype.isOutdoor && room.mouldRisk == MouldRiskLevel.HIGH) {
                breaches.add(
                    RoomAlertBreach(
                        key = "mould_risk_${room.channelId}",
                        notifId = 3000 + idx,
                        channelId = room.channelId,
                        roomName = room.roomName,
                        severity = AlertSeverity.MOULD_RISK,
                        shortBadge = "Mould risk",
                        title = "${room.roomName}: high mould and condensation risk",
                        body = "Dew point ${settings.tempUnit.format(room.dewPointC)} is close to room temperature (${settings.tempUnit.format(room.temperatureC)} at ${room.humidityPct.toInt()}% RH)."
                    )
                )
            }

            if (settings.ventilationAdviceNotifications && !room.archetype.isOutdoor && outdoor != null) {
                val absDiff = room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3
                val humLimit = room.archetype.scientificAlertHumidityMaxPct
                val humSustained = Psychrometrics.isHumiditySustained(
                    room = room,
                    thresholdPct = humLimit,
                    minSustainedHours = 4.0f
                )
                val canOpenWindow = Psychrometrics.canRecommendOpeningWindow(room, outdoor)
                if (room.humidityPct >= humLimit && humSustained && absDiff >= 1.5f && canOpenWindow) {
                    val isChillyOut = outdoor.temperatureC < 13.0f
                    breaches.add(
                        RoomAlertBreach(
                            key = "vent_advice_${room.channelId}",
                            notifId = 4000 + idx,
                            channelId = room.channelId,
                            roomName = room.roomName,
                            severity = AlertSeverity.VENTILATION_ADVICE,
                            shortBadge = if (isChillyOut) "Air 5–10m" else "Open window",
                            title = if (isChillyOut) {
                                "Brief 5–10 min ventilation in ${room.roomName}"
                            } else {
                                "Open window in ${room.roomName}"
                            },
                            body = String.format(
                                java.util.Locale.UK,
                                "Indoor humidity has remained at %.0f%% RH (≥%.0f%% for 4h+). Outdoor air in %s (%s, %.0f%% RH, %s) carries %.1f g/m³ less moisture.",
                                room.humidityPct,
                                humLimit,
                                outdoor.locationName,
                                settings.tempUnit.format(outdoor.temperatureC),
                                outdoor.humidityPct,
                                outdoor.conditionLabel.lowercase(),
                                absDiff
                            )
                        )
                    )
                }
            }

            if (settings.lowBatteryNotifications && room.batteryLow) {
                breaches.add(
                    RoomAlertBreach(
                        key = "low_batt_${room.channelId}",
                        notifId = 5000 + idx,
                        channelId = room.channelId,
                        roomName = room.roomName,
                        severity = AlertSeverity.LOW_BATTERY,
                        shortBadge = "Low battery",
                        title = "${room.roomName}: sensor battery low",
                        body = "Replace the battery in ${room.hardwareLabel} (${room.channelId.uppercase()}) to maintain continuous telemetry."
                    )
                )
            }
        }

        val pTrend = snapshot.pressureTrend3hHpa ?: 0f
        if (settings.pressureDropNotifications && pTrend <= -2.0f) {
            val gwRoom = snapshot.primaryGatewayRoom
            breaches.add(
                RoomAlertBreach(
                    key = "pressure_drop_gateway",
                    notifId = 6000,
                    channelId = gwRoom?.channelId ?: "gateway",
                    roomName = gwRoom?.roomName ?: "GW1200",
                    severity = AlertSeverity.PRESSURE_DROP,
                    shortBadge = "Pressure drop",
                    title = "Rapid barometric pressure drop (${String.format(java.util.Locale.UK, "%+.1f", pTrend)} hPa/3h)",
                    body = snapshot.pressureForecastText
                )
            )
        }

        settings.customCriteria.forEachIndexed { cIdx, crit ->
            if (!crit.enabled) return@forEachIndexed
            val targetRoom = snapshot.rooms.firstOrNull { it.channelId == crit.channelId }
                ?: snapshot.rooms.firstOrNull()
                ?: return@forEachIndexed

            val actualValue: Float = when (crit.metric) {
                NotificationMetric.TEMPERATURE -> targetRoom.temperatureC
                NotificationMetric.HUMIDITY -> targetRoom.humidityPct
                NotificationMetric.DEW_POINT -> targetRoom.dewPointC
                NotificationMetric.PRESSURE -> targetRoom.relativePressureHpa ?: snapshot.pressureHpa ?: 1013.2f
                NotificationMetric.COMFORT_SCORE -> targetRoom.comfortScore.toFloat()
            }

            val durationMet = if (crit.metric == NotificationMetric.HUMIDITY && crit.operator == ComparisonOperator.ABOVE) {
                Psychrometrics.isHumiditySustained(targetRoom, crit.threshold, minSustainedHours = 4.0f)
            } else {
                true
            }

            if (crit.operator.matches(actualValue, crit.threshold) && durationMet) {
                val formattedActual = String.format(java.util.Locale.UK, "%.1f%s", actualValue, crit.metric.unitSuffix)
                val formattedTarget = String.format(java.util.Locale.UK, "%.1f%s", crit.threshold, crit.metric.unitSuffix)
                val bodyText = if (crit.customMessage.isNotBlank()) {
                    "${crit.customMessage} (current: $formattedActual, criterion: ${crit.operator.symbol} $formattedTarget)"
                } else {
                    "${targetRoom.roomName} ${crit.metric.label.lowercase()} is $formattedActual (${crit.operator.symbol} $formattedTarget)."
                }
                val affectsTop = crit.metric == NotificationMetric.TEMPERATURE || crit.metric == NotificationMetric.COMFORT_SCORE
                val affectsBottom = crit.metric == NotificationMetric.HUMIDITY || crit.metric == NotificationMetric.DEW_POINT || crit.metric == NotificationMetric.COMFORT_SCORE
                breaches.add(
                    RoomAlertBreach(
                        key = "custom_crit_${crit.id}",
                        notifId = 7000 + cIdx,
                        channelId = targetRoom.channelId,
                        roomName = targetRoom.roomName,
                        severity = AlertSeverity.CUSTOM_CRITERION,
                        shortBadge = "${crit.metric.label} ${crit.operator.symbol}${formattedTarget}",
                        title = "Criterion met: ${targetRoom.roomName} ${crit.metric.label.lowercase()} ${crit.operator.symbol} $formattedTarget",
                        body = bodyText,
                        affectsTopTempArc = affectsTop,
                        affectsBottomHumidityArc = affectsBottom
                    )
                )
            }
        }

        return breaches
    }

    fun breachesForRoom(
        channelId: String,
        snapshot: HomeAtmosphereSnapshot,
        settings: AppSettings
    ): List<RoomAlertBreach> = evaluateBreaches(snapshot, settings).filter { it.channelId == channelId }
}
