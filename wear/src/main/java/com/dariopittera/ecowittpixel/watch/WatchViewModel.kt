package com.dariopittera.ecowittpixel.watch

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dariopittera.ecowittpixel.BuildConfig
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.OtaUpdater
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.AlertEvaluator
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.ConnectionSource
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.RoomAlertBreach
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.watch.complication.EcowittComplicationService
import com.dariopittera.ecowittpixel.watch.tile.EcowittTileService
import com.dariopittera.ecowittpixel.watch.worker.WatchSyncWorker
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val historyStore = LocalHistoryStore(application)
    private val apiClient = EcowittApiClient(historyStore)
    private val otaUpdater = OtaUpdater(application)

    private val _settings = MutableStateFlow(
        historyStore.loadSettings().copy(demoModeEnabled = false)
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _snapshot: MutableStateFlow<HomeAtmosphereSnapshot>
    val snapshot: StateFlow<HomeAtmosphereSnapshot>

    private val _breaches: MutableStateFlow<List<RoomAlertBreach>>
    val breaches: StateFlow<List<RoomAlertBreach>>

    // Carousel index: 0..rooms.lastIndex are rooms, rooms.size is the Watch Sync & OTA page
    private val _carouselIndex = MutableStateFlow(0)
    val carouselIndex: StateFlow<Int> = _carouselIndex.asStateFlow()

    // Non-null when the user taps into a specific room (Level 2 detail screen)
    private val _detailChannelId = MutableStateFlow<String?>(null)
    val detailChannelId: StateFlow<String?> = _detailChannelId.asStateFlow()

    // Normalized 0f..1f scrub position along the 24h history (1f = live now)
    private val _historyScrubFraction = MutableStateFlow(1f)
    val historyScrubFraction: StateFlow<Float> = _historyScrubFraction.asStateFlow()

    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    private val _ambientMinuteTick = MutableStateFlow(0)
    val ambientMinuteTick: StateFlow<Int> = _ambientMinuteTick.asStateFlow()

    private val _statusText = MutableStateFlow("Syncing with Pixel 10...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _otaLabel = MutableStateFlow("Watch OTA update")
    val otaLabel: StateFlow<String> = _otaLabel.asStateFlow()

    private val _isCheckingOta = MutableStateFlow(false)
    val isCheckingOta: StateFlow<Boolean> = _isCheckingOta.asStateFlow()

    private var pollingJob: Job? = null

    private val dataLayerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadFromLocalCache()
            val targetRoom = intent.getStringExtra(EXTRA_OPEN_CHANNEL_ID)
            if (!targetRoom.isNullOrBlank()) {
                openRoomDetail(targetRoom)
            }
        }
    }

    private val foregroundDataListener = DataClient.OnDataChangedListener { _: DataEventBuffer ->
        viewModelScope.launch {
            val app = getApplication<Application>()
            val (dlSettings, dlSnap) = WearDataLayerBridge.pullFromDataLayer(app, historyStore)
            if (dlSettings != null || dlSnap != null) {
                applySyncedPair(dlSettings, dlSnap, "Synced with Pixel 10")
                refreshWatchSurfaces(app)
            }
        }
    }

    private val foregroundMessageListener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
        when (event.path) {
            WearDataLayerBridge.PATH_INSTANT_PUSH -> {
                viewModelScope.launch {
                    val app = getApplication<Application>()
                    val (dlSettings, dlSnap) = WearDataLayerBridge.applyIncomingPayloadBytes(
                        event.data,
                        historyStore,
                        app
                    )
                    if (dlSettings != null || dlSnap != null) {
                        applySyncedPair(dlSettings, dlSnap, "Synced with Pixel 10")
                        refreshWatchSurfaces(app)
                    }
                }
            }
            WearDataLayerBridge.PATH_OPEN_ROOM_ON_WATCH -> {
                val ch = String(event.data, Charsets.UTF_8).ifBlank { "gateway" }
                openRoomDetail(ch)
            }
        }
    }

    init {
        // Purge any stale v3.0.0 simulated cache so the watch never mistakes demo data for real cache
        val initialCache = historyStore.loadWidgetSnapshotCache()
        if (initialCache != null && initialCache.isSimulatedOrPreview) {
            historyStore.clearWidgetSnapshotCache()
        }
        val cleanInitial = historyStore.loadWidgetSnapshotCache()?.let { _settings.value.applyToSnapshot(it) }
            ?: apiClient.generateSimulatedSnapshot(_settings.value, SimulationParameters())
        _snapshot = MutableStateFlow(cleanInitial)
        snapshot = _snapshot.asStateFlow()
        _breaches = MutableStateFlow(AlertEvaluator.evaluateBreaches(cleanInitial, _settings.value))
        breaches = _breaches.asStateFlow()

        val filter = IntentFilter(ACTION_WATCH_DATA_UPDATED)
        ContextCompat.registerReceiver(
            application,
            dataLayerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        runCatching {
            Wearable.getDataClient(application).addListener(foregroundDataListener)
            Wearable.getMessageClient(application).addListener(foregroundMessageListener)
        }
        WatchSyncWorker.schedulePeriodicWatchSync(application)
        syncHybridNow(quiet = false)
        startAutoRefresh()
    }

    override fun onCleared() {
        super.onCleared()
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(dataLayerReceiver) }
        runCatching { Wearable.getDataClient(app).removeListener(foregroundDataListener) }
        runCatching { Wearable.getMessageClient(app).removeListener(foregroundMessageListener) }
    }

    private fun applySyncedPair(
        newSettings: AppSettings?,
        newSnapshot: HomeAtmosphereSnapshot?,
        sourceLabel: String
    ) {
        if (newSettings != null) {
            val liveCfg = newSettings.copy(demoModeEnabled = false)
            _settings.value = liveCfg
            historyStore.saveSettings(liveCfg)
            historyStore.markSyncedFromPhone(true)
        }
        val activeSettings = _settings.value.copy(demoModeEnabled = false)
        val snapToApply = if (newSnapshot != null && !newSnapshot.isSimulatedOrPreview) {
            newSnapshot
        } else {
            _snapshot.value
        }
        val renamedSnap = activeSettings.applyToSnapshot(snapToApply)
        _snapshot.value = renamedSnap
        if (!renamedSnap.isSimulatedOrPreview) {
            historyStore.saveWidgetSnapshotCache(renamedSnap)
        }
        _breaches.value = AlertEvaluator.evaluateBreaches(renamedSnap, activeSettings)
        _statusText.value = sourceLabel
    }

    fun reloadFromLocalCache() {
        val loadedSettings = historyStore.loadSettings().copy(demoModeEnabled = false)
        val loadedSnap = historyStore.loadWidgetSnapshotCache()
        _settings.value = loadedSettings
        if (loadedSnap != null && !loadedSnap.isSimulatedOrPreview) {
            val renamed = loadedSettings.applyToSnapshot(loadedSnap)
            _snapshot.value = renamed
            _breaches.value = AlertEvaluator.evaluateBreaches(renamed, loadedSettings)
            _statusText.value = if (historyStore.hasSyncedFromPhone()) {
                "Synced with Pixel 10"
            } else {
                "Live GW1200 (${renamed.gatewayIp})"
            }
        }
    }

    private fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = if (_snapshot.value.isSimulatedOrPreview || !historyStore.hasSyncedFromPhone()) {
                    6_000L
                } else {
                    18_000L
                }
                delay(intervalMs)
                if (!_isAmbient.value) {
                    syncHybridNow(quiet = true)
                }
            }
        }
    }

    fun syncHybridNow(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) {
                _statusText.value = "Syncing with Pixel 10..."
            }
            val app = getApplication<Application>()

            // 1. Request instant push from connected Pixel 10 & pull DataItem / UDP / Wi-Fi peer
            val requestedCount = WearDataLayerBridge.requestSyncFromPhone(app)
            if (requestedCount > 0) {
                delay(400L)
            }
            var (dlSettings, dlSnapshot) = WearDataLayerBridge.pullFromDataLayer(app, historyStore)
            if (dlSettings == null && dlSnapshot == null && requestedCount > 0) {
                delay(500L)
                val retryPair = WearDataLayerBridge.pullFromDataLayer(app, historyStore)
                dlSettings = retryPair.first
                dlSnapshot = retryPair.second
            }

            if (dlSettings != null) {
                val liveCfg = dlSettings.copy(demoModeEnabled = false)
                _settings.value = liveCfg
                historyStore.saveSettings(liveCfg)
                historyStore.markSyncedFromPhone(true)
            }

            val liveSettings = _settings.value.copy(demoModeEnabled = false)
            val nowMs = System.currentTimeMillis()
            val hasRealPhoneSnap = dlSnapshot != null && !dlSnapshot.isSimulatedOrPreview
            val isFreshPhoneSnap = hasRealPhoneSnap && (nowMs - (dlSnapshot?.lastSyncMs ?: 0L)) <= 120_000L

            if (dlSnapshot != null && isFreshPhoneSnap) {
                val syncedSnap = liveSettings.applyToSnapshot(dlSnapshot)
                _snapshot.value = syncedSnap
                historyStore.saveWidgetSnapshotCache(syncedSnap)
                _breaches.value = AlertEvaluator.evaluateBreaches(syncedSnap, liveSettings)
                _statusText.value = "Synced with Pixel 10"
                refreshWatchSurfaces(app)
                return@launch
            }

            // 2. Poll real GW1200 over local Wi-Fi or Ecowitt Cloud API v3 directly
            var directSnap = liveSettings.applyToSnapshot(
                apiClient.fetchAtmosphere(liveSettings, SimulationParameters())
            )

            // 3. If still showing preview/simulated data OR room names haven't synced from phone yet,
            //    auto-discover Pixel 10 (UDP broadcast + HTTP :8765) and GW1200 (:80) on local network
            if (directSnap.isSimulatedOrPreview || !historyStore.hasSyncedFromPhone()) {
                if (!quiet) {
                    _statusText.value = "Finding Pixel 10 / GW1200 on Wi-Fi..."
                }
                val (discSettings, discSnap) = WearDataLayerBridge.discoverPhoneOrGatewayOnLan(app, historyStore)
                if (discSettings != null) {
                    val liveDisc = discSettings.copy(demoModeEnabled = false)
                    _settings.value = liveDisc
                    historyStore.saveSettings(liveDisc)
                }
                if (discSnap != null && !discSnap.isSimulatedOrPreview) {
                    directSnap = _settings.value.applyToSnapshot(discSnap)
                } else if (dlSnapshot != null && !dlSnapshot.isSimulatedOrPreview) {
                    directSnap = _settings.value.applyToSnapshot(dlSnapshot)
                } else {
                    directSnap = _settings.value.applyToSnapshot(directSnap)
                }
            }

            _snapshot.value = directSnap
            if (!directSnap.isSimulatedOrPreview) {
                historyStore.saveWidgetSnapshotCache(directSnap)
            }
            _breaches.value = AlertEvaluator.evaluateBreaches(directSnap, _settings.value)
            _statusText.value = when {
                directSnap.isSimulatedOrPreview -> "Open Pixel 10 app (v6) to sync rooms"
                directSnap.source == ConnectionSource.LOCAL_LAN && historyStore.hasSyncedFromPhone() ->
                    "Synced with Pixel 10 • GW1200 (${directSnap.gatewayIp})"
                directSnap.source == ConnectionSource.LOCAL_LAN ->
                    "Live GW1200 (${directSnap.gatewayIp}) • Open phone app for room names"
                directSnap.source == ConnectionSource.ECOWITT_CLOUD ->
                    "Synced with Pixel 10 • Ecowitt Cloud"
                else -> "Synced with Pixel 10"
            }

            refreshWatchSurfaces(app)
        }
    }

    fun stepCarouselByCrown(delta: Float) {
        val totalPages = _snapshot.value.rooms.size + 1
        if (totalPages <= 1) return
        val step = if (delta > 0f) 1 else if (delta < 0f) -1 else 0
        if (step != 0) {
            _carouselIndex.value = (_carouselIndex.value + step).coerceIn(0, totalPages - 1)
        }
    }

    fun selectCarouselPage(index: Int) {
        val totalPages = _snapshot.value.rooms.size + 1
        _carouselIndex.value = index.coerceIn(0, totalPages - 1)
    }

    fun openRoomDetail(channelId: String) {
        val idx = _snapshot.value.rooms.indexOfFirst { it.channelId == channelId }
        if (idx >= 0) {
            _carouselIndex.value = idx
        }
        _detailChannelId.value = channelId
        _historyScrubFraction.value = 1f
    }

    fun closeRoomDetail() {
        _detailChannelId.value = null
        _historyScrubFraction.value = 1f
    }

    fun resetDetailView() {
        _historyScrubFraction.value = 1f
    }

    fun onDetailCrownScroll(scrollPixels: Float) {
        val normDelta = (scrollPixels / 180f).coerceIn(-0.15f, 0.15f)
        _historyScrubFraction.value = (_historyScrubFraction.value + normDelta * 0.45f).coerceIn(0f, 1f)
    }

    fun setAmbientMode(ambient: Boolean) {
        _isAmbient.value = ambient
        if (ambient) {
            _ambientMinuteTick.value += 1
        }
    }

    fun onAmbientUpdate() {
        _ambientMinuteTick.value += 1
        reloadFromLocalCache()
    }

    fun checkAndInstallWatchOta() {
        if (_isCheckingOta.value) return
        viewModelScope.launch {
            _isCheckingOta.value = true
            _otaLabel.value = "Checking GitHub..."
            val result = otaUpdater.checkForUpdate(
                githubRepo = BuildConfig.GITHUB_REPO,
                preferWatchAsset = true
            )
            result.fold(
                onSuccess = { release ->
                    val currentBaseVersion = BuildConfig.VERSION_CODE % 1000
                    val remoteBaseVersion = (release?.versionCode ?: 0) % 1000
                    if (release != null && remoteBaseVersion > currentBaseVersion) {
                        if (!otaUpdater.canRequestPackageInstalls()) {
                            _isCheckingOta.value = false
                            _otaLabel.value = "Allow install permission"
                            otaUpdater.openUnknownAppsSettings()
                            return@fold
                        }
                        val dl = otaUpdater.downloadApk(release.apkDownloadUrl) { pct ->
                            _otaLabel.value = "Downloading ${(pct * 100).toInt()}%"
                        }
                        _isCheckingOta.value = false
                        dl.fold(
                            onSuccess = { file ->
                                _otaLabel.value = "Installing ${release.tagName}"
                                otaUpdater.promptInstall(file)
                            },
                            onFailure = {
                                _otaLabel.value = "Download failed"
                            }
                        )
                    } else {
                        _isCheckingOta.value = false
                        _otaLabel.value = "Up to date (v${BuildConfig.VERSION_NAME})"
                    }
                },
                onFailure = {
                    _isCheckingOta.value = false
                    _otaLabel.value = "OTA check failed"
                }
            )
        }
    }

    companion object {
        const val ACTION_WATCH_DATA_UPDATED = "com.dariopittera.ecowittpixel.WATCH_DATA_UPDATED"
        const val EXTRA_OPEN_CHANNEL_ID = "extra_open_channel_id"

        fun refreshWatchSurfaces(context: Context) {
            runCatching {
                TileService.getUpdater(context).requestUpdate(EcowittTileService::class.java)
            }
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, EcowittComplicationService::class.java)
                ).requestUpdateAll()
            }
        }
    }
}
