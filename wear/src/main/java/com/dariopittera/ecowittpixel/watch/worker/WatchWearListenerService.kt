package com.dariopittera.ecowittpixel.watch.worker

import android.content.Intent
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import com.dariopittera.ecowittpixel.watch.WatchMainActivity
import com.dariopittera.ecowittpixel.watch.WatchViewModel
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WatchWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        serviceScope.launch {
            val store = LocalHistoryStore(applicationContext)
            WearDataLayerBridge.pullFromDataLayer(applicationContext, store)
            WatchViewModel.refreshWatchSurfaces(applicationContext)
            sendBroadcast(
                Intent(WatchViewModel.ACTION_WATCH_DATA_UPDATED).apply {
                    setPackage(packageName)
                }
            )
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            WearDataLayerBridge.PATH_INSTANT_PUSH -> {
                serviceScope.launch {
                    val store = LocalHistoryStore(applicationContext)
                    WearDataLayerBridge.applyIncomingPayloadBytes(
                        messageEvent.data,
                        store,
                        applicationContext
                    )
                    WatchViewModel.refreshWatchSurfaces(applicationContext)
                    sendBroadcast(
                        Intent(WatchViewModel.ACTION_WATCH_DATA_UPDATED).apply {
                            setPackage(packageName)
                        }
                    )
                }
            }
            WearDataLayerBridge.PATH_OPEN_ROOM_ON_WATCH -> {
                val channelId = String(messageEvent.data, Charsets.UTF_8).ifBlank { "gateway" }
                val openIntent = Intent(applicationContext, WatchMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(WatchMainActivity.EXTRA_CHANNEL_ID, channelId)
                }
                startActivity(openIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
