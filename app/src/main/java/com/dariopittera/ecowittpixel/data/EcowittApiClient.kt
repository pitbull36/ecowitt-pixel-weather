package com.dariopittera.ecowittpixel.data

import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ConnectionSource
import com.dariopittera.ecowittpixel.model.HistoricalPoint
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.LocalOutdoorWeather
import com.dariopittera.ecowittpixel.model.Psychrometrics
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.SimulationParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

class EcowittApiClient(private val historyStore: LocalHistoryStore) {

    suspend fun fetchAtmosphere(
        settings: AppSettings,
        simParams: SimulationParameters
    ): HomeAtmosphereSnapshot = withContext(Dispatchers.IO) {
        if (settings.demoModeEnabled) {
            return@withContext settings.applyToSnapshot(generateSimulatedSnapshot(settings, simParams))
        }

        val hasCloudKeys = settings.cloudApplicationKey.isNotBlank() && settings.cloudApiKey.isNotBlank()
        val hasCustomLanIp = settings.gatewayIp.isNotBlank() && settings.gatewayIp.trim() != "192.168.1.120"

        // 1. Try GW1200 local LAN HTTP API first when preferred and a real IP is configured
        if (settings.preferLocalLan && (hasCustomLanIp || !hasCloudKeys) && settings.gatewayIp.isNotBlank()) {
            val lanSnapshot = runCatching { fetchFromGw1200Lan(settings) }.getOrNull()
            if (lanSnapshot != null) {
                val enriched = settings.applyToSnapshot(enrichWithHistory(lanSnapshot, settings))
                historyStore.recordSnapshot(enriched)
                return@withContext enriched
            }
        }

        // 2. Query Ecowitt Cloud API v3
        if (hasCloudKeys) {
            val cloudSnapshot = runCatching { fetchFromEcowittCloud(settings) }.getOrNull()
            if (cloudSnapshot != null) {
                val enriched = settings.applyToSnapshot(enrichWithHistory(cloudSnapshot, settings))
                historyStore.recordSnapshot(enriched)
                return@withContext enriched
            }
        }

        // 3. Fallback to LAN if not already tried
        if (settings.gatewayIp.isNotBlank()) {
            val lanSnapshot = runCatching { fetchFromGw1200Lan(settings) }.getOrNull()
            if (lanSnapshot != null) {
                val enriched = settings.applyToSnapshot(enrichWithHistory(lanSnapshot, settings))
                historyStore.recordSnapshot(enriched)
                return@withContext enriched
            }
        }

        // 4. Fallback to real cached snapshot (never a simulated snapshot) or simulation preview
        val cached = historyStore.loadWidgetSnapshotCache()
        if (cached != null && !historyStore.isSimulatedSnapshot(cached)) {
            return@withContext settings.applyToSnapshot(
                cached.copy(
                    source = ConnectionSource.OFFLINE_CACHE,
                    syncMessage = "Gateway unreachable on LAN or cloud; showing last cached telemetry"
                )
            )
        }

        settings.applyToSnapshot(
            generateSimulatedSnapshot(settings, simParams).copy(
                syncMessage = "No gateway reachable yet; showing preview telemetry"
            )
        )
    }

