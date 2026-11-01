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
        val defaults = AppSettings()
        val roomConfigsJson = prefs.getString("room_configs_json", null)
        val roomConfigs = if (roomConfigsJson != null) {
            parseRoomConfigs(roomConfigsJson)
        } else {
            AppSettings.defaultRoomConfigs()
        }
        val customCriteriaJson = prefs.getString("custom_criteria_json", null)
        val customCriteria = if (customCriteriaJson != null) {
            parseCustomCriteria(customCriteriaJson)
        } else {
            AppSettings.defaultCustomCriteria()
        }
        return AppSettings(
            gatewayIp = prefs.getString("gateway_ip", defaults.gatewayIp) ?: defaults.gatewayIp,
            cloudApplicationKey = prefs.getString("cloud_app_key", "") ?: "",
            cloudApiKey = prefs.getString("cloud_api_key", "") ?: "",
            cloudDeviceMac = prefs.getString("cloud_mac", "") ?: "",
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
                    // Preserve both 7-day (1 week) history and dense 24h history for watch scrubbing
                    val olderWeek = room.history.filter { it.timestampMs in cutoff7d until cutoff24h }
                    val recent24h = room.history.filter { it.timestampMs >= cutoff24h }.ifEmpty {
                        room.history.takeLast(48)
                    }
                    val sampledPts = ArrayList<HistoricalPoint>()
                    if (olderWeek.isNotEmpty()) {
                        val weekStep = (olderWeek.size / 60).coerceAtLeast(1)
                        for (i in olderWeek.indices step weekStep) {
                            sampledPts.add(olderWeek[i])
                        }
                    }
                    if (recent24h.isNotEmpty()) {
                        val dayStep = (recent24h.size / 48).coerceAtLeast(1)
                        for (i in recent24h.indices step dayStep) {
                            sampledPts.add(recent24h[i])
                        }
                        if ((recent24h.size - 1) % dayStep != 0) {
                            sampledPts.add(recent24h.last())
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
        }
        return root.toString()
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
                val finalHistory = historyPts.ifEmpty {
                    loadChannelHistory(chId, System.currentTimeMillis() - 7L * 24L * 3600L * 1000L)
                }
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
                syncMessage = root.optString("syncMessage", "Cached snapshot")
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
