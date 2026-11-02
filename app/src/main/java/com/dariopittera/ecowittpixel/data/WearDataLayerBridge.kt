package com.dariopittera.ecowittpixel.data

import android.content.Context
import android.net.Uri
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

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
    private const val KEY_PHONE_LAN_IP = "phone_lan_ip"
    private const val KEY_TIMESTAMP_MS = "timestamp_ms"
    private const val PREF_PHONE_LAN_IP = "synced_phone_lan_ip"
    const val PHONE_SYNC_PORT = 8765
    const val PHONE_UDP_DISCOVERY_PORT = 8766
    private const val UDP_DISCOVER_MAGIC = "ECOWITT_WATCH_DISCOVER"

    private val lanServerRunning = AtomicBoolean(false)
    private val udpServerRunning = AtomicBoolean(false)
    private val lanServerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts both:
     * 1. A local HTTP server on TCP port 8765 (`/ecowitt_sync`) on the companion phone.
     * 2. A local UDP broadcast responder on UDP port 8766 so any Wear OS watch can discover the
     *    companion phone in a single UDP packet (< 30 ms) without scanning 254 IPs.
     */
    fun startPhoneLanSyncServer(context: Context) {
        val appContext = context.applicationContext
        if (lanServerRunning.compareAndSet(false, true)) {
            lanServerScope.launch {
                runCatching {
                    val serverSocket = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(PHONE_SYNC_PORT))
                    }
                    while (true) {
                        val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                        lanServerScope.launch {
                            runCatching {
                                client.soTimeout = 3000
                                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                                val requestLine = reader.readLine() ?: ""
                                if (requestLine.contains("/ecowitt_sync")) {
                                    val bodyBytes = buildCurrentSyncPayloadBytes(appContext)
                                    val header = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/json; charset=utf-8\r\n" +
                                        "Content-Length: ${bodyBytes.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    val out = client.getOutputStream()
                                    out.write(header.toByteArray(Charsets.UTF_8))
                                    out.write(bodyBytes)
                                    out.flush()
                                }
                            }
                            runCatching { client.close() }
                        }
                    }
                }.onFailure {
                    lanServerRunning.set(false)
                }
            }
        }

        if (udpServerRunning.compareAndSet(false, true)) {
            lanServerScope.launch {
                runCatching {
                    val socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(PHONE_UDP_DISCOVERY_PORT))
                    }
                    val buf = ByteArray(1024)
                    while (true) {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (msg.startsWith(UDP_DISCOVER_MAGIC)) {
                            val replyObj = JSONObject().apply {
                                put(KEY_PHONE_LAN_IP, findLocalIpv4Address() ?: "")
                                put("port", PHONE_SYNC_PORT)
                            }.toString().toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(
                                replyObj,
                                replyObj.size,
                                packet.address,
                                packet.port
                            )
                            runCatching { socket.send(replyPacket) }
                        }
                    }
                }.onFailure {
                    udpServerRunning.set(false)
                }
            }
        }
    }

    private suspend fun buildCurrentSyncPayloadBytes(context: Context): ByteArray {
        val store = LocalHistoryStore(context)
        val settings = store.loadSettings()
        val liveQuerySettings = if (settings.gatewayIp.isNotBlank() ||
            (settings.cloudApplicationKey.isNotBlank() && settings.cloudApiKey.isNotBlank())
        ) {
            settings.copy(demoModeEnabled = false)
        } else {
            settings
        }
        val cachedSnap = store.loadWidgetSnapshotCache()
        val rawSnap = if (cachedSnap != null && !cachedSnap.isSimulatedOrPreview) {
            cachedSnap
        } else {
            EcowittApiClient(store).fetchAtmosphere(liveQuerySettings, SimulationParameters())
        }
        val syncedSnap = settings.applyToSnapshot(rawSnap)
        return JSONObject().apply {
            put(KEY_SETTINGS_JSON, store.serializeSettingsJson(settings))
            put(KEY_SNAPSHOT_JSON, store.serializeSnapshotJson(syncedSnap))
            put(KEY_PHONE_LAN_IP, findLocalIpv4Address() ?: "")
            put(KEY_TIMESTAMP_MS, System.currentTimeMillis())
        }.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun getConnectedNodes(context: Context): List<WearNodeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.map { node ->
                WearNodeInfo(
                    id = node.id,
                    displayName = node.displayName.ifBlank { "Wear OS Watch" },
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
        startPhoneLanSyncServer(context)
        try {
            val syncedSnapshot = settings.applyToSnapshot(snapshot)
            val settingsJson = store.serializeSettingsJson(settings)
            val snapshotJson = store.serializeSnapshotJson(syncedSnapshot)
            val phoneIp = findLocalIpv4Address() ?: ""
            val nowMs = System.currentTimeMillis()

            val putDataMapReq = PutDataMapRequest.create(PATH_SYNC_PAYLOAD).apply {
                dataMap.putString(KEY_SETTINGS_JSON, settingsJson)
                dataMap.putString(KEY_SNAPSHOT_JSON, snapshotJson)
                dataMap.putString(KEY_PHONE_LAN_IP, phoneIp)
                dataMap.putLong(KEY_TIMESTAMP_MS, nowMs)
            }
            Wearable.getDataClient(context)
                .putDataItem(putDataMapReq.asPutDataRequest().setUrgent())
                .await()

            val nodes = getConnectedNodes(context)
            val envelopeBytes = JSONObject().apply {
                put(KEY_SETTINGS_JSON, settingsJson)
                put(KEY_SNAPSHOT_JSON, snapshotJson)
                put(KEY_PHONE_LAN_IP, phoneIp)
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
                "Synced room names, settings, and live telemetry to $names"
            } else {
                "Queued room names and telemetry in Wearable Data Layer and Wi-Fi sync server"
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
                message = "Data layer sync queued on Wi-Fi (${e.localizedMessage ?: "Play Services"})"
            )
        }
    }

    /**
     * Reads synced settings and snapshot from Google Play Services Wearable DataClient.
     * Uses both the zero-argument `dataItems` query (which avoids `FILTER_LITERAL` wildcard
     * mismatches on `wear://&#42;/ecowitt/sync_payload`) and `DataClient.FILTER_PREFIX`.
     */
    suspend fun pullFromDataLayer(
        context: Context,
        store: LocalHistoryStore
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> = withContext(Dispatchers.IO) {
        try {
            var latestTs = -1L
            var bestSettingsJson: String? = null
            var bestSnapshotJson: String? = null
            var bestPhoneIp: String? = null

            fun inspectItem(item: DataItem) {
                val path = item.uri.path ?: ""
                if (path == PATH_SYNC_PAYLOAD || path.endsWith("sync_payload")) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val ts = dataMap.getLong(KEY_TIMESTAMP_MS, 0L)
                    if (ts >= latestTs) {
                        latestTs = ts
                        bestSettingsJson = dataMap.getString(KEY_SETTINGS_JSON)
                        bestSnapshotJson = dataMap.getString(KEY_SNAPSHOT_JSON)
                        bestPhoneIp = dataMap.getString(KEY_PHONE_LAN_IP)
                    }
                }
            }

            runCatching {
                val allItems = Wearable.getDataClient(context).dataItems.await()
                try {
                    for (item in allItems) {
                        inspectItem(item)
                    }
                } finally {
                    allItems.release()
                }
            }

            if (bestSettingsJson == null && bestSnapshotJson == null) {
                runCatching {
                    val prefixItems = Wearable.getDataClient(context)
                        .getDataItems(Uri.parse("wear://*$PATH_SYNC_PAYLOAD"), DataClient.FILTER_PREFIX)
                        .await()
                    try {
                        for (item in prefixItems) {
                            inspectItem(item)
                        }
                    } finally {
                        prefixItems.release()
                    }
                }
            }

            if (!bestPhoneIp.isNullOrBlank()) {
                savePhoneLanIp(context, bestPhoneIp!!)
            }

            val parsedSettings = bestSettingsJson?.let { store.deserializeSettingsJson(it) }
            val rawSnapshot = bestSnapshotJson?.let { store.deserializeSnapshotJson(it) }
            val effectiveSettings = parsedSettings ?: store.loadSettings()
            val parsedSnapshot = rawSnapshot?.let { effectiveSettings.applyToSnapshot(it) }

            if (parsedSettings != null) {
                store.saveSettings(parsedSettings.copy(demoModeEnabled = false))
                store.markSyncedFromPhone(true)
            }
            if (parsedSnapshot != null && !parsedSnapshot.isSimulatedOrPreview) {
                store.saveWidgetSnapshotCache(parsedSnapshot)
                store.markSyncedFromPhone(true)
                parsedSnapshot.rooms.forEach { room ->
                    if (room.history.isNotEmpty()) {
                        store.recordHistoryBatch(room.channelId, room.history)
                    }
                }
            }
            if (parsedSettings != null || (parsedSnapshot != null && !parsedSnapshot.isSimulatedOrPreview)) {
                return@withContext Pair(parsedSettings, parsedSnapshot)
            }
        } catch (_: Exception) {
            // Fall through to fast local Wi-Fi / UDP peer check
        }

        // Fast fallback 1: check cached Android phone LAN IP if on same Wi-Fi or Bluetooth proxy
        val savedPhoneIp = loadPhoneLanIp(context)
        if (!savedPhoneIp.isNullOrBlank()) {
            val lanPair = fetchPhoneSyncOverHttp(context, store, savedPhoneIp, timeoutMs = 900)
            if (lanPair.first != null || lanPair.second != null) {
                return@withContext lanPair
            }
        }

        // Fast fallback 2: single-packet UDP broadcast discovery (< 350ms)
        val udpPhoneIp = discoverPhoneViaUdpBroadcast(timeoutMs = 350)
        if (!udpPhoneIp.isNullOrBlank()) {
            savePhoneLanIp(context, udpPhoneIp)
            val udpPair = fetchPhoneSyncOverHttp(context, store, udpPhoneIp, timeoutMs = 900)
            if (udpPair.first != null || udpPair.second != null) {
                return@withContext udpPair
            }
        }

        Pair(null, null)
    }

    private fun discoverPhoneViaUdpBroadcast(timeoutMs: Int): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val reqBytes = UDP_DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
                val targets = mutableSetOf("255.255.255.255")
                findLocalSubnetPrefix()?.let { targets.add("$it.255") }
                for (target in targets) {
                    runCatching {
                        val addr = InetAddress.getByName(target)
                        socket.send(DatagramPacket(reqBytes, reqBytes.size, addr, PHONE_UDP_DISCOVERY_PORT))
                    }
                }
                val buf = ByteArray(1024)
                val reply = DatagramPacket(buf, buf.size)
                socket.receive(reply)
                val json = JSONObject(String(reply.data, 0, reply.length, Charsets.UTF_8))
                val reportedIp = json.optString(KEY_PHONE_LAN_IP, "")
                reportedIp.ifBlank { reply.address?.hostAddress }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Discovers either:
     * 1. The paired Android phone (`http://<ip>:8765/ecowitt_sync`) via UDP broadcast or subnet scan,
     *    returning the user's custom room names, archetypes, Cloud keys, and live telemetry.
     * 2. The Ecowitt GW1200 gateway (`http://<ip>/get_livedata_info`) directly over home Wi-Fi
     *    or Wear OS Bluetooth proxy.
     */
    suspend fun discoverPhoneOrGatewayOnLan(
        context: Context,
        store: LocalHistoryStore
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> = withContext(Dispatchers.IO) {
        val savedPhoneIp = loadPhoneLanIp(context)
        if (!savedPhoneIp.isNullOrBlank()) {
            val fast = fetchPhoneSyncOverHttp(context, store, savedPhoneIp, timeoutMs = 900)
            if (fast.first != null || fast.second != null) {
                return@withContext fast
            }
        }

        val udpIp = discoverPhoneViaUdpBroadcast(timeoutMs = 450)
        if (!udpIp.isNullOrBlank()) {
            savePhoneLanIp(context, udpIp)
            val fromUdp = fetchPhoneSyncOverHttp(context, store, udpIp, timeoutMs = 1000)
            if (fromUdp.first != null || fromUdp.second != null) {
                return@withContext fromUdp
            }
        }

        // Build candidate /24 prefixes even when watch wlan0 is asleep behind Wear OS Bluetooth proxy
        val prefixes = LinkedHashSet<String>()
        findLocalSubnetPrefix()?.let { prefixes.add(it) }
        val configuredGwIp = store.loadSettings().gatewayIp.trim()
        val gwParts = configuredGwIp.split(".")
        if (gwParts.size == 4) {
            prefixes.add("${gwParts[0]}.${gwParts[1]}.${gwParts[2]}")
        }
        prefixes.add("192.168.1")
        prefixes.add("192.168.0")

        val selfIp = findLocalIpv4Address()
        val semaphore = Semaphore(48)
        var discoveredGwIp: String? = null
        var phoneSyncResult: Pair<AppSettings?, HomeAtmosphereSnapshot?>? = null

        for (prefix in prefixes) {
            if (phoneSyncResult != null) break
            coroutineScope {
                val jobs = (1..254).map { host ->
                    async {
                        val candidateIp = "$prefix.$host"
                        if (candidateIp == selfIp) return@async
                        semaphore.withPermit {
                            if (phoneSyncResult == null) {
                                val pair = fetchPhoneSyncOverHttp(context, store, candidateIp, timeoutMs = 550)
                                if (pair.first != null || pair.second != null) {
                                    phoneSyncResult = pair
                                    return@withPermit
                                }
                            }
                            if (discoveredGwIp == null) {
                                runCatching {
                                    val conn = (URL("http://$candidateIp/get_livedata_info").openConnection() as HttpURLConnection).apply {
                                        requestMethod = "GET"
                                        connectTimeout = 550
                                        readTimeout = 550
                                    }
                                    try {
                                        if (conn.responseCode in 200..299) {
                                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                                            if (text.contains("wh25") || text.contains("common_list") || text.contains("ch_aisle")) {
                                                discoveredGwIp = candidateIp
                                            }
                                        }
                                    } finally {
                                        conn.disconnect()
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
            if (phoneSyncResult != null || discoveredGwIp != null) break
        }

        phoneSyncResult?.let { return@withContext it }

        val gwIp = discoveredGwIp
        if (!gwIp.isNullOrBlank()) {
            val currentSettings = store.loadSettings()
            val updatedSettings = currentSettings.copy(
                gatewayIp = gwIp,
                demoModeEnabled = false,
                preferLocalLan = true
            )
            store.saveSettings(updatedSettings)
            val api = EcowittApiClient(store)
            val liveSnap = runCatching {
                api.fetchAtmosphere(updatedSettings, SimulationParameters())
            }.getOrNull()
            return@withContext Pair(updatedSettings, liveSnap?.let { updatedSettings.applyToSnapshot(it) })
        }

        Pair(null, null)
    }

    private fun fetchPhoneSyncOverHttp(
        context: Context,
        store: LocalHistoryStore,
        ip: String,
        timeoutMs: Int
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> {
        return try {
            val conn = (URL("http://$ip:$PHONE_SYNC_PORT/ecowitt_sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return Pair(null, null)
                val bytes = conn.inputStream.readBytes()
                savePhoneLanIp(context, ip)
                applyIncomingPayloadBytes(bytes, store, context)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    fun applyIncomingPayloadBytes(
        payload: ByteArray,
        store: LocalHistoryStore,
        context: Context? = null
    ): Pair<AppSettings?, HomeAtmosphereSnapshot?> {
        return try {
            val root = JSONObject(String(payload, Charsets.UTF_8))
            val settingsJson = root.optString(KEY_SETTINGS_JSON, "")
            val snapshotJson = root.optString(KEY_SNAPSHOT_JSON, "")
            val phoneIp = root.optString(KEY_PHONE_LAN_IP, "")
            if (context != null && phoneIp.isNotBlank()) {
                savePhoneLanIp(context, phoneIp)
            }

            val parsedSettings = if (settingsJson.isNotBlank()) {
                store.deserializeSettingsJson(settingsJson)?.copy(demoModeEnabled = false)
            } else null
            val rawSnapshot = if (snapshotJson.isNotBlank()) {
                store.deserializeSnapshotJson(snapshotJson)
            } else null

            val effectiveSettings = parsedSettings ?: store.loadSettings()
            val parsedSnapshot = rawSnapshot?.let { effectiveSettings.applyToSnapshot(it) }

            if (parsedSettings != null) {
                store.saveSettings(parsedSettings)
                store.markSyncedFromPhone(true)
            }
            if (parsedSnapshot != null && !parsedSnapshot.isSimulatedOrPreview) {
                store.saveWidgetSnapshotCache(parsedSnapshot)
                store.markSyncedFromPhone(true)
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

    private fun savePhoneLanIp(context: Context, ip: String) {
        context.getSharedPreferences("ecowitt_pixel_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PHONE_LAN_IP, ip.trim())
            .apply()
    }

    private fun loadPhoneLanIp(context: Context): String? {
        return context.getSharedPreferences("ecowitt_pixel_prefs", Context.MODE_PRIVATE)
            .getString(PREF_PHONE_LAN_IP, null)
    }

    private fun findLocalIpv4Address(): String? {
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
                            return ip
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findLocalSubnetPrefix(): String? {
        val ip = findLocalIpv4Address() ?: return null
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
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