    /**
     * Queries the GW1200 local WebUI JSON endpoint:
     * GET http://<gatewayIp>/get_livedata_info
     */
    fun fetchFromGw1200Lan(settings: AppSettings): HomeAtmosphereSnapshot {
        val cleanIp = settings.gatewayIp.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val liveJsonStr = httpGet("http://$cleanIp/get_livedata_info", timeoutMs = 1500)
        val root = JSONObject(liveJsonStr)

        val versionFirmware = runCatching {
            val verStr = httpGet("http://$cleanIp/get_version", timeoutMs = 800)
            JSONObject(verStr).optString("version", "GW1200")
        }.getOrDefault("GW1200 local HTTP")

        val rooms = ArrayList<SensorRoomState>()
        val now = System.currentTimeMillis()

        // Parse built-in GW1200 indoor sensor + barometer ("wh25" array)
        val wh25Arr = root.optJSONArray("wh25")
        if (wh25Arr != null && wh25Arr.length() > 0) {
            val wh25 = wh25Arr.getJSONObject(0)
            val rawInTemp = wh25.optString("intemp", "21.0").toFloatOrNull() ?: 21.0f
            val inUnit = wh25.optString("unit", "C")
            val tempC = if (inUnit.contains("F", ignoreCase = true)) {
                (rawInTemp - 32f) * 5f / 9f
            } else {
                rawInTemp
            }
            val inHum = wh25.optString("inhumi", "48").replace("%", "").toFloatOrNull() ?: 48f
            val absPressRaw = wh25.optString("abs", "1013.2")
            val relPressRaw = wh25.optString("rel", "1015.0")
            val pressUnit = wh25.optString("unit", "hPa")
            val relPressHpa = parsePressureToHpa(relPressRaw, pressUnit)
            val absPressHpa = parsePressureToHpa(absPressRaw, pressUnit)

            val cfg = settings.roomConfigs["gateway"] ?: RoomConfig(
                channelId = "gateway",
                customName = "Living room (GW1200)",
                archetype = RoomArchetype.LIVING_ROOM
            )
            rooms.add(
                SensorRoomState(
                    channelId = "gateway",
                    hardwareLabel = "GW1200 built-in sensor",
                    roomName = cfg.customName,
                    archetype = cfg.archetype,
                    temperatureC = tempC,
                    humidityPct = inHum,
                    relativePressureHpa = relPressHpa,
                    absolutePressureHpa = absPressHpa,
                    batteryLow = false,
                    lastUpdatedMs = now
                )
            )
        }

        // Parse WH31 / WN31 multi-channel temperature and humidity sensors ("ch_aisle" array)
        val chArr = root.optJSONArray("ch_aisle")
        if (chArr != null) {
            for (i in 0 until chArr.length()) {
                val obj = chArr.getJSONObject(i)
                val chNum = obj.optString("channel", "${i + 1}").trim()
                val channelId = "ch$chNum"
                val rawTemp = obj.optString("temp", "").toFloatOrNull() ?: continue
                val unit = obj.optString("unit", "C")
                val tempC = if (unit.contains("F", ignoreCase = true)) {
                    (rawTemp - 32f) * 5f / 9f
                } else {
                    rawTemp
                }
                val hum = obj.optString("humidity", "50").replace("%", "").toFloatOrNull() ?: 50f
                val battStr = obj.optString("battery", "0")
                val battLow = battStr == "1" || battStr.equals("low", ignoreCase = true)
                val hwName = obj.optString("name", "").ifBlank { "WH31 channel $chNum" }

                val defaultArchetype = when (chNum) {
                    "1" -> RoomArchetype.BEDROOM
                    "2" -> RoomArchetype.BALCONY_GARDEN
                    else -> RoomArchetype.STUDY
                }
                val cfg = settings.roomConfigs[channelId] ?: RoomConfig(
                    channelId = channelId,
                    customName = hwName,
                    archetype = defaultArchetype
                )
                rooms.add(
                    SensorRoomState(
                        channelId = channelId,
                        hardwareLabel = "WH31 sensor (Ch $chNum)",
                        roomName = cfg.customName,
                        archetype = cfg.archetype,
                        temperatureC = tempC,
                        humidityPct = hum,
                        batteryLow = battLow,
                        lastUpdatedMs = now
                    )
                )
            }
        }

        // Also check common_list for an outdoor WH32 sensor (id "0x02" temp, "0x07" humidity)
        val commonArr = root.optJSONArray("common_list")
        if (commonArr != null) {
            var outTempC: Float? = null
            var outHum: Float? = null
            for (i in 0 until commonArr.length()) {
                val item = commonArr.getJSONObject(i)
                when (item.optString("id")) {
                    "0x02" -> {
                        val raw = item.optString("val", "").toFloatOrNull()
                        val unit = item.optString("unit", "C")
                        if (raw != null) {
                            outTempC = if (unit.contains("F", ignoreCase = true)) {
                                (raw - 32f) * 5f / 9f
                            } else raw
                        }
                    }
                    "0x07" -> {
                        outHum = item.optString("val", "").replace("%", "").toFloatOrNull()
                    }
                }
            }
            if (outTempC != null && outHum != null) {
                val cfg = settings.roomConfigs["outdoor"] ?: RoomConfig(
                    channelId = "outdoor",
                    customName = "Outdoor station",
                    archetype = RoomArchetype.BALCONY_GARDEN
                )
                rooms.add(
                    SensorRoomState(
                        channelId = "outdoor",
                        hardwareLabel = "Outdoor sensor",
                        roomName = cfg.customName,
                        archetype = cfg.archetype,
                        temperatureC = outTempC,
                        humidityPct = outHum,
                        lastUpdatedMs = now
                    )
                )
            }
        }

        return HomeAtmosphereSnapshot(
            rooms = rooms,
            source = ConnectionSource.LOCAL_LAN,
            gatewayModel = "GW1200",
            gatewayFirmware = versionFirmware,
            gatewayIp = cleanIp,
            lastSyncMs = now,
            syncMessage = "Connected directly to GW1200 over local WiFi ($cleanIp)"
        )
    }

