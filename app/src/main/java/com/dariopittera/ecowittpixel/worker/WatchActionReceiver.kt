package com.dariopittera.ecowittpixel.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dariopittera.ecowittpixel.data.WearDataLayerBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: "gateway"
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WearDataLayerBridge.sendOpenRoomOnWatch(context.applicationContext, channelId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_ROOM_ON_WATCH = "com.dariopittera.ecowittpixel.ACTION_OPEN_ROOM_ON_WATCH"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }
}
