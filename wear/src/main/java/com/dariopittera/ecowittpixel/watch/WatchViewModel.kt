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
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.RoomAlertBreach
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.watch.complication.EcowittComplicationService
import com.dariopittera.ecowittpixel.watch.tile.EcowittTileService
import com.dariopittera.ecowittpixel.watch.worker.WatchSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DetailCrownMode(val label: String) {
    SCRUB_24H("24h scrub"),
    TILT_3D_ROOM("3D tilt")
}

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val historyStore = LocalHistoryStore(application)
    private val apiClient = EcowittApiClient(historyStore)
    private val otaUpdater = OtaUpdater(application)

    private val _settings = MutableStateFlow(historyStore.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _snapshot = MutableStateFlow(
        historyStore.loadWidgetSnapshotCache()
            ?: apiClient.generateSimulatedSnapshot(_settings.value, SimulationParameters())
    )
    val snapshot: StateFlow<HomeAtmosphereSnapshot> = _snapshot.asStateFlow()

    private val _breaches = MutableStateFlow<List<RoomAlertBreach>>(
        AlertEvaluator.evaluateBreaches(_snapshot.value, _settings.value)
    )
    val breaches: StateFlow<List<RoomAlertBreach>> = _breaches.asStateFlow()

    // Carousel index: 0..rooms.lastIndex are rooms, rooms.size is the Watch Sync & OTA page
    private val _carouselIndex = MutableStateFlow(0)
    val carouselIndex: StateFlow<Int> = _carouselIndex.asStateFlow()

    // Non-null when the user taps into a specific room (Level 2 detail screen)
    private val _detailChannelId = MutableStateFlow<String?>(null)
    val detailChannelId: StateFlow<String?> = _detailChannelId.asStateFlow()

    private val _detailCrownMode = MutableStateFlow(DetailCrownMode.SCRUB_24H)
    val detailCrownMode: StateFlow<DetailCrownMode> = _detailCrownMode.asStateFlow()

    // normalized 0f..1f scrub position along the 24h history (1f = live now)
    private val _historyScrubFraction = MutableStateFlow(1f)
    val historyScrubFraction: StateFlow<Float> = _historyScrubFraction.asStateFlow()

    private val _crownYawOffset = MutableStateFlow(0f)
    val crownYawOffset: StateFlow<Float> = _crownYawOffset.asStateFlow()

    private val _crownPitchOffset = MutableStateFlow(0f)
    val crownPitchOffset: StateFlow<Float> = _crownPitchOffset.asStateFlow()

    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    private val _ambientMinuteTick = MutableStateFlow(0)
    val ambientMinuteTick: StateFlow<Int> = _ambientMinuteTick.asStateFlow()

    private val _statusText = MutableStateFlow("Synced")
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

    init {
        val filter = IntentFilter(ACTION_WATCH_DATA_UPDATED)
        ContextCompat.registerReceiver(
            application,
            dataLayerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        WatchSyncWorker.schedulePeriodicWatchSync(application)
        syncHybridNow()
        startAutoRefresh()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unregisterReceiver(dataLayerReceiver) }
    }

    fun reloadFromLocalCache() {
        val loadedSettings = historyStore.loadSettings()
        val loadedSnap = historyStore.loadWidgetSnapshotCache()
        _settings.value = loadedSettings
        if (loadedSnap != null) {
            _snapshot.value = loadedSnap
            _breaches.value = AlertEvaluator.evaluateBreaches(loadedSnap, loadedSettings)
            _statusText.value = "Phone Data Layer live"
        }
    }

    private fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(20_000L)
                if (!_isAmbient.value) {
                    syncHybridNow(quiet = true)
                }
            }
        }
    }

    fun syncHybridNow(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) {
                _statusText.value = "Syncing..."
            }
            val app = getApplication<Application>()

            // 1. Request instant push from connected Pixel 10 & pull latest DataItem
            val requestedCount = WearDataLayerBridge.requestSyncFromPhone(app)
            val (dlSettings, dlSnapshot) = WearDataLayerBridge.pullFromDataLayer(app, historyStore)
            if (dlSettings != null) {
                _settings.value = dlSettings
            }
            val nowMs = System.currentTimeMillis()
            val hasFreshPhoneSnapshot = dlSnapshot != null && (nowMs - dlSnapshot.lastSyncMs) <= 90_000L

            if (dlSnapshot != null && (hasFreshPhoneSnapshot || requestedCount > 0)) {
                _snapshot.value = dlSnapshot
                _breaches.value = AlertEvaluator.evaluateBreaches(dlSnapshot, _settings.value)
                _statusText.value = "Synced with Pixel 10"
            } else {
                // 2. Direct watch Wi-Fi / Cloud fallback when away from phone or no DataItem yet
                val directSnap = apiClient.fetchAtmosphere(_settings.value, SimulationParameters())
                _snapshot.value = directSnap
                historyStore.saveWidgetSnapshotCache(directSnap)
                _breaches.value = AlertEvaluator.evaluateBreaches(directSnap, _settings.value)
                _statusText.value = directSnap.source.label
            }

            refreshWatchSurfaces(app)
        }
    }

    fun stepCarouselByCrown(delta: Float) {
        val totalPages = _snapshot.value.rooms.size + 1 // rooms + settings/OTA page
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
        _detailCrownMode.value = DetailCrownMode.SCRUB_24H
        _historyScrubFraction.value = 1f
        _crownYawOffset.value = 0f
        _crownPitchOffset.value = 0f
    }

    fun closeRoomDetail() {
        _detailChannelId.value = null
        _historyScrubFraction.value = 1f
        _crownYawOffset.value = 0f
        _crownPitchOffset.value = 0f
    }

    fun toggleDetailCrownMode() {
        _detailCrownMode.value = if (_detailCrownMode.value == DetailCrownMode.SCRUB_24H) {
            DetailCrownMode.TILT_3D_ROOM
        } else {
            DetailCrownMode.SCRUB_24H
        }
    }

    fun resetDetailView() {
        _historyScrubFraction.value = 1f
        _crownYawOffset.value = 0f
        _crownPitchOffset.value = 0f
    }

    fun onDetailCrownScroll(scrollPixels: Float) {
        val normDelta = (scrollPixels / 180f).coerceIn(-0.15f, 0.15f)
        if (_detailCrownMode.value == DetailCrownMode.SCRUB_24H) {
            _historyScrubFraction.value = (_historyScrubFraction.value + normDelta * 0.45f).coerceIn(0f, 1f)
        } else {
            _crownYawOffset.value = (_crownYawOffset.value + normDelta * 0.85f).coerceIn(-0.52f, 0.52f)
            _crownPitchOffset.value = (_crownPitchOffset.value + normDelta * 0.25f).coerceIn(-0.18f, 0.18f)
        }
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