    /**
     * Queries Ecowitt Cloud API v3:
     * https://api.ecowitt.net/api/v3/device/real_time
     */
    fun fetchFromEcowittCloud(settings: AppSettings): HomeAtmosphereSnapshot {
        val appKey = URLEncoder.encode(settings.cloudApplicationKey.trim(), "UTF-8")
        val apiKey = URLEncoder.encode(settings.cloudApiKey.trim(), "UTF-8")
        var mac = settings.cloudDeviceMac.trim().ifBlank { historyStore.loadDiscoveredMac() }

        if (mac.isBlank()) {
            // Auto-discover first gateway MAC on the user's Ecowitt account
            val listUrl =
                "https://api.ecowitt.net/api/v3/device/list?application_key=$appKey&api_key=$apiKey"
            val listResp = JSONObject(httpGet(listUrl, timeoutMs = 4500))
            val listData = listResp.optJSONObject("data")?.optJSONArray("list")
            if (listData != null && listData.length() > 0) {
                mac = listData.getJSONObject(0).optString("mac", "")
                if (mac.isNotBlank()) {
                    historyStore.saveDiscoveredMac(mac)
                }
            }
            // Respect Ecowitt Cloud v3 1-req/sec rate limit between /device/list and /device/real_time
            Thread.sleep(1100L)
        }
        require(mac.isNotBlank()) { "No Ecowitt device MAC found" }

        val encodedMac = URLEncoder.encode(mac, "UTF-8")
        val url =
            "https://api.ecowitt.net/api/v3/device/real_time?application_key=$appKey&api_key=$apiKey&mac=$encodedMac&call_back=all&temp_unitid=1&pressure_unitid=3"
        val root = JSONObject(httpGet(url, timeoutMs = 5000))
        val data = root.optJSONObject("data")
            ?: throw IllegalStateException("Ecowitt cloud response missing data object: ${root.optString("msg")}")

        val now = System.currentTimeMillis()
        val rooms = ArrayList<SensorRoomState>()

        val pressObj = data.optJSONObject("pressure")
        val relPress = pressObj?.optJSONObject("relative")?.optString("value")?.toFloatOrNull()
        val absPress = pressObj?.optJSONObject("absolute")?.optString("value")?.toFloatOrNull()

        val indoorObj = data.optJSONObject("indoor")
        if (indoorObj != null) {
            val tempC = indoorObj.optJSONObject("temperature")?.optString("value")?.toFloatOrNull()
            val hum = indoorObj.optJSONObject("humidity")?.optString("value")?.toFloatOrNull()
            if (tempC != null && hum != null) {
                val cfg = settings.roomConfigs["gateway"] ?: RoomConfig(
                    channelId = "gateway",
                    customName = "Living room (GW1200)",
                    archetype = RoomArchetype.LIVING_ROOM
                )
                rooms.add(
                    SensorRoomState(
                        channelId = "gateway",
                        hardwareLabel = "GW1200 built-in sensor",
                        roomName = cfg.customName,
                        archetype = cfg.archetype,
                        temperatureC = tempC,
                        humidityPct = hum,
                        relativePressureHpa = relPress,
                        absolutePressureHpa = absPress,
                        lastUpdatedMs = now
                    )
                )
            }
        }

        val batteryObj = data.optJSONObject("battery")
        for (ch in 1..8) {
            val chObj = data.optJSONObject("temp_and_humidity_ch$ch") ?: continue
            val tempC = chObj.optJSONObject("temperature")?.optString("value")?.toFloatOrNull() ?: continue
            val hum = chObj.optJSONObject("humidity")?.optString("value")?.toFloatOrNull() ?: 50f
            val battVal = batteryObj?.optJSONObject("temp_humidity_sensor_ch$ch")?.optString("value")
            val channelId = "ch$ch"
            val defaultArchetype = when (ch) {
                1 -> RoomArchetype.BEDROOM
                2 -> RoomArchetype.BALCONY_GARDEN
                else -> RoomArchetype.STUDY
            }
            val cfg = settings.roomConfigs[channelId] ?: RoomConfig(
                channelId = channelId,
                customName = "Sensor channel $ch",
                archetype = defaultArchetype
            )
            rooms.add(
                SensorRoomState(
                    channelId = channelId,
                    hardwareLabel = "WH31 sensor (Ch $ch)",
                    roomName = cfg.customName,
                    archetype = cfg.archetype,
                    temperatureC = tempC,
                    humidityPct = hum,
                    batteryLow = battVal == "1",
                    lastUpdatedMs = now
                )
            )
        }

        return HomeAtmosphereSnapshot(
            rooms = rooms,
            source = ConnectionSource.ECOWITT_CLOUD,
            gatewayModel = "GW1200",
            gatewayFirmware = "Ecowitt cloud v3 ($mac)",
            gatewayIp = settings.gatewayIp,
            lastSyncMs = now,
            syncMessage = "Synced via Ecowitt cloud API ($mac)"
        )
    }

