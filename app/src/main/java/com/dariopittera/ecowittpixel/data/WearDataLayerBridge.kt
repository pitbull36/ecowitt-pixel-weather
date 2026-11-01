package com.dariopittera.ecowittpixel.data

import android.content.Context
import android.net.Uri
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WearNodeInfo(
    val id: String,
    val displayName: String,
    val isNearby: Boolean
)

data class WearSyncResult(
    val success: Boolean,
    val connectedNodes: List<WearNodeInfo>,
    val message: String
)

object WearDataLayerBridge {
    const val PATH_SYNC_PAYLOAD = "/ecowitt/sync_payload"
    const val PATH_INSTANT_PUSH = "/ecowitt/instant_push"
    const val PATH_REQUEST_SYNC = "/ecowitt/request_sync"
    const val PATH_OPEN_ROOM_ON_WATCH = "/ecowitt/open_room"

    private const val KEY_SETTINGS_JSON = "settings_json"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    private const val KEY_TIMESTAMP_MS = "timestamp_ms"

    suspend fun getConnectedNodes(context: Context): List<WearNodeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.map { node ->
                WearNodeInfo(
                    id = node.id,
                    displayName = node.displayName.ifBlank { "Pixel Watch" },
                    isNearby = node.isNearby
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun hasNearbyPeer(context: Context): Boolean = withContext(Dispatchers.IO) {
        getConnectedNodes(context).any { it.isNearby }
    }

    suspend fun pushToWatch(
        context: Context,
        store: LocalHistoryStore,
        settings: AppSettings,
        snapshot: HomeAtmosphereSnapshot
    ): WearSyncResult = withContext(Dispatchers.IO) {
        try {
            val settingsJson = store.serializeSettingsJson(settings)
            val snapshotJson = store.serializeSnapshotJson(snapshot)
            val nowMs = System.currentTimeMillis()

            val putDataMapReq = PutDataMapRequest.create(PATH_SYNC_PAYLOAD).apply {
                dataMap.putString(KEY_SETTINGS_JSON, settingsJson)
                dataMap.putString(KEY_SNAPSHOT_JSON, snapshotJson)
                dataMap.putLong(KEY_TIMESTAMP_MS, nowMs)
            }
            Wearable.getDataClient(context)
                .putDataItem(putDataMapReq.asPutDataRequest().setUrgent())
                .await()

            val nodes = getConnectedNodes(context)
            val envelopeBytes = JSONObject().apply {
                put(KEY_SETTINGS_JSON, settingsJson)
                put(KEY_SNAPSHOT_JSON, snapshotJson)
                put(KEY_TIMESTAMP_MS, nowMs)
            }.toString().toByteArray(Charsets.UTF_8)

            val msgClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                runCatching {
                    msgClient.sendMessage(node.id, PATH_INSTANT_PUSH, envelopeBytes).await()
                }
            }

            val summary = if (nodes.isNotEmpty()) {
                val names = nodes.joinToString(", ") { it.displayName }
                "Synced telemetry and settings to $names"
            } else {
                "Queued telemetry in Wearable Data Layer (will sync when Pixel Watch connects)"
            }
            WearSyncResult(
                success = true,
                connectedNodes = nodes,
                message = summary
            )
        } catch (e: Exception) {
            WearSyncResult(
                success = false,
                connectedNodes = emptyList(),
                message = "Data layer sync unavailable: ${e.localizedMessage ?: "check Play Services"}"
            )
        }
    }

    suspend fun pullFromDataLayer(
        context: Context,
        store: LocalHistoryStore
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> = withContext(Dispatchers.IO) {
        try {
            val dataItems = Wearable.getDataClient(context)
                .getDataItems(Uri.parse("wear://*$PATH_SYNC_PAYLOAD"))
                .await()
            var latestTs = -1L
            var bestSettingsJson: String? = null
            var bestSnapshotJson: String? = null

            try {
                for (item in dataItems) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val ts = dataMap.getLong(KEY_TIMESTAMP_MS, 0L)
                    if (ts >= latestTs) {
                        latestTs = ts
                        bestSettingsJson = dataMap.getString(KEY_SETTINGS_JSON)
                        bestSnapshotJson = dataMap.getString(KEY_SNAPSHOT_JSON)
                    }
                }
            } finally {
                dataItems.release()
            }

            val parsedSettings = bestSettingsJson?.let { store.deserializeSettingsJson(it) }
            val parsedSnapshot = bestSnapshotJson?.let { store.deserializeSnapshotJson(it) }

            if (parsedSettings != null) {
                store.saveSettings(parsedSettings)
            }
            if (parsedSnapshot != null) {
                store.saveWidgetSnapshotCache(parsedSnapshot)
                parsedSnapshot.rooms.forEach { room ->
                    if (room.history.isNotEmpty()) {
                        store.recordHistoryBatch(room.channelId, room.history)
                    }
                }
            }
            Pair(parsedSettings, parsedSnapshot)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    fun applyIncomingPayloadBytes(
        payload: ByteArray,
        store: LocalHistoryStore
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> {
        return try {
            val root = JSONObject(String(payload, Charsets.UTF_8))
            val settingsJson = root.optString(KEY_SETTINGS_JSON, "")
            val snapshotJson = root.optString(KEY_SNAPSHOT_JSON, "")
            val parsedSettings = if (settingsJson.isNotBlank()) {
                store.deserializeSettingsJson(settingsJson)
            } else null
            val parsedSnapshot = if (snapshotJson.isNotBlank()) {
                store.deserializeSnapshotJson(snapshotJson)
            } else null

            if (parsedSettings != null) {
                store.saveSettings(parsedSettings)
            }
            if (parsedSnapshot != null) {
                store.saveWidgetSnapshotCache(parsedSnapshot)
                parsedSnapshot.rooms.forEach { room ->
                    if (room.history.isNotEmpty()) {
                        store.recordHistoryBatch(room.channelId, room.history)
                    }
                }
            }
            Pair(parsedSettings, parsedSnapshot)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    suspend fun requestSyncFromPhone(context: Context): Int = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = getConnectedNodes(context)
            val msgClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                runCatching {
                    msgClient.sendMessage(node.id, PATH_REQUEST_SYNC, ByteArray(0)).await()
                }
            }
            nodes.size
        }.getOrDefault(0)
    }

    suspend fun sendOpenRoomOnWatch(context: Context, channelId: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = getConnectedNodes(context)
            val msgClient = Wearable.getMessageClient(context)
            val bytes = channelId.toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                runCatching {
                    msgClient.sendMessage(node.id, PATH_OPEN_ROOM_ON_WATCH, bytes).await()
                }
            }
            nodes.size
        }.getOrDefault(0)
    }
}
