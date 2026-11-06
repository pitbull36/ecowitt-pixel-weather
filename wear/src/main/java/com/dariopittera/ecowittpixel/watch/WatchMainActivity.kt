package com.dariopittera.ecowittpixel.watch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.ambient.AmbientLifecycleObserver
import com.dariopittera.ecowittpixel.watch.ui.EcowittWatchTheme
import com.dariopittera.ecowittpixel.watch.ui.WatchRootScreen
import com.dariopittera.ecowittpixel.watch.ui.createNormalizedWatchContext

class WatchMainActivity : ComponentActivity() {

    private val viewModel: WatchViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(createNormalizedWatchContext(newBase))
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            viewModel.setAmbientMode(true)
        }

        override fun onUpdateAmbient() {
            viewModel.onAmbientUpdate()
        }

        override fun onExitAmbient() {
            viewModel.setAmbientMode(false)
        }
    }

    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        handleIncomingDeepLink(intent)

        setContent {
            EcowittWatchTheme {
                val snapshot by viewModel.snapshot.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val breaches by viewModel.breaches.collectAsState()
                val carouselIndex by viewModel.carouselIndex.collectAsState()
                val detailChannelId by viewModel.detailChannelId.collectAsState()
                val scrubFraction by viewModel.historyScrubFraction.collectAsState()
                val detailIsWeekRange by viewModel.detailIsWeekRange.collectAsState()
                val isAmbient by viewModel.isAmbient.collectAsState()
                val ambientTick by viewModel.ambientMinuteTick.collectAsState()
                val statusText by viewModel.statusText.collectAsState()
                val otaLabel by viewModel.otaLabel.collectAsState()
                val isCheckingOta by viewModel.isCheckingOta.collectAsState()

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                WatchRootScreen(
                    snapshot = snapshot,
                    settings = settings,
                    breaches = breaches,
                    carouselIndex = carouselIndex,
                    detailChannelId = detailChannelId,
                    historyScrubFraction = scrubFraction,
                    detailIsWeekRange = detailIsWeekRange,
                    isAmbient = isAmbient,
                    ambientMinuteTick = ambientTick,
                    statusText = statusText,
                    otaLabel = otaLabel,
                    isCheckingOta = isCheckingOta,
                    onStepCarouselByCrown = viewModel::stepCarouselByCrown,
                    onSelectCarouselPage = viewModel::selectCarouselPage,
                    onOpenRoomDetail = viewModel::openRoomDetail,
                    onCloseRoomDetail = viewModel::closeRoomDetail,
                    onResetDetailView = viewModel::resetDetailView,
                    onToggleDetailHistoryRange = viewModel::toggleDetailHistoryRange,
                    onDetailCrownScroll = viewModel::onDetailCrownScroll,
                    onSyncNow = { viewModel.syncHybridNow(quiet = false) },
                    onCheckWatchOta = viewModel::checkAndInstallWatchOta
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForegroundStart()
    }

    override fun onStop() {
        viewModel.onForegroundStop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        if (intent == null) return
        val extraCh = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val uriCh = intent.data?.lastPathSegment
        val targetChannel = extraCh ?: uriCh
        if (!targetChannel.isNullOrBlank()) {
            viewModel.openRoomDetail(targetChannel)
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }
}