    private fun enrichWithHistory(
        snapshot: HomeAtmosphereSnapshot,
        settings: AppSettings
    ): HomeAtmosphereSnapshot {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24L * 3600L * 1000L
        val twentyFourHoursAgo = now - 24L * 3600L * 1000L

        var resolvedMac = settings.cloudDeviceMac.trim().ifBlank {
            historyStore.loadDiscoveredMac()
        }.ifBlank {
            snapshot.gatewayFirmware.substringAfter("(", "").substringBefore(")", "").trim()
        }

        val hasCloudCredentials = settings.cloudApplicationKey.isNotBlank() && settings.cloudApiKey.isNotBlank()
        if (hasCloudCredentials && !resolvedMac.contains(":")) {
            runCatching {
                val appKey = URLEncoder.encode(settings.cloudApplicationKey.trim(), "UTF-8")
                val apiKey = URLEncoder.encode(settings.cloudApiKey.trim(), "UTF-8")
                val listUrl =
                    "https://api.ecowitt.net/api/v3/device/list?application_key=$appKey&api_key=$apiKey"
                val listResp = JSONObject(httpGet(listUrl, timeoutMs = 4500))
                val listData = listResp.optJSONObject("data")?.optJSONArray("list")
                if (listData != null && listData.length() > 0) {
                    val foundMac = listData.getJSONObject(0).optString("mac", "")
                    if (foundMac.isNotBlank()) {
                        resolvedMac = foundMac
                        historyStore.saveDiscoveredMac(foundMac)
                    }
                }
                Thread.sleep(1100L)
            }
        }

        val existingGatewayHistory = historyStore.loadChannelHistory("gateway", sevenDaysAgo)
        val oldestSpanMs = existingGatewayHistory.firstOrNull()?.timestampMs ?: now
        val hasFullWeekInDb = existingGatewayHistory.size >= 40 && (now - oldestSpanMs) >= 5L * 24L * 3600L * 1000L

        val recent24hHistory = historyStore.loadChannelHistory("gateway", twentyFourHoursAgo)
        val oldest24hMs = recent24hHistory.firstOrNull()?.timestampMs ?: now
        val hasFull24hCoverage = recent24hHistory.size >= 80 && (now - oldest24hMs) >= 22L * 3600L * 1000L
        val lastCloudBackfillMs = historyStore.loadCloudBackfillTimestamp()
        val needsRecent24hBackfill = (!hasFull24hCoverage && (now - lastCloudBackfillMs) >= 5 * 60_000L) ||
            (now - lastCloudBackfillMs) >= 30 * 60_000L

        if (hasCloudCredentials && resolvedMac.contains(":")) {
            if (!hasFullWeekInDb) {
                runCatching {
                    Thread.sleep(1100L)
                    fetchAndStoreCloudHistory(settings, resolvedMac, days = 1)
                    historyStore.saveCloudBackfillTimestamp(now)
                }
                runCatching {
                    Thread.sleep(1100L)
                    fetchAndStoreCloudHistory(settings, resolvedMac, days = 7)
                }
                runCatching {
                    Thread.sleep(1100L)
                    fetchAndStoreCloudHistory(settings, resolvedMac, days = 30)
                }
            } else if (needsRecent24hBackfill) {
                // Periodically backfill the past 24 hours at 5-minute resolution so LAN-only gaps and peaks are never missed
                runCatching {
                    fetchAndStoreCloudHistory(settings, resolvedMac, days = 1)
                    historyStore.saveCloudBackfillTimestamp(now)
                }
            }
        }

        // Record the live snapshot first so the current real point is present in SQLite
        historyStore.recordSnapshot(snapshot)

        val updatedRooms = snapshot.rooms.map { room ->
            // Load strictly real recorded SQLite history for this channel (never synthesize backfill points)
            val dbHistory = historyStore.loadChannelHistory(room.channelId, sinceMs = 0L)
            val fullHistory = if (dbHistory.isNotEmpty()) {
                dbHistory
            } else {
                listOf(
                    HistoricalPoint(
                        timestampMs = now,
                        temperatureC = room.temperatureC,
                        humidityPct = room.humidityPct,
                        pressureHpa = room.relativePressureHpa
                    )
                )
            }

            val oneHourAgo = fullHistory.lastOrNull { it.timestampMs <= now - 45 * 60 * 1000L }
                ?: fullHistory.firstOrNull()
            val threeHoursAgo = fullHistory.lastOrNull { it.timestampMs <= now - 160 * 60 * 1000L }
                ?: fullHistory.firstOrNull()

            val tempDelta1h = if (oneHourAgo != null) room.temperatureC - oneHourAgo.temperatureC else 0f
            val humDelta1h = if (oneHourAgo != null) room.humidityPct - oneHourAgo.humidityPct else 0f
            val pressDelta3h = if (room.relativePressureHpa != null && threeHoursAgo?.pressureHpa != null) {
                room.relativePressureHpa - threeHoursAgo.pressureHpa
            } else null

            room.copy(
                tempTrend1hC = tempDelta1h,
                humidityTrend1hPct = humDelta1h,
                pressureTrend3hHpa = pressDelta3h,
                history = fullHistory
            )
        }
        val localWeather = fetchLocalOutdoorWeather(settings) ?: snapshot.localWeather
        return snapshot.copy(
            rooms = updatedRooms,
            localWeather = localWeather
        )
    }

