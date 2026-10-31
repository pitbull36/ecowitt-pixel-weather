package com.dariopittera.ecowittpixel.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ConnectionSource
import com.dariopittera.ecowittpixel.model.HistoricalPoint
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
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
            gyroscopeParallaxEnabled = prefs.getBoolean(
                "gyro_parallax",
                defaults.gyroscopeParallaxEnabled
            ),
            roomConfigs = roomConfigs
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
                put("alertHumidityMaxPct", cfg.alertHumidityMaxPct.toDouble())
                put("alertsEnabled", cfg.alertsEnabled)
            }
            roomsObj.put(channelId, obj)
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
            .putBoolean("gyro_parallax", settings.gyroscopeParallaxEnabled)
            .putString("room_configs_json", roomsObj.toString())
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
                result[key] = RoomConfig(
                    channelId = obj.optString("channelId", key),
                    customName = obj.optString("customName", key),
                    archetype = RoomArchetype.fromId(obj.optString("archetype", "living_room")),
                    alertTempMinC = obj.optDouble("alertTempMinC", 16.0).toFloat(),
                    alertTempMaxC = obj.optDouble("alertTempMaxC", 27.0).toFloat(),
                    alertHumidityMaxPct = obj.optDouble("alertHumidityMaxPct", 65.0).toFloat(),
                    alertsEnabled = obj.optBoolean("alertsEnabled", true)
                )
            }
            result
        } catch (_: Exception) {
            result
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
            // Keep up to 35 days of history
            val cutoff = System.currentTimeMillis() - 35L * 24L * 3600L * 1000L
            db.delete("sensor_history", "timestamp_ms < ?", arrayOf(cutoff.toString()))
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
                }
                arr.put(rObj)
            }
            put("rooms", arr)
        }
        prefs.edit().putString("cached_snapshot_json", root.toString()).apply()
    }

    fun loadWidgetSnapshotCache(): HomeAtmosphereSnapshot? {
        val raw = prefs.getString("cached_snapshot_json", null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("rooms")
            val rooms = ArrayList<SensorRoomState>()
            for (i in 0 until arr.length()) {
                val rObj = arr.getJSONObject(i)
                rooms.add(
                    SensorRoomState(
                        channelId = rObj.getString("channelId"),
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
                        } else null
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
                room.history.forEach { pt ->
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
