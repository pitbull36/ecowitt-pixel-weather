package com.dariopittera.ecowittpixel.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ComparisonOperator
import com.dariopittera.ecowittpixel.model.ConnectionSource
import com.dariopittera.ecowittpixel.model.CustomAlertCriterion
import com.dariopittera.ecowittpixel.model.HistoricalPoint
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.LocalOutdoorWeather
import com.dariopittera.ecowittpixel.model.NotificationMetric
import com.dariopittera.ecowittpixel.model.PressureUnit
import com.dariopittera.ecowittpixel.model.RoomArchetype
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.SensorRoomState
import com.dariopittera.ecowittpixel.model.TempUnit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalHistoryStore(private val context: Context) :
    SQLiteOpenHelper(context, "ecowitt_atmosphere.db", null, 1) {

    private val prefs = context.getSharedPreferences("ecowitt_pixel_prefs", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sensor_history (
                channel_id TEXT NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                temp_c REAL NOT NULL,
                humidity_pct REAL NOT NULL,
                pressure_hpa REAL,
                PRIMARY KEY (channel_id, timestamp_ms)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_history_time ON sensor_history(channel_id, timestamp_ms)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun loadSettings(): AppSettings {
        purgeLegacySyntheticHistoryIfNeeded()
        val defaults = AppSettings()
        val roomConfigsJson = prefs.getString("room_configs_json", null)
        val rawRoomConfigs = if (roomConfigsJson != null) {
            parseRoomConfigs(roomConfigsJson)
        } else {
            AppSettings.defaultRoomConfigs()
        }
        val customCriteriaJson = prefs.getString("custom_criteria_json", null)
        val rawCustomCriteria = if (customCriteriaJson != null) {
            parseCustomCriteria(customCriteriaJson)
        } else {
            AppSettings.defaultCustomCriteria()
        }

        val migrated = !prefs.getBoolean("migrated_non_alarmist_humidity_v12", false)
        val roomConfigs = if (migrated) {
            rawRoomConfigs.mapValues { (_, cfg) ->
                val wasOldDefault = when (cfg.archetype) {
                    RoomArchetype.LIVING_ROOM, RoomArchetype.BEDROOM, RoomArchetype.STUDY ->
                        cfg.alertHumidityMaxPct <= 60.5f
                    RoomArchetype.KITCHEN ->
                        cfg.alertHumidityMaxPct <= 68.5f
                    RoomArchetype.BATHROOM ->
                        cfg.alertHumidityMaxPct <= 72.5f
                    RoomArchetype.BALCONY_GARDEN ->
                        false
                }
                if (wasOldDefault) {
                    cfg.copy(alertHumidityMaxPct = cfg.archetype.scientificAlertHumidityMaxPct)
                } else {
                    cfg
                }
            }
        } else {
            rawRoomConfigs
        }

        val customCriteria = if (migrated) {
            rawCustomCriteria.map { crit ->
                if (crit.id == "crit_humid_bedroom" && crit.threshold <= 60.5f) {
                    crit.copy(
                        threshold = 68.0f,
                        customMessage = "Bedroom humidity sustained above 68%: ventilate or run dehumidifier"
                    )
                } else {
                    crit
                }
            }
        } else {
            rawCustomCriteria
        }

        val loaded = AppSettings(
            gatewayIp = prefs.getString("gateway_ip", defaults.gatewayIp) ?: defaults.gatewayIp,
            cloudApplicationKey = prefs.getString("cloud_app_key", "") ?: "",
            cloudApiKey = prefs.getString("cloud_api_key", "") ?: "",
            cloudDeviceMac = prefs.getString("cloud_mac", "") ?: "",
            weatherLocationQuery = prefs.getString("weather_location_query", "") ?: "",
            preferLocalLan = prefs.getBoolean("prefer_local_lan", defaults.preferLocalLan),
            demoModeEnabled = prefs.getBoolean("demo_mode", defaults.demoModeEnabled),
            tempUnit = TempUnit.entries.firstOrNull {
                it.name == prefs.getString("temp_unit", TempUnit.CELSIUS.name)
            } ?: TempUnit.CELSIUS,
            pressureUnit = PressureUnit.entries.firstOrNull {
                it.name == prefs.getString("pressure_unit", PressureUnit.HPA.name)
            } ?: PressureUnit.HPA,
            pollingIntervalMinutes = prefs.getInt("polling_interval_min", defaults.pollingIntervalMinutes),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", defaults.notificationsEnabled),
            ventilationAdviceNotifications = prefs.getBoolean(
                "ventilation_notifications",
                defaults.ventilationAdviceNotifications
            ),
            mouldRiskNotifications = prefs.getBoolean(
                "mould_risk_notifications",
                defaults.mouldRiskNotifications
            ),
            pressureDropNotifications = prefs.getBoolean(
                "pressure_drop_notifications",
                defaults.pressureDropNotifications
            ),
            lowBatteryNotifications = prefs.getBoolean(
                "low_battery_notifications",
                defaults.lowBatteryNotifications
            ),
            gyroscopeParallaxEnabled = prefs.getBoolean(
                "gyro_parallax",
                defaults.gyroscopeParallaxEnabled
            ),
            roomConfigs = roomConfigs,
            customCriteria = customCriteria
        )

        if (migrated) {
            prefs.edit().putBoolean("migrated_non_alarmist_humidity_v12", true).apply()
            saveSettings(loaded)
        }
        return loaded
    }

    /**
     * Removes any legacy synthetic backfill points that may have been persisted (e.g. on watch sync).
     * Real local LAN/Cloud snapshots use 5-minute buckets (`timestamp_ms % 300000 == 0`) and Ecowitt
     * Cloud history API timestamps are exact minute boundaries (`timestamp_ms % 60000 == 0`), whereas
     * legacy `synthesizeBackfillHistory` points had arbitrary millisecond offsets (`% 60000 != 0`).
     */
    private fun purgeLegacySyntheticHistoryIfNeeded() {
        if (prefs.getBoolean("purged_synthetic_history_v12", false)) return
        runCatching {
            writableDatabase.execSQL("DELETE FROM sensor_history WHERE (timestamp_ms % 60000) != 0")
            prefs.edit().putBoolean("purged_synthetic_history_v12", true).apply()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val roomsObj = JSONObject()
        settings.roomConfigs.forEach { (channelId, cfg) ->
            val obj = JSONObject().apply {
                put("channelId", cfg.channelId)
                put("customName", cfg.customName)
                put("archetype", cfg.archetype.id)
                put("alertTempMinC", cfg.alertTempMinC.toDouble())
                put("alertTempMaxC", cfg.alertTempMaxC.toDouble())
                put("alertHumidityMinPct", cfg.alertHumidityMinPct.toDouble())
                put("alertHumidityMaxPct", cfg.alertHumidityMaxPct.toDouble())
                put("alertsEnabled", cfg.alertsEnabled)
            }
            roomsObj.put(channelId, obj)
        }
        val criteriaArr = JSONArray()
        settings.customCriteria.forEach { crit ->
            val cObj = JSONObject().apply {
                put("id", crit.id)
                put("channelId", crit.channelId)
                put("metric", crit.metric.id)
                put("operator", crit.operator.id)
                put("threshold", crit.threshold.toDouble())
                put("customMessage", crit.customMessage)
                put("enabled", crit.enabled)
            }
            criteriaArr.put(cObj)
        }
        prefs.edit()
            .putString("gateway_ip", settings.gatewayIp.trim())
            .putString("cloud_app_key", settings.cloudApplicationKey.trim())
            .putString("cloud_api_key", settings.cloudApiKey.trim())
            .putString("cloud_mac", settings.cloudDeviceMac.trim())
            .putString("weather_location_query", settings.weatherLocationQuery.trim())
            .putBoolean("prefer_local_lan", settings.preferLocalLan)
            .putBoolean("demo_mode", settings.demoModeEnabled)
            .putString("temp_unit", settings.tempUnit.name)
            .putString("pressure_unit", settings.pressureUnit.name)
            .putInt("polling_interval_min", settings.pollingIntervalMinutes)
            .putBoolean("notifications_enabled", settings.notificationsEnabled)
            .putBoolean("ventilation_notifications", settings.ventilationAdviceNotifications)
            .putBoolean("mould_risk_notifications", settings.mouldRiskNotifications)
            .putBoolean("pressure_drop_notifications", settings.pressureDropNotifications)
            .putBoolean("low_battery_notifications", settings.lowBatteryNotifications)
            .putBoolean("gyro_parallax", settings.gyroscopeParallaxEnabled)
            .putString("room_configs_json", roomsObj.toString())
            .putString("custom_criteria_json", criteriaArr.toString())
            .apply()
    }

    private fun parseRoomConfigs(json: String): Map<String, RoomConfig> {
        val result = AppSettings.defaultRoomConfigs().toMutableMap()
        return try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                val arch = RoomArchetype.fromId(obj.optString("archetype", "living_room"))
                result[key] = RoomConfig(
                    channelId = obj.optString("channelId", key),
                    customName = obj.optString("customName", key),
                    archetype = arch,
                    alertTempMinC = obj.optDouble("alertTempMinC", arch.scientificAlertTempMinC.toDouble()).toFloat(),
                    alertTempMaxC = obj.optDouble("alertTempMaxC", arch.scientificAlertTempMaxC.toDouble()).toFloat(),
                    alertHumidityMinPct = obj.optDouble("alertHumidityMinPct", 30.0).toFloat(),
                    alertHumidityMaxPct = obj.optDouble("alertHumidityMaxPct", arch.scientificAlertHumidityMaxPct.toDouble()).toFloat(),
                    alertsEnabled = obj.optBoolean("alertsEnabled", true)
                )
            }
            result
        } catch (_: Exception) {
            result
        }
    }

    private fun parseCustomCriteria(json: String): List<CustomAlertCriterion> {
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<CustomAlertCriterion>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CustomAlertCriterion(
                        id = obj.optString("id", "crit_$i"),
                        channelId = obj.optString("channelId", "gateway"),
                        metric = NotificationMetric.fromId(obj.optString("metric", "temperature")),
                        operator = ComparisonOperator.fromId(obj.optString("operator", "above")),
                        threshold = obj.optDouble("threshold", 25.0).toFloat(),
                        customMessage = obj.optString("customMessage", "Custom alert criterion met"),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            AppSettings.defaultCustomCriteria()
        }
    }

    fun recordSnapshot(snapshot: HomeAtmosphereSnapshot) {
        if (isSimulatedSnapshot(snapshot)) return
        val db = writableDatabase
        val bucketTimestamp = (snapshot.lastSyncMs / (5 * 60 * 1000L)) * (5 * 60 * 1000L)
        db.beginTransaction()
        try {
            snapshot.rooms.forEach { room ->
                val values = ContentValues().apply {
                    put("channel_id", room.channelId)
                    put("timestamp_ms", bucketTimestamp)
                    put("temp_c", room.temperatureC.toDouble())
                    put("humidity_pct", room.humidityPct.toDouble())
                    if (room.relativePressureHpa != null) {
                        put("pressure_hpa", room.relativePressureHpa.toDouble())
                    }
                }
                db.insertWithOnConflict(
                    "sensor_history",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            // Permanent local SQLite archive: no expiration cutoff so history is retained forever on device
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Also cache latest snapshot in SharedPreferences for instant Glance widget access
        saveWidgetSnapshotCache(snapshot)
    }

    fun recordHistoryBatch(channelId: String, points: List<HistoricalPoint>) {
        if (points.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (pt in points) {
                val values = ContentValues().apply {
                    put("channel_id", channelId)
                    put("timestamp_ms", pt.timestampMs)
                    put("temp_c", pt.temperatureC.toDouble())
                    put("humidity_pct", pt.humidityPct.toDouble())
                    if (pt.pressureHpa != null) {
                        put("pressure_hpa", pt.pressureHpa.toDouble())
                    }
                }
                db.insertWithOnConflict(
                    "sensor_history",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadChannelHistory(channelId: String, sinceMs: Long): List<HistoricalPoint> {
        val db = readableDatabase
        val list = ArrayList<HistoricalPoint>()
        val cursor = db.query(
            "sensor_history",
            arrayOf("timestamp_ms", "temp_c", "humidity_pct", "pressure_hpa"),
            "channel_id = ? AND timestamp_ms >= ?",
            arrayOf(channelId, sinceMs.toString()),
            null,
            null,
            "timestamp_ms ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val ts = c.getLong(0)
                val temp = c.getFloat(1)
                val hum = c.getFloat(2)
                val press = if (c.isNull(3)) null else c.getFloat(3)
                list.add(HistoricalPoint(ts, temp, hum, press))
            }
        }
        return list
    }

    fun saveWidgetSnapshotCache(snapshot: HomeAtmosphereSnapshot) {
        if (isSimulatedSnapshot(snapshot)) {
            return
        }
        val synced = loadSettings().applyToSnapshot(snapshot)
        val rawJson = serializeSnapshotJson(synced)
        prefs.edit().putString("cached_snapshot_json", rawJson).apply()
    }

    fun loadWidgetSnapshotCache(): HomeAtmosphereSnapshot? {
        val raw = prefs.getString("cached_snapshot_json", null) ?: return null
        val snap = deserializeSnapshotJson(raw) ?: return null
        if (isSimulatedSnapshot(snap)) {
            prefs.edit().remove("cached_snapshot_json").apply()
            return null
        }
        return loadSettings().applyToSnapshot(snap)
    }

    fun clearWidgetSnapshotCache() {
        prefs.edit().remove("cached_snapshot_json").apply()
    }

    fun hasSyncedFromPhone(): Boolean =
        prefs.getBoolean("watch_has_synced_from_phone", false)

    fun markSyncedFromPhone(synced: Boolean) {
        prefs.edit().putBoolean("watch_has_synced_from_phone", synced).apply()
    }

    fun isSimulatedSnapshot(snapshot: HomeAtmosphereSnapshot): Boolean {
        return snapshot.source == ConnectionSource.DEMO_SIMULATION ||
            snapshot.gatewayFirmware == "GW1200C_V1.3.4" ||
            snapshot.syncMessage.contains("Interactive simulation", ignoreCase = true) ||
            snapshot.syncMessage.contains("preview telemetry", ignoreCase = true)
    }

    fun saveDiscoveredMac(mac: String) {
        if (mac.isNotBlank()) {
            prefs.edit().putString("cloud_mac", mac.trim()).apply()
        }
    }

    fun loadDiscoveredMac(): String {
        return prefs.getString("cloud_mac", "") ?: ""
    }

    fun serializeSettingsJson(settings: AppSettings): String {
        val roomsObj = JSONObject()
        settings.roomConfigs.forEach { (channelId, cfg) ->
            val obj = JSONObject().apply {
                put("channelId", cfg.channelId)
                put("customName", cfg.customName)
                put("archetype", cfg.archetype.id)
                put("alertTempMinC", cfg.alertTempMinC.toDouble())
                put("alertTempMaxC", cfg.alertTempMaxC.toDouble())
                put("alertHumidityMinPct", cfg.alertHumidityMinPct.toDouble())
                put("alertHumidityMaxPct", cfg.alertHumidityMaxPct.toDouble())
                put("alertsEnabled", cfg.alertsEnabled)
            }
            roomsObj.put(channelId, obj)
        }
        val criteriaArr = JSONArray()
        settings.customCriteria.forEach { crit ->
            val cObj = JSONObject().apply {
                put("id", crit.id)
                put("channelId", crit.channelId)
                put("metric", crit.metric.id)
                put("operator", crit.operator.id)
                put("threshold", crit.threshold.toDouble())
                put("customMessage", crit.customMessage)
                put("enabled", crit.enabled)
            }
            criteriaArr.put(cObj)
        }
        return JSONObject().apply {
            put("gatewayIp", settings.gatewayIp.trim())
            put("cloudApplicationKey", settings.cloudApplicationKey.trim())
            put("cloudApiKey", settings.cloudApiKey.trim())
            put("cloudDeviceMac", settings.cloudDeviceMac.trim())
            put("weatherLocationQuery", settings.weatherLocationQuery.trim())
            put("preferLocalLan", settings.preferLocalLan)
            put("demoModeEnabled", settings.demoModeEnabled)
            put("tempUnit", settings.tempUnit.name)
            put("pressureUnit", settings.pressureUnit.name)
            put("pollingIntervalMinutes", settings.pollingIntervalMinutes)
            put("notificationsEnabled", settings.notificationsEnabled)
            put("ventilationAdviceNotifications", settings.ventilationAdviceNotifications)
            put("mouldRiskNotifications", settings.mouldRiskNotifications)
            put("pressureDropNotifications", settings.pressureDropNotifications)
            put("lowBatteryNotifications", settings.lowBatteryNotifications)
            put("gyroscopeParallaxEnabled", settings.gyroscopeParallaxEnabled)
            put("roomConfigs", roomsObj)
            put("customCriteria", criteriaArr)
        }.toString()
    }

    fun deserializeSettingsJson(raw: String): AppSettings? {
        return try {
            val defaults = AppSettings()
            val root = JSONObject(raw)
            val roomsObj = root.optJSONObject("roomConfigs")
            val roomConfigs = if (roomsObj != null) {
                parseRoomConfigs(roomsObj.toString())
            } else {
                AppSettings.defaultRoomConfigs()
            }
            val criteriaArr = root.optJSONArray("customCriteria")
            val customCriteria = if (criteriaArr != null) {
                parseCustomCriteria(criteriaArr.toString())
            } else {
                AppSettings.defaultCustomCriteria()
            }
            AppSettings(
                gatewayIp = root.optString("gatewayIp", defaults.gatewayIp),
                cloudApplicationKey = root.optString("cloudApplicationKey", ""),
                cloudApiKey = root.optString("cloudApiKey", ""),
                cloudDeviceMac = root.optString("cloudDeviceMac", ""),
                weatherLocationQuery = root.optString("weatherLocationQuery", ""),
                preferLocalLan = root.optBoolean("preferLocalLan", defaults.preferLocalLan),
                demoModeEnabled = root.optBoolean("demoModeEnabled", defaults.demoModeEnabled),
                tempUnit = TempUnit.entries.firstOrNull {
                    it.name == root.optString("tempUnit", TempUnit.CELSIUS.name)
                } ?: TempUnit.CELSIUS,
                pressureUnit = PressureUnit.entries.firstOrNull {
                    it.name == root.optString("pressureUnit", PressureUnit.HPA.name)
                } ?: PressureUnit.HPA,
                pollingIntervalMinutes = root.optInt("pollingIntervalMinutes", defaults.pollingIntervalMinutes),
                notificationsEnabled = root.optBoolean("notificationsEnabled", defaults.notificationsEnabled),
                ventilationAdviceNotifications = root.optBoolean(
                    "ventilationAdviceNotifications",
                    defaults.ventilationAdviceNotifications
                ),
                mouldRiskNotifications = root.optBoolean(
                    "mouldRiskNotifications",
                    defaults.mouldRiskNotifications
                ),
                pressureDropNotifications = root.optBoolean(
                    "pressureDropNotifications",
                    defaults.pressureDropNotifications
                ),
                lowBatteryNotifications = root.optBoolean(
                    "lowBatteryNotifications",
                    defaults.lowBatteryNotifications
                ),
                gyroscopeParallaxEnabled = root.optBoolean(
                    "gyroscopeParallaxEnabled",
                    defaults.gyroscopeParallaxEnabled
                ),
                roomConfigs = roomConfigs,
                customCriteria = customCriteria
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveCloudBackfillTimestamp(timestampMs: Long) {
        prefs.edit().putLong("last_cloud_backfill_ms", timestampMs).apply()
    }

    fun loadCloudBackfillTimestamp(): Long {
        return prefs.getLong("last_cloud_backfill_ms", 0L)
    }

    private fun downsamplePreservingExtrema(
        points: List<HistoricalPoint>,
        targetBuckets: Int
    ): List<HistoricalPoint> {
        if (points.size <= targetBuckets * 2) return points
        val out = ArrayList<HistoricalPoint>(targetBuckets * 2 + 2)
        val bucketSize = (points.size.toFloat() / targetBuckets).coerceAtLeast(1f)
        for (b in 0 until targetBuckets) {
            val startIdx = (b * bucketSize).toInt().coerceIn(0, points.lastIndex)
            val endIdx = (((b + 1) * bucketSize).toInt() - 1).coerceIn(startIdx, points.lastIndex)
            val slice = points.subList(startIdx, endIdx + 1)
            if (slice.isEmpty()) continue
            val minHumPt = slice.minByOrNull { it.humidityPct }!!
            val maxHumPt = slice.maxByOrNull { it.humidityPct }!!
            val minTempPt = slice.minByOrNull { it.temperatureC }!!
            val maxTempPt = slice.maxByOrNull { it.temperatureC }!!
            val unique = linkedSetOf(slice.first(), minHumPt, maxHumPt, minTempPt, maxTempPt, slice.last())
                .sortedBy { it.timestampMs }
            for (pt in unique) {
                if (out.isEmpty() || out.last().timestampMs != pt.timestampMs) {
                    out.add(pt)
                }
            }
        }
        return out
    }

    fun serializeSnapshotJson(snapshot: HomeAtmosphereSnapshot): String {
        val nowMs = System.currentTimeMillis()
        val cutoff7d = nowMs - 7L * 24L * 3600L * 1000L
        val cutoff24h = nowMs - 24L * 3600L * 1000L
        val root = JSONObject().apply {
            put("source", snapshot.source.name)
            put("gatewayModel", snapshot.gatewayModel)
            put("gatewayFirmware", snapshot.gatewayFirmware)
            put("gatewayIp", snapshot.gatewayIp)
            put("lastSyncMs", snapshot.lastSyncMs)
            put("syncMessage", snapshot.syncMessage)
            val arr = JSONArray()
            snapshot.rooms.forEach { room ->
                val rObj = JSONObject().apply {
                    put("channelId", room.channelId)
                    put("hardwareLabel", room.hardwareLabel)
                    put("roomName", room.roomName)
                    put("archetype", room.archetype.id)
                    put("temperatureC", room.temperatureC.toDouble())
                    put("humidityPct", room.humidityPct.toDouble())
                    if (room.relativePressureHpa != null) {
                        put("relativePressureHpa", room.relativePressureHpa.toDouble())
                    }
                    if (room.absolutePressureHpa != null) {
                        put("absolutePressureHpa", room.absolutePressureHpa.toDouble())
                    }
                    put("batteryLow", room.batteryLow)
                    put("tempTrend1hC", room.tempTrend1hC.toDouble())
                    put("humidityTrend1hPct", room.humidityTrend1hPct.toDouble())
                    if (room.pressureTrend3hHpa != null) {
                        put("pressureTrend3hHpa", room.pressureTrend3hHpa.toDouble())
                    }
                    // Preserve extrema (min/max humidity and temperature peaks) across both 7d and 24h windows
                    val olderWeek = room.history.filter { it.timestampMs in cutoff7d until cutoff24h }
                    val recent24h = room.history.filter { it.timestampMs >= cutoff24h }.ifEmpty {
                        room.history.takeLast(96)
                    }
                    val sampledPts = ArrayList<HistoricalPoint>()
                    if (olderWeek.isNotEmpty()) {
                        sampledPts.addAll(downsamplePreservingExtrema(olderWeek, targetBuckets = 42))
                    }
                    if (recent24h.isNotEmpty()) {
                        // Keep all 24h points up to 288 (every 5 min), or bucket-extrema downsample if denser
                        val recentSampled = if (recent24h.size <= 288) {
                            recent24h
                        } else {
                            downsamplePreservingExtrema(recent24h, targetBuckets = 96)
                        }
                        for (pt in recentSampled) {
                            if (sampledPts.isEmpty() || sampledPts.last().timestampMs < pt.timestampMs) {
                                sampledPts.add(pt)
                            }
                        }
                    }
                    val histArr = JSONArray()
                    for (pt in sampledPts) {
                        histArr.put(
                            JSONObject().apply {
                                put("t", pt.timestampMs)
                                put("c", pt.temperatureC.toDouble())
                                put("h", pt.humidityPct.toDouble())
                                if (pt.pressureHpa != null) {
                                    put("p", pt.pressureHpa.toDouble())
                                }
                            }
                        )
                    }
                    put("history", histArr)
                }
                arr.put(rObj)
            }
            put("rooms", arr)
            snapshot.localWeather?.let { lw ->
                put("localWeather", serializeLocalWeatherJson(lw))
            }
        }
        return root.toString()
    }

    fun serializeLocalWeatherJson(lw: LocalOutdoorWeather): JSONObject = JSONObject().apply {
        put("locationName", lw.locationName)
        put("temperatureC", lw.temperatureC.toDouble())
        put("feelsLikeC", lw.feelsLikeC.toDouble())
        put("humidityPct", lw.humidityPct.toDouble())
        put("dewPointC", lw.dewPointC.toDouble())
        put("pressureHpa", lw.pressureHpa.toDouble())
        put("precipitationMm", lw.precipitationMm.toDouble())
        put("windSpeedKmh", lw.windSpeedKmh.toDouble())
        put("weatherCode", lw.weatherCode)
        put("conditionLabel", lw.conditionLabel)
        put("isRainingOrSnowing", lw.isRainingOrSnowing)
        put("sourceLabel", lw.sourceLabel)
        put("updatedAtMs", lw.updatedAtMs)
    }

    fun deserializeLocalWeatherJson(obj: JSONObject?): LocalOutdoorWeather? {
        if (obj == null) return null
        return try {
            LocalOutdoorWeather(
                locationName = obj.optString("locationName", "Local weather"),
                temperatureC = obj.optDouble("temperatureC", 15.0).toFloat(),
                feelsLikeC = obj.optDouble("feelsLikeC", 14.5).toFloat(),
                humidityPct = obj.optDouble("humidityPct", 60.0).toFloat(),
                dewPointC = obj.optDouble("dewPointC", 7.3).toFloat(),
                pressureHpa = obj.optDouble("pressureHpa", 1013.2).toFloat(),
                precipitationMm = obj.optDouble("precipitationMm", 0.0).toFloat(),
                windSpeedKmh = obj.optDouble("windSpeedKmh", 8.0).toFloat(),
                weatherCode = obj.optInt("weatherCode", 0),
                conditionLabel = obj.optString("conditionLabel", "Clear sky"),
                isRainingOrSnowing = obj.optBoolean("isRainingOrSnowing", false),
                sourceLabel = obj.optString("sourceLabel", "Open-Meteo local weather"),
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveLocalWeatherCache(queryKey: String, weather: LocalOutdoorWeather) {
        val obj = serializeLocalWeatherJson(weather).apply {
            put("queryKey", queryKey.trim().lowercase(Locale.UK))
        }
        prefs.edit().putString("cached_local_weather_json", obj.toString()).apply()
    }

    fun loadLocalWeatherCache(queryKey: String, maxAgeMs: Long = 15 * 60_000L): LocalOutdoorWeather? {
        val raw = prefs.getString("cached_local_weather_json", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val cachedKey = obj.optString("queryKey", "")
            if (cachedKey != queryKey.trim().lowercase(Locale.UK)) return null
            val weather = deserializeLocalWeatherJson(obj) ?: return null
            if (System.currentTimeMillis() - weather.updatedAtMs > maxAgeMs) return null
            weather
        } catch (_: Exception) {
            null
        }
    }

    fun deserializeSnapshotJson(raw: String): HomeAtmosphereSnapshot? {
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("rooms")
            val rooms = ArrayList<SensorRoomState>()
            for (i in 0 until arr.length()) {
                val rObj = arr.getJSONObject(i)
                val histArr = rObj.optJSONArray("history")
                val historyPts = ArrayList<HistoricalPoint>()
                if (histArr != null) {
                    for (j in 0 until histArr.length()) {
                        val hObj = histArr.getJSONObject(j)
                        historyPts.add(
                            HistoricalPoint(
                                timestampMs = hObj.optLong("t", 0L),
                                temperatureC = hObj.optDouble("c", 20.0).toFloat(),
                                humidityPct = hObj.optDouble("h", 50.0).toFloat(),
                                pressureHpa = if (hObj.has("p")) hObj.optDouble("p").toFloat() else null
                            )
                        )
                    }
                }
                val chId = rObj.getString("channelId")
                val dbPts = loadChannelHistory(chId, System.currentTimeMillis() - 7L * 24L * 3600L * 1000L)
                val mergedMap = java.util.TreeMap<Long, HistoricalPoint>()
                for (pt in dbPts) {
                    mergedMap[pt.timestampMs] = pt
                }
                for (pt in historyPts) {
                    mergedMap[pt.timestampMs] = pt
                }
                val finalHistory = ArrayList(mergedMap.values)
                rooms.add(
                    SensorRoomState(
                        channelId = chId,
                        hardwareLabel = rObj.optString("hardwareLabel", "Sensor"),
                        roomName = rObj.getString("roomName"),
                        archetype = RoomArchetype.fromId(rObj.optString("archetype", "living_room")),
                        temperatureC = rObj.getDouble("temperatureC").toFloat(),
                        humidityPct = rObj.getDouble("humidityPct").toFloat(),
                        relativePressureHpa = if (rObj.has("relativePressureHpa")) {
                            rObj.getDouble("relativePressureHpa").toFloat()
                        } else null,
                        absolutePressureHpa = if (rObj.has("absolutePressureHpa")) {
                            rObj.getDouble("absolutePressureHpa").toFloat()
                        } else null,
                        batteryLow = rObj.optBoolean("batteryLow", false),
                        tempTrend1hC = rObj.optDouble("tempTrend1hC", 0.0).toFloat(),
                        humidityTrend1hPct = rObj.optDouble("humidityTrend1hPct", 0.0).toFloat(),
                        pressureTrend3hHpa = if (rObj.has("pressureTrend3hHpa")) {
                            rObj.getDouble("pressureTrend3hHpa").toFloat()
                        } else null,
                        history = finalHistory
                    )
                )
            }
            HomeAtmosphereSnapshot(
                rooms = rooms,
                source = ConnectionSource.entries.firstOrNull {
                    it.name == root.optString("source")
                } ?: ConnectionSource.OFFLINE_CACHE,
                gatewayModel = root.optString("gatewayModel", "GW1200"),
                gatewayFirmware = root.optString("gatewayFirmware", "GW1200C"),
                gatewayIp = root.optString("gatewayIp", ""),
                lastSyncMs = root.optLong("lastSyncMs", System.currentTimeMillis()),
                syncMessage = root.optString("syncMessage", "Cached snapshot"),
                localWeather = deserializeLocalWeatherJson(root.optJSONObject("localWeather"))
            )
        } catch (_: Exception) {
            null
        }
    }

    fun exportHistoryCsv(snapshot: HomeAtmosphereSnapshot): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ecowitt_sensor_history.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
        file.bufferedWriter().use { out ->
            out.write("channel_id,room_name,timestamp_iso,temperature_c,humidity_pct,dew_point_c,pressure_hpa\n")
            snapshot.rooms.forEach { room ->
                val allPoints = loadChannelHistory(room.channelId, sinceMs = 0L).ifEmpty { room.history }
                allPoints.forEach { pt ->
                    val dp = com.dariopittera.ecowittpixel.model.Psychrometrics.computeDewPointC(
                        pt.temperatureC,
                        pt.humidityPct
                    )
                    out.write(
                        "${room.channelId},\"${room.roomName}\",${fmt.format(Date(pt.timestampMs))}," +
                            String.format(Locale.UK, "%.2f,%.1f,%.2f,", pt.temperatureC, pt.humidityPct, dp) +
                            (pt.pressureHpa?.let { String.format(Locale.UK, "%.1f", it) } ?: "") + "\n"
                    )
                }
            }
        }
        return file
    }
}