    /**
     * Fetches live local outdoor meteorological telemetry (temperature, relative humidity,
     * dew point, barometric pressure, precipitation, wind speed, and sky condition) via Open-Meteo.
     * Cached for 15 minutes so frequent LAN polling never spams external endpoints.
     */
    fun fetchLocalOutdoorWeather(settings: AppSettings, forceRefresh: Boolean = false): LocalOutdoorWeather? {
        val queryKey = settings.weatherLocationQuery.trim().ifBlank { "auto" }
        if (!forceRefresh) {
            val cached = historyStore.loadLocalWeatherCache(queryKey, maxAgeMs = 15 * 60_000L)
            if (cached != null) return cached
        }
        return runCatching {
            val (lat, lon, resolvedName) = resolveLocationCoordinates(settings.weatherLocationQuery)
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,pressure_msl,surface_pressure,precipitation,rain,showers,snowfall,weather_code,wind_speed_10m" +
                    "&timezone=auto"
            val root = JSONObject(httpGet(url, timeoutMs = 4000))
            val current = root.optJSONObject("current") ?: return null
            val tempC = current.optDouble("temperature_2m", 15.0).toFloat()
            val humPct = current.optDouble("relative_humidity_2m", 60.0).toFloat().coerceIn(1f, 100f)
            val dewC = if (current.has("dew_point_2m")) {
                current.optDouble("dew_point_2m").toFloat()
            } else {
                Psychrometrics.computeDewPointC(tempC, humPct)
            }
            val feelsC = if (current.has("apparent_temperature")) {
                current.optDouble("apparent_temperature").toFloat()
            } else {
                Psychrometrics.computeFeelsLikeC(tempC, humPct)
            }
            val pressHpa = when {
                current.has("pressure_msl") -> current.optDouble("pressure_msl", 1013.2).toFloat()
                current.has("surface_pressure") -> current.optDouble("surface_pressure", 1013.2).toFloat()
                else -> 1013.2f
            }
            val precipMm = (
                current.optDouble("precipitation", 0.0) +
                    current.optDouble("rain", 0.0).coerceAtLeast(0.0) * 0.0
                ).toFloat()
            val windKmh = current.optDouble("wind_speed_10m", 0.0).toFloat()
            val wmoCode = current.optInt("weather_code", 0)
            val (condLabel, isRainOrSnow) = Psychrometrics.wmoCodeToCondition(wmoCode)

            val weather = LocalOutdoorWeather(
                locationName = resolvedName,
                temperatureC = tempC,
                feelsLikeC = feelsC,
                humidityPct = humPct,
                dewPointC = dewC,
                pressureHpa = pressHpa,
                precipitationMm = precipMm,
                windSpeedKmh = windKmh,
                weatherCode = wmoCode,
                conditionLabel = condLabel,
                isRainingOrSnowing = isRainOrSnow || precipMm >= 0.2f,
                sourceLabel = "Open-Meteo ($resolvedName)",
                updatedAtMs = System.currentTimeMillis()
            )
            historyStore.saveLocalWeatherCache(queryKey, weather)
            weather
        }.getOrElse {
            // If offline, return the most recent cached weather even if older than 15m
            historyStore.loadLocalWeatherCache(queryKey, maxAgeMs = 12 * 3600_000L)
        }
    }

    private fun resolveLocationCoordinates(customQuery: String): Triple<Double, Double, String> {
        val cleanQuery = customQuery.trim()
        if (cleanQuery.isNotEmpty()) {
            runCatching {
                val enc = URLEncoder.encode(cleanQuery, "UTF-8")
                val geoUrl =
                    "https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=1&language=en&format=json"
                val geoRoot = JSONObject(httpGet(geoUrl, timeoutMs = 3500))
                val results = geoRoot.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    val city = first.optString("name", cleanQuery)
                    val countryCode = first.optString("country_code", "").uppercase(Locale.UK)
                    val label = if (countryCode.isNotBlank()) "$city, $countryCode" else city
                    return Triple(lat, lon, label)
                }
            }
        }

