package com.dariopittera.ecowittpixel.worker

import com.dariopittera.ecowittpixel.data.EcowittApiClient
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.model.SimulationParameters
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == WearDataLayerBridge.PATH_REQUEST_SYNC) {
            serviceScope.launch {
                val store = LocalHistoryStore(applicationContext)
                val settings = store.loadSettings()
                val api = EcowittApiClient(store)
                val cached = store.loadWidgetSnapshotCache()
                if (cached != null) {
                    WearDataLayerBridge.pushToWatch(
                        applicationContext,
                        store,
                        settings,
                        settings.applyToSnapshot(cached)
                    )
                }
                val liveSettings = if (settings.gatewayIp.isNotBlank() ||
                    (settings.cloudApplicationKey.isNotBlank() && settings.cloudApiKey.isNotBlank())
                ) {
                    settings.copy(demoModeEnabled = false)
                } else {
                    settings
                }
                val fresh = api.fetchAtmosphere(liveSettings, SimulationParameters())
                WearDataLayerBridge.pushToWatch(
                    applicationContext,
                    store,
                    settings,
                    settings.applyToSnapshot(fresh)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
