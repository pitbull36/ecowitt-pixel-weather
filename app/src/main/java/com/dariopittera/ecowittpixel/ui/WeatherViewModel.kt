package com.dariopittera.ecowittpixel.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dariopittera.ecowittpixel.BuildConfig
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.OtaUpdater
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.data.WearNodeInfo
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.widget.EcowittGlanceWidget
import com.dariopittera.ecowittpixel.worker.WeatherSyncWorker
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

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val historyStore = LocalHistoryStore(application)
    private val apiClient = EcowittApiClient(historyStore)
    private val otaUpdater = OtaUpdater(application)

    private val _settings = MutableStateFlow(historyStore.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _simParams = MutableStateFlow(SimulationParameters())
    val simParams: StateFlow<SimulationParameters> = _simParams.asStateFlow()

    private val _snapshot = MutableStateFlow(
        historyStore.loadWidgetSnapshotCache()
            ?: apiClient.generateSimulatedSnapshot(_settings.value, _simParams.value)
    )
    val snapshot: StateFlow<HomeAtmosphereSnapshot> = _snapshot.asStateFlow()

    private val _selectedRoomIndex = MutableStateFlow(0)
    val selectedRoomIndex: StateFlow<Int> = _selectedRoomIndex.asStateFlow()

    private val _wholeHomeOverview = MutableStateFlow(false)
    val wholeHomeOverview: StateFlow<Boolean> = _wholeHomeOverview.asStateFlow()

    private val _isScanningSubnet = MutableStateFlow(false)
    val isScanningSubnet: StateFlow<Boolean> = _isScanningSubnet.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _otaButtonLabel = MutableStateFlow("Check updates")
    val otaButtonLabel: StateFlow<String> = _otaButtonLabel.asStateFlow()

    private val _isCheckingOta = MutableStateFlow(false)
    val isCheckingOta: StateFlow<Boolean> = _isCheckingOta.asStateFlow()

    private val _connectedWatchNodes = MutableStateFlow<List<WearNodeInfo>>(emptyList())
    val connectedWatchNodes: StateFlow<List<WearNodeInfo>> = _connectedWatchNodes.asStateFlow()

    private val _isSyncingWatch = MutableStateFlow(false)
    val isSyncingWatch: StateFlow<Boolean> = _isSyncingWatch.asStateFlow()

    private var pollingJob: Job? = null

    private val watchMessageListener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
        if (event.path == WearDataLayerBridge.PATH_REQUEST_SYNC) {
            viewModelScope.launch {
                pushSnapshotToWatchQuietly(_snapshot.value)
            }
        }
    }

    init {
        WearDataLayerBridge.startPhoneLanSyncServer(application)
        runCatching {
            Wearable.getMessageClient(application).addListener(watchMessageListener)
        }
        refreshNow()
        startAutoPolling()
        WeatherSyncWorker.schedulePeriodicSync(application, _settings.value.pollingIntervalMinutes)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            Wearable.getMessageClient(getApplication<Application>()).removeListener(watchMessageListener)
        }
    }

    private fun startAutoPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (!_settings.value.demoModeEnabled) {
                    val updated = _settings.value.applyToSnapshot(
                        apiClient.fetchAtmosphere(_settings.value, _simParams.value)
                    )
                    _snapshot.value = updated
                    if (_settings.value.notificationsEnabled) {
                        WeatherSyncWorker.evaluateAndNotify(getApplication(), updated, _settings.value)
                    }
                    runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
                    pushSnapshotToWatchQuietly(updated)
                }
            }
        }
    }

    private suspend fun pushSnapshotToWatchQuietly(snap: HomeAtmosphereSnapshot) {
        val result = WearDataLayerBridge.pushToWatch(
            context = getApplication(),
            store = historyStore,
            settings = _settings.value,
            snapshot = _settings.value.applyToSnapshot(snap)
        )
        _connectedWatchNodes.value = result.connectedNodes
    }

    fun syncToWatchNow() {
        if (_isSyncingWatch.value) return
        viewModelScope.launch {
            _isSyncingWatch.value = true
            val currentSettings = _settings.value
            val baseSnapshot = if (!currentSettings.demoModeEnabled &&
                historyStore.isSimulatedSnapshot(_snapshot.value)
            ) {
                apiClient.fetchAtmosphere(currentSettings, _simParams.value)
            } else {
                _snapshot.value
            }
            val synced = currentSettings.applyToSnapshot(baseSnapshot)
            _snapshot.value = synced
            historyStore.saveWidgetSnapshotCache(synced)
            val result = WearDataLayerBridge.pushToWatch(
                context = getApplication(),
                store = historyStore,
                settings = currentSettings,
                snapshot = synced
            )
            _connectedWatchNodes.value = result.connectedNodes
            _statusBanner.value = result.message
            _isSyncingWatch.value = false
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val updated = _settings.value.applyToSnapshot(
                apiClient.fetchAtmosphere(_settings.value, _simParams.value)
            )
            _snapshot.value = updated
            historyStore.saveWidgetSnapshotCache(updated)
            if (_settings.value.notificationsEnabled) {
                WeatherSyncWorker.evaluateAndNotify(getApplication(), updated, _settings.value)
            }
            runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
            pushSnapshotToWatchQuietly(updated)
        }
    }

    fun selectRoomIndex(index: Int) {
        val maxIdx = (_snapshot.value.rooms.size - 1).coerceAtLeast(0)
        _selectedRoomIndex.value = index.coerceIn(0, maxIdx)
    }

    fun toggleWholeHomeOverview() {
        _wholeHomeOverview.value = !_wholeHomeOverview.value
    }

    fun updateSettings(newSettings: AppSettings) {
        val oldSettings = _settings.value
        _settings.value = newSettings
        historyStore.saveSettings(newSettings)
        val immediatelyRenamed = newSettings.applyToSnapshot(_snapshot.value)
        _snapshot.value = immediatelyRenamed
        historyStore.saveWidgetSnapshotCache(immediatelyRenamed)
        WeatherSyncWorker.schedulePeriodicSync(getApplication(), newSettings.pollingIntervalMinutes)
        val connectionChanged = oldSettings.demoModeEnabled != newSettings.demoModeEnabled ||
            oldSettings.gatewayIp != newSettings.gatewayIp ||
            oldSettings.cloudApplicationKey != newSettings.cloudApplicationKey ||
            oldSettings.cloudApiKey != newSettings.cloudApiKey ||
            oldSettings.cloudDeviceMac != newSettings.cloudDeviceMac ||
            oldSettings.preferLocalLan != newSettings.preferLocalLan
        if (connectionChanged) {
            refreshNow()
        } else {
            viewModelScope.launch {
                runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
                pushSnapshotToWatchQuietly(immediatelyRenamed)
            }
        }
    }

    fun updateRoomConfig(channelId: String, updatedConfig: RoomConfig) {
        val currentMap = _settings.value.roomConfigs.toMutableMap()
        currentMap[channelId] = updatedConfig
        updateSettings(_settings.value.copy(roomConfigs = currentMap))
    }

    fun updateSimulationParams(newSim: SimulationParameters) {
        _simParams.value = newSim
        if (_settings.value.demoModeEnabled) {
            val updated = apiClient.generateSimulatedSnapshot(_settings.value, newSim)
            _snapshot.value = updated
            historyStore.saveWidgetSnapshotCache(updated)
            if (_settings.value.notificationsEnabled) {
                WeatherSyncWorker.evaluateAndNotify(getApplication(), updated, _settings.value)
            }
            viewModelScope.launch {
                runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
                pushSnapshotToWatchQuietly(updated)
            }
        }
    }

    fun testActiveNotificationsNow() {
        viewModelScope.launch {
            val results = WeatherSyncWorker.evaluateAndNotify(
                context = getApplication(),
                snapshot = _snapshot.value,
                settings = _settings.value,
                forceTest = true
            )
            _statusBanner.value = if (results.isNotEmpty()) {
                "Dispatched ${results.size} notification(s): ${results.first()}"
            } else {
                "Notification test dispatched to your Pixel status bar"
            }
        }
    }

    fun discoverGatewayOnLan() {
        if (_isScanningSubnet.value) return
        viewModelScope.launch {
            _isScanningSubnet.value = true
            _statusBanner.value = "Scanning local WiFi subnet for GW1200 gateway..."
            val discoveredIp = apiClient.discoverGw1200OnLan()
            _isScanningSubnet.value = false
            if (discoveredIp != null) {
                val updated = _settings.value.copy(
                    gatewayIp = discoveredIp,
                    demoModeEnabled = false,
                    preferLocalLan = true
                )
                updateSettings(updated)
                _statusBanner.value = "Found GW1200 gateway at $discoveredIp and switched to live LAN mode"
            } else {
                _statusBanner.value =
                    "No GW1200 detected on local subnet; check WiFi connection or use Ecowitt cloud keys below"
            }
        }
    }

    fun testLiveConnection() {
        viewModelScope.launch {
            _statusBanner.value = "Connecting to GW1200..."
            val liveTestSettings = _settings.value.copy(demoModeEnabled = false)
            val rawResult = apiClient.fetchAtmosphere(liveTestSettings, _simParams.value)
            val result = _settings.value.applyToSnapshot(rawResult)
            _snapshot.value = result
            historyStore.saveWidgetSnapshotCache(result)
            _statusBanner.value = result.syncMessage
            runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
            pushSnapshotToWatchQuietly(result)
        }
    }

    /**
     * Checks GitHub Releases for a newer versionCode; if available, downloads the APK
     * and launches Android's package installer automatically.
     */
    fun checkAndInstallOtaUpdate() {
        if (_isCheckingOta.value) return
        viewModelScope.launch {
            _isCheckingOta.value = true
            _otaButtonLabel.value = "Checking..."
            val result = otaUpdater.checkForUpdate(BuildConfig.GITHUB_REPO)
            result.fold(
                onSuccess = { release ->
                    if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
                        if (!otaUpdater.canRequestPackageInstalls()) {
                            _isCheckingOta.value = false
                            _otaButtonLabel.value = "Check updates"
                            _statusBanner.value =
                                "Please allow 'Install unknown apps' for Ecowitt atmosphere, then tap Check updates again"
                            otaUpdater.openUnknownAppsSettings()
                            return@fold
                        }
                        _statusBanner.value = "Downloading update ${release.tagName}..."
                        val dlResult = otaUpdater.downloadApk(release.apkDownloadUrl) { pct ->
                            _otaButtonLabel.value = "Downloading ${(pct * 100).toInt()}%"
                        }
                        _isCheckingOta.value = false
                        _otaButtonLabel.value = "Check updates"
                        dlResult.fold(
                            onSuccess = { apkFile ->
                                _statusBanner.value = "Launching installer for ${release.tagName}..."
                                otaUpdater.promptInstall(apkFile)
                            },
                            onFailure = { err ->
                                _statusBanner.value = "Update download failed: ${err.message}"
                            }
                        )
                    } else {
                        _isCheckingOta.value = false
                        _otaButtonLabel.value = "Check updates"
                        _statusBanner.value =
                            "App is up to date (v${BuildConfig.VERSION_NAME}, code ${BuildConfig.VERSION_CODE})"
                    }
                },
                onFailure = { err ->
                    _isCheckingOta.value = false
                    _otaButtonLabel.value = "Check updates"
                    _statusBanner.value = "Could not check updates: ${err.message}"
                }
            )
        }
    }

    fun shareCsvExport() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val csvFile = historyStore.exportHistoryCsv(_snapshot.value)
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                csvFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ecowitt GW1200 sensor history export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Export sensor history CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }
    }
}