        // Auto-detect approximate city & coordinates via IP geolocation (no location permission prompt required)
        runCatching {
            val ipGeo = JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json", timeoutMs = 2500))
            val lat = ipGeo.optString("latitude", "").toDoubleOrNull()
            val lon = ipGeo.optString("longitude", "").toDoubleOrNull()
            val city = ipGeo.optString("city", "").trim()
            val cc = ipGeo.optString("country_code", "").trim().uppercase(Locale.UK)
            if (lat != null && lon != null) {
                val label = when {
                    city.isNotBlank() && cc.isNotBlank() -> "$city, $cc"
                    city.isNotBlank() -> city
                    else -> "Local area"
                }
                return Triple(lat, lon, label)
            }
        }

        runCatching {
            val ipWho = JSONObject(httpGet("https://ipwho.is/", timeoutMs = 2500))
            if (ipWho.optBoolean("success", true)) {
                val lat = ipWho.optDouble("latitude", Double.NaN)
                val lon = ipWho.optDouble("longitude", Double.NaN)
                val city = ipWho.optString("city", "").trim()
                val cc = ipWho.optString("country_code", "").trim().uppercase(Locale.UK)
                if (!lat.isNaN() && !lon.isNaN()) {
                    val label = when {
                        city.isNotBlank() && cc.isNotBlank() -> "$city, $cc"
                        city.isNotBlank() -> city
                        else -> "Local area"
                    }
                    return Triple(lat, lon, label)
                }
            }
        }

