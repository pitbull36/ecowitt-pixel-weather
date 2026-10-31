package com.dariopittera.ecowittpixel.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.model.AppSettings
import com.dariopittera.ecowittpixel.model.HomeAtmosphereSnapshot
import com.dariopittera.ecowittpixel.model.RoomConfig
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.dariopittera.ecowittpixel.widget.EcowittGlanceWidget
import com.dariopittera.ecowittpixel.worker.WeatherSyncWorker
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

    private val _settings = MutableStateFlow(historyStore.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _simParams = MutableStateFlow(SimulationParameters())
    val simParams: StateFlow<SimulationParameters> = _simParams.asStateFlow()

    private val _snapshot = MutableStateFlow(
        apiClient.generateSimulatedSnapshot(_settings.value, _simParams.value)
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

    private var pollingJob: Job? = null

    init {
        refreshNow()
        startAutoPolling()
        WeatherSyncWorker.schedulePeriodicSync(application, _settings.value.pollingIntervalMinutes)
    }

    private fun startAutoPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (!_settings.value.demoModeEnabled) {
                    val updated = apiClient.fetchAtmosphere(_settings.value, _simParams.value)
                    _snapshot.value = updated
                    runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
                }
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val updated = apiClient.fetchAtmosphere(_settings.value, _simParams.value)
            _snapshot.value = updated
            runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
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
        _settings.value = newSettings
        historyStore.saveSettings(newSettings)
        WeatherSyncWorker.schedulePeriodicSync(getApplication(), newSettings.pollingIntervalMinutes)
        refreshNow()
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
            viewModelScope.launch {
                runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
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
                    "No GW1200 detected on local subnet; check WiFi connection or enter IP manually"
            }
        }
    }

    fun testLiveConnection() {
        viewModelScope.launch {
            _statusBanner.value = "Testing GW1200 connection..."
            val liveTestSettings = _settings.value.copy(demoModeEnabled = false)
            val result = apiClient.fetchAtmosphere(liveTestSettings, _simParams.value)
            _snapshot.value = result
            _statusBanner.value = result.syncMessage
            runCatching { EcowittGlanceWidget().updateAll(getApplication()) }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
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
