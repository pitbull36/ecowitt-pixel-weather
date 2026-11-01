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
        scientificAlertHumidityMaxPct = 60.0f,
        standardCitation = "ASHRAE 55, CIBSE Guide A, and WHO",
        standardRationale = "WHO minimum 18°C for living areas; CIBSE overheating limit at 26°C; EPA and ASHRAE dust mite and spore limit at 60% RH"
    ),
    BEDROOM(
        id = "bedroom",
        title = "Master bedroom",
        subtitle = "Sleep sanctuary optimised for cooler night air",
        isOutdoor = false,
        idealTempMinC = 16.5f,
        idealTempMaxC = 19.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 55f,
        scientificAlertTempMinC = 15.5f,
        scientificAlertTempMaxC = 24.0f,
        scientificAlertHumidityMaxPct = 58.0f,
        standardCitation = "CIBSE Sleep Standard and WHO",
        standardRationale = "Core body cooling for REM sleep requires 16.5–19.5°C; CIBSE sleep quality degrades above 24°C; bedding dust mite limit at 58% RH"
    ),
    STUDY(
        id = "study",
        title = "Home office",
        subtitle = "Study desk with dual monitors and bookshelf",
        isOutdoor = false,
        idealTempMinC = 20.0f,
        idealTempMaxC = 23.5f,
        idealHumidityMin = 40f,
        idealHumidityMax = 55f,
        scientificAlertTempMinC = 18.5f,
        scientificAlertTempMaxC = 25.5f,
        scientificAlertHumidityMaxPct = 60.0f,
        standardCitation = "EN 16798-1 Category I Office",
        standardRationale = "Cognitive performance and tear-film stability peak at 20–23.5°C and 40–55% RH; productivity declines above 25.5°C"
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
        scientificAlertHumidityMaxPct = 68.0f,
        standardCitation = "CIBSE Guide A Kitchen Standard",
        standardRationale = "Active metabolic rate allows 18–23.5°C; sustained humidity above 68% RH requires extractor ventilation"
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
        scientificAlertHumidityMaxPct = 72.0f,
        standardCitation = "ASHRAE 160 Mould Germination Model",
        standardRationale = "Prevents surface water activity aw > 0.80 on tiles and mirrors; sustained RH above 72% triggers mould germination"
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
        standardCitation = "WMO and RHS Horticultural Standard",
        standardRationale = "Ground frost alert below 2°C for container plants; heat stress above 32°C; foliar fungal risk above 88% RH"
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
    val mouldRiskNotifications: Boolean = true,
    val pressureDropNotifications: Boolean = true,
    val lowBatteryNotifications: Boolean = true,
    val gyroscopeParallaxEnabled: Boolean = true,
    val roomConfigs: Map<String, RoomConfig> = defaultRoomConfigs(),
    val customCriteria: List<CustomAlertCriterion> = defaultCustomCriteria()
) {
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
                threshold = 60.0f,
                customMessage = "Bedroom humidity above 60%: ventilate or run dehumidifier before sleep",
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

    fun evaluateMouldRisk(tempC: Float, humidityPct: Float, dewPointC: Float): MouldRiskLevel {
        val dewDepression = tempC - dewPointC
        return when {
            humidityPct >= 70f || dewDepression < 3.2f -> MouldRiskLevel.HIGH
            humidityPct >= 60f || dewDepression < 5.2f -> MouldRiskLevel.MODERATE
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

    fun buildVentilationAdvice(
        indoor: SensorRoomState,
        outdoor: SensorRoomState?,
        tempUnit: TempUnit
    ): String {
        if (indoor.archetype.isOutdoor) {
            return "Outdoor reference sensor: ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³ absolute moisture"
        }
        if (outdoor == null || outdoor.channelId == indoor.channelId) {
            return if (indoor.humidityPct > indoor.archetype.scientificAlertHumidityMaxPct) {
                "Indoor air holds ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³ moisture; ventilate if outdoor air is drier"
            } else {
                "Indoor moisture is balanced at ${String.format("%.1f", indoor.absoluteHumidityGm3)} g/m³"
            }
        }
        val absDiff = indoor.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3
        val tempDiff = indoor.temperatureC - outdoor.temperatureC

        return when {
            indoor.humidityPct >= indoor.archetype.idealHumidityMax && absDiff >= 1.0f ->
                "Open window: outdoor air carries ${String.format("%.1f", absDiff)} g/m³ less water vapour and will dry this room"
            indoor.temperatureC > indoor.archetype.idealTempMaxC && tempDiff >= 1.5f && absDiff >= -0.8f ->
                "Open window: outdoor air is ${tempUnit.format(outdoor.temperatureC)} and will naturally cool the room"
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
        val outdoor = snapshot.outdoorRoom

        snapshot.rooms.forEachIndexed { idx, room ->
            val cfg = settings.roomConfigs[room.channelId]
            val alertsOn = cfg?.alertsEnabled ?: true
            if (alertsOn) {
                val maxHum = cfg?.alertHumidityMaxPct ?: room.archetype.scientificAlertHumidityMaxPct
                val minHum = cfg?.alertHumidityMinPct ?: 30.0f
                val minTemp = cfg?.alertTempMinC ?: room.archetype.scientificAlertTempMinC
                val maxTemp = cfg?.alertTempMaxC ?: room.archetype.scientificAlertTempMaxC
                val citation = room.archetype.standardCitation

                if (room.humidityPct >= maxHum) {
                    val outdoorDryerMsg = if (outdoor != null && !room.archetype.isOutdoor &&
                        room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3 > 1.0f
                    ) {
                        " Outdoor air is drier; open a window to ventilate."
                    } else {
                        " Consider running a dehumidifier or extractor fan."
                    }
                    breaches.add(
                        RoomAlertBreach(
                            key = "room_hum_high_${room.channelId}",
                            notifId = 1000 + idx,
                            channelId = room.channelId,
                            roomName = room.roomName,
                            severity = AlertSeverity.HIGH_HUMIDITY,
                            shortBadge = "High RH ${room.humidityPct.toInt()}%",
                            title = "${room.roomName}: high humidity (${room.humidityPct.toInt()}% RH)",
                            body = "Exceeds ${maxHum.toInt()}% RH limit ($citation, dew point ${settings.tempUnit.format(room.dewPointC)}).$outdoorDryerMsg"
                        )
                    )
                }

                if (!room.archetype.isOutdoor && room.humidityPct <= minHum) {
                    breaches.add(
                        RoomAlertBreach(
                            key = "room_hum_low_${room.channelId}",
                            notifId = 1100 + idx,
                            channelId = room.channelId,
                            roomName = room.roomName,
                            severity = AlertSeverity.LOW_HUMIDITY,
                            shortBadge = "Dry ${room.humidityPct.toInt()}%",
                            title = "${room.roomName}: dry air (${room.humidityPct.toInt()}% RH)",
                            body = "Below ${minHum.toInt()}% RH minimum comfort threshold ($citation)."
                        )
                    )
                }

                if (room.temperatureC >= maxTemp || room.temperatureC <= minTemp) {
                    val isHigh = room.temperatureC >= maxTemp
                    val reason = if (isHigh) {
                        "above ${settings.tempUnit.format(maxTemp)} maximum limit"
                    } else {
                        "below ${settings.tempUnit.format(minTemp)} minimum limit"
                    }
                    breaches.add(
                        RoomAlertBreach(
                            key = "room_temp_${room.channelId}",
                            notifId = 2000 + idx,
                            channelId = room.channelId,
                            roomName = room.roomName,
                            severity = if (isHigh) AlertSeverity.HIGH_TEMP else AlertSeverity.LOW_TEMP,
                            shortBadge = if (isHigh) "Warm ${settings.tempUnit.format(room.temperatureC)}" else "Cold ${settings.tempUnit.format(room.temperatureC)}",
                            title = "${room.roomName}: temperature $reason",
                            body = "Current ${settings.tempUnit.format(room.temperatureC)} is $reason ($citation)."
                        )
                    )
                }
            }

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

            if (settings.ventilationAdviceNotifications && !room.archetype.isOutdoor && outdoor != null && outdoor.channelId != room.channelId) {
                val absDiff = room.absoluteHumidityGm3 - outdoor.absoluteHumidityGm3
                if (room.humidityPct >= room.archetype.idealHumidityMax && absDiff >= 1.2f) {
                    breaches.add(
                        RoomAlertBreach(
                            key = "vent_advice_${room.channelId}",
                            notifId = 4000 + idx,
                            channelId = room.channelId,
                            roomName = room.roomName,
                            severity = AlertSeverity.VENTILATION_ADVICE,
                            shortBadge = "Open window",
                            title = "Open window in ${room.roomName}",
                            body = String.format(
                                java.util.Locale.UK,
                                "Outdoor air carries %.1f g/m³ less moisture than indoors (%.0f%% RH) and will naturally dry the room.",
                                absDiff,
                                room.humidityPct
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

            if (crit.operator.matches(actualValue, crit.threshold)) {
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