        // Fallback to device timezone region if IP geolocation is blocked
        val tzId = TimeZone.getDefault().id
        return when {
            tzId.contains("London", ignoreCase = true) || tzId.contains("Dublin", ignoreCase = true) ->
                Triple(51.5074, -0.1278, "London, UK")
            tzId.contains("Rome", ignoreCase = true) || tzId.contains("Milan", ignoreCase = true) ->
                Triple(45.4642, 9.1900, "Milan, IT")
            tzId.contains("Zurich", ignoreCase = true) ->
                Triple(47.3769, 8.5417, "Zurich, CH")
            tzId.contains("Paris", ignoreCase = true) || tzId.contains("Berlin", ignoreCase = true) ->
                Triple(48.8566, 2.3522, "Central Europe")
            tzId.contains("New_York", ignoreCase = true) ->
                Triple(40.7128, -74.0060, "New York, US")
            else ->
                Triple(51.5074, -0.1278, "Local weather")
        }
    }

    private fun fetchAndStoreCloudHistory(
        settings: AppSettings,
        resolvedMac: String,
        days: Int
    ) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val endMs = System.currentTimeMillis()
        val startMs = endMs - days.toLong() * 24L * 3600L * 1000L
        val appKey = URLEncoder.encode(settings.cloudApplicationKey.trim(), "UTF-8")
        val apiKey = URLEncoder.encode(settings.cloudApiKey.trim(), "UTF-8")
        val mac = URLEncoder.encode(resolvedMac.trim(), "UTF-8")
        val startStr = URLEncoder.encode(fmt.format(Date(startMs)), "UTF-8")
        val endStr = URLEncoder.encode(fmt.format(Date(endMs)), "UTF-8")

        val url =
            "https://api.ecowitt.net/api/v3/device/history?application_key=$appKey&api_key=$apiKey&mac=$mac&start_date=$startStr&end_date=$endStr&cycle_type=auto&call_back=indoor,pressure,temp_and_humidity_ch1,temp_and_humidity_ch2,temp_and_humidity_ch3,outdoor&temp_unitid=1&pressure_unitid=3"
        val root = JSONObject(httpGet(url, timeoutMs = 8000))
        val data = root.optJSONObject("data") ?: return

        parseCloudChannelHistory(data, "indoor", "gateway", data.optJSONObject("pressure"))
        for (ch in 1..8) {
            parseCloudChannelHistory(data, "temp_and_humidity_ch$ch", "ch$ch", null)
        }
    }

    private fun parseCloudChannelHistory(
        data: JSONObject,
        jsonKey: String,
        channelId: String,
        pressureObj: JSONObject?
    ) {
        val section = data.optJSONObject(jsonKey) ?: return
        val tempList = section.optJSONObject("temperature")?.optJSONObject("list") ?: return
        val humList = section.optJSONObject("humidity")?.optJSONObject("list")
        val pressList = pressureObj?.optJSONObject("relative")?.optJSONObject("list")

        val points = ArrayList<HistoricalPoint>()
        val keys = tempList.keys()
        while (keys.hasNext()) {
            val tsSecStr = keys.next()
            val tsMs = (tsSecStr.toLongOrNull() ?: continue) * 1000L
            val tempC = tempList.optString(tsSecStr).toFloatOrNull() ?: continue
            val hum = humList?.optString(tsSecStr)?.toFloatOrNull() ?: continue
            val press = pressList?.optString(tsSecStr)?.toFloatOrNull()
            points.add(HistoricalPoint(tsMs, tempC, hum, press))
        }
        points.sortBy { it.timestampMs }
        historyStore.recordHistoryBatch(channelId, points)
    }

    /**
     * Scans the local /24 WiFi subnet concurrently for an Ecowitt GW1200 responding on /get_livedata_info.
     */
    suspend fun discoverGw1200OnLan(): String? = withContext(Dispatchers.IO) {
        val prefix = findLocalSubnetPrefix() ?: "192.168.1"
        val semaphore = Semaphore(32)
        coroutineScope {
            val jobs = (1..254).map { host ->
                async {
                    semaphore.withPermit {
                        val candidateIp = "$prefix.$host"
                        runCatching {
                            val resp = httpGet("http://$candidateIp/get_livedata_info", timeoutMs = 350)
                            if (resp.contains("wh25") || resp.contains("common_list") || resp.contains("ch_aisle")) {
                                candidateIp
                            } else null
                        }.getOrNull()
                    }
                }
            }
            jobs.awaitAll().firstOrNull { it != null }
        }
    }

    private fun findLocalSubnetPrefix(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        if (parts.size == 4 && (parts[0] == "192" || parts[0] == "10" || parts[0] == "172")) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun generateSimulatedSnapshot(
        settings: AppSettings,
        sim: SimulationParameters
    ): HomeAtmosphereSnapshot {
        val now = System.currentTimeMillis()
        val phaseRad = ((sim.timeOfDayHours - 6f) / 24f) * (2.0 * Math.PI)
        val diurnalWave = sin(phaseRad).toFloat()

        // 1. GW1200 Built-in: Living room
        val gwConfig = settings.roomConfigs["gateway"] ?: RoomConfig(
            channelId = "gateway",
            customName = "Living room (GW1200)",
            archetype = RoomArchetype.LIVING_ROOM
        )
        val livingTemp = (21.6f + 1.1f * diurnalWave + sim.livingRoomTempOffsetC).coerceIn(10f, 38f)
        val livingHum = (49.0f - 4.5f * diurnalWave + sim.livingRoomHumidityOffsetPct).coerceIn(18f, 95f)
        val livingPress = (1016.4f + 2.2f * cos(phaseRad).toFloat() + sim.pressureOffsetHpa).coerceIn(975f, 1045f)
        val livingHistory = synthesizeBackfillHistory(
            currentTempC = livingTemp,
            currentHumidity = livingHum,
            currentPressureHpa = livingPress,
            isOutdoor = false,
            nowMs = now,
            seedOffset = 0.0
        )

        // 2. Sensor Channel 1: Master bedroom
        val ch1Config = settings.roomConfigs["ch1"] ?: RoomConfig(
            channelId = "ch1",
            customName = "Master bedroom (Ch 1)",
            archetype = RoomArchetype.BEDROOM
        )
        val bedTemp = (19.2f + 0.9f * diurnalWave + sim.bedroomTempOffsetC).coerceIn(10f, 36f)
        val bedHum = (53.5f - 3.5f * diurnalWave + sim.bedroomHumidityOffsetPct).coerceIn(20f, 95f)
        val bedHistory = synthesizeBackfillHistory(
            currentTempC = bedTemp,
            currentHumidity = bedHum,
            currentPressureHpa = null,
            isOutdoor = false,
            nowMs = now,
            seedOffset = 1.1
        )

        // 3. Sensor Channel 2: Balcony and garden
        val ch2Config = settings.roomConfigs["ch2"] ?: RoomConfig(
            channelId = "ch2",
            customName = "Balcony garden (Ch 2)",
            archetype = RoomArchetype.BALCONY_GARDEN
        )
        val outTemp = (15.4f + 4.2f * diurnalWave + sim.outdoorTempOffsetC).coerceIn(-8f, 42f)
        val outHum = (64.0f - 14.0f * diurnalWave + sim.outdoorHumidityOffsetPct).coerceIn(20f, 99f)
        val outHistory = synthesizeBackfillHistory(
            currentTempC = outTemp,
            currentHumidity = outHum,
            currentPressureHpa = null,
            isOutdoor = true,
            nowMs = now,
            seedOffset = 2.3
        )

        val rooms = listOf(
            SensorRoomState(
                channelId = "gateway",
                hardwareLabel = "GW1200 built-in sensor",
                roomName = gwConfig.customName,
                archetype = gwConfig.archetype,
                temperatureC = livingTemp,
                humidityPct = livingHum,
                relativePressureHpa = livingPress,
                absolutePressureHpa = livingPress - 4.2f,
                batteryLow = false,
                signalQualityPct = 100,
                lastUpdatedMs = now,
                tempTrend1hC = 0.3f * cos(phaseRad).toFloat(),
                humidityTrend1hPct = -0.8f * cos(phaseRad).toFloat(),
                pressureTrend3hHpa = -1.1f * sin(phaseRad).toFloat() + (sim.pressureOffsetHpa * 0.15f),
                history = livingHistory
            ),
            SensorRoomState(
                channelId = "ch1",
                hardwareLabel = "WH31 sensor (Ch 1)",
                roomName = ch1Config.customName,
                archetype = ch1Config.archetype,
                temperatureC = bedTemp,
                humidityPct = bedHum,
                batteryLow = false,
                signalQualityPct = 96,
                lastUpdatedMs = now,
                tempTrend1hC = 0.2f * cos(phaseRad).toFloat(),
                humidityTrend1hPct = -0.5f * cos(phaseRad).toFloat(),
                history = bedHistory
            ),
            SensorRoomState(
                channelId = "ch2",
                hardwareLabel = "WH31 sensor (Ch 2)",
                roomName = ch2Config.customName,
                archetype = ch2Config.archetype,
                temperatureC = outTemp,
                humidityPct = outHum,
                batteryLow = false,
                signalQualityPct = 91,
                lastUpdatedMs = now,
                tempTrend1hC = 0.9f * cos(phaseRad).toFloat(),
                humidityTrend1hPct = -2.4f * cos(phaseRad).toFloat(),
                history = outHistory
            )
        )

        val simLocalWeather = LocalOutdoorWeather(
            locationName = settings.weatherLocationQuery.trim().ifBlank { "Local station (auto)" },
            temperatureC = outTemp,
            feelsLikeC = Psychrometrics.computeFeelsLikeC(outTemp, outHum),
            humidityPct = outHum,
            dewPointC = Psychrometrics.computeDewPointC(outTemp, outHum),
            pressureHpa = livingPress,
            precipitationMm = 0f,
            windSpeedKmh = 11f,
            weatherCode = 1,
            conditionLabel = if (outHum >= 82f) "Overcast" else "Mainly clear",
            isRainingOrSnowing = outHum >= 90f,
            sourceLabel = "Simulated local weather",
            updatedAtMs = now
        )

        return HomeAtmosphereSnapshot(
            rooms = rooms,
            source = ConnectionSource.DEMO_SIMULATION,
            gatewayModel = "GW1200",
            gatewayFirmware = "GW1200C_V1.3.4",
            gatewayIp = settings.gatewayIp,
            lastSyncMs = now,
            syncMessage = "Interactive simulation active (GW1200 + 2 WH31 sensors)",
            localWeather = simLocalWeather
        )
    }

    private fun synthesizeBackfillHistory(
        currentTempC: Float,
        currentHumidity: Float,
        currentPressureHpa: Float?,
        isOutdoor: Boolean,
        nowMs: Long,
        seedOffset: Double = 0.0
    ): List<HistoricalPoint> {
        val points = ArrayList<HistoricalPoint>(168)
        val tempAmp = if (isOutdoor) 4.4f else 1.25f
        val humAmp = if (isOutdoor) 13.5f else 4.8f
        val totalHours = 168 // 7 days of hourly points
        for (h in totalHours downTo 0) {
            val ts = nowMs - h * 3600_000L
            val hoursAgo = h.toDouble()
            val diurnal = sin(((hoursAgo % 24.0) / 24.0) * 2.0 * Math.PI + seedOffset).toFloat()
            val synoptic = sin((hoursAgo / 72.0) * 2.0 * Math.PI + seedOffset).toFloat()
            val blend = if (h == 0) 0f else 1f
            val t = currentTempC + blend * (diurnal * tempAmp + synoptic * (tempAmp * 0.4f))
            val rh = (currentHumidity + blend * (-diurnal * humAmp + synoptic * 3.2f)).coerceIn(18f, 98f)
            val p = currentPressureHpa?.let { baseP ->
                baseP + blend * (synoptic * 5.5f + cos((hoursAgo / 12.0) * Math.PI).toFloat() * 0.8f)
            }
            points.add(HistoricalPoint(ts, t, rh, p))
        }
        return points
    }

    private fun parsePressureToHpa(raw: String, unitHint: String): Float? {
        val num = raw.replace("hPa", "", ignoreCase = true)
            .replace("inHg", "", ignoreCase = true)
            .trim()
            .toFloatOrNull() ?: return null
        return if (unitHint.contains("inHg", ignoreCase = true) || num < 60f) {
            num / 0.02953f
        } else {
            num
        }
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $urlStr")
            }
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                return reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }
}
