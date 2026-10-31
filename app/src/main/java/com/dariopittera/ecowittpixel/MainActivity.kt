package com.dariopittera.ecowittpixel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dariopittera.ecowittpixel.ui.WeatherViewModel
import com.dariopittera.ecowittpixel.ui.screens.AnalyticsScreen
import com.dariopittera.ecowittpixel.ui.screens.DioramaHomeScreen
import com.dariopittera.ecowittpixel.ui.screens.SettingsScreen
import com.dariopittera.ecowittpixel.ui.theme.CoolCyan
import com.dariopittera.ecowittpixel.ui.theme.EcowittPixelTheme
import com.dariopittera.ecowittpixel.ui.theme.PixelSkyNavy
import com.dariopittera.ecowittpixel.ui.theme.PixelSurfaceCard

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EcowittPixelTheme {
                val snapshot by viewModel.snapshot.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val simParams by viewModel.simParams.collectAsState()
                val selectedRoomIdx by viewModel.selectedRoomIndex.collectAsState()
                val wholeHomeOverview by viewModel.wholeHomeOverview.collectAsState()
                val isScanningSubnet by viewModel.isScanningSubnet.collectAsState()
                val statusBanner by viewModel.statusBanner.collectAsState()

                var activeTab by rememberSaveable { mutableIntStateOf(0) }

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    containerColor = PixelSkyNavy,
                    bottomBar = {
                        NavigationBar(
                            containerColor = PixelSurfaceCard,
                            contentColor = Color.White
                        ) {
                            NavigationBarItem(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ViewInAr,
                                        contentDescription = "3D rooms"
                                    )
                                },
                                label = { Text("3D rooms") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoolCyan,
                                    selectedTextColor = CoolCyan,
                                    indicatorColor = Color(0xFF1D3454),
                                    unselectedIconColor = Color(0xFF90A4AE),
                                    unselectedTextColor = Color(0xFF90A4AE)
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Insights,
                                        contentDescription = "Analytics"
                                    )
                                },
                                label = { Text("Analytics") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoolCyan,
                                    selectedTextColor = CoolCyan,
                                    indicatorColor = Color(0xFF1D3454),
                                    unselectedIconColor = Color(0xFF90A4AE),
                                    unselectedTextColor = Color(0xFF90A4AE)
                                )
                            )
                            NavigationBarItem(
                                selected = activeTab == 2,
                                onClick = { activeTab = 2 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoolCyan,
                                    selectedTextColor = CoolCyan,
                                    indicatorColor = Color(0xFF1D3454),
                                    unselectedIconColor = Color(0xFF90A4AE),
                                    unselectedTextColor = Color(0xFF90A4AE)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    when (activeTab) {
                        0 -> DioramaHomeScreen(
                            snapshot = snapshot,
                            settings = settings,
                            simParams = simParams,
                            selectedRoomIndex = selectedRoomIdx,
                            wholeHomeOverview = wholeHomeOverview,
                            onSelectRoomIndex = viewModel::selectRoomIndex,
                            onToggleWholeHome = viewModel::toggleWholeHomeOverview,
                            onUpdateRoomConfig = viewModel::updateRoomConfig,
                            onUpdateSimParams = viewModel::updateSimulationParams,
                            onRefresh = viewModel::refreshNow,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> AnalyticsScreen(
                            snapshot = snapshot,
                            settings = settings,
                            onExportCsv = viewModel::shareCsvExport,
                            modifier = Modifier.padding(innerPadding)
                        )
                        else -> SettingsScreen(
                            settings = settings,
                            snapshot = snapshot,
                            isScanningSubnet = isScanningSubnet,
                            statusBanner = statusBanner,
                            onUpdateSettings = viewModel::updateSettings,
                            onUpdateRoomConfig = viewModel::updateRoomConfig,
                            onScanSubnet = viewModel::discoverGatewayOnLan,
                            onTestConnection = viewModel::testLiveConnection,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
