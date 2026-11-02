package com.dariopittera.ecowittpixel.watch.complication

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dariopittera.ecowittpixel.data.LocalHistoryStore
import com.dariopittera.ecowittpixel.watch.ui.EcowittWatchTheme
import com.dariopittera.ecowittpixel.watch.ui.WatchComfortEmerald
import com.dariopittera.ecowittpixel.watch.ui.WatchCoolCyan
import com.dariopittera.ecowittpixel.watch.ui.WatchOledBlack
import com.dariopittera.ecowittpixel.watch.ui.WatchSurfaceCard
import com.dariopittera.ecowittpixel.watch.ui.createNormalizedWatchContext

enum class ComplicationMetricOption(val id: String, val label: String) {
    TEMP_AND_HUMIDITY("temp_rh", "Temperature and RH"),
    HUMIDITY_AND_DEW("rh_dew", "Humidity and dew point"),
    COMFORT_SCORE("comfort", "Comfort score (0–100)"),
    PRESSURE("pressure", "Barometric pressure");

    companion object {
        fun fromId(id: String): ComplicationMetricOption =
            entries.firstOrNull { it.id == id } ?: TEMP_AND_HUMIDITY
    }
}

data class ComplicationSlotConfig(
    val channelId: String = "gateway",
    val metric: ComplicationMetricOption = ComplicationMetricOption.TEMP_AND_HUMIDITY,
    val alertOverrideEnabled: Boolean = true
)

object ComplicationConfigStore {
    private const val PREFS_NAME = "ecowitt_complication_prefs"

    fun loadSlotConfig(context: Context, complicationId: Int): ComplicationSlotConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ch = prefs.getString("slot_${complicationId}_ch", null)
            ?: prefs.getString("default_ch", "gateway")
            ?: "gateway"
        val metricId = prefs.getString("slot_${complicationId}_metric", null)
            ?: prefs.getString("default_metric", ComplicationMetricOption.TEMP_AND_HUMIDITY.id)
            ?: ComplicationMetricOption.TEMP_AND_HUMIDITY.id
        val alertOverride = if (prefs.contains("slot_${complicationId}_alert")) {
            prefs.getBoolean("slot_${complicationId}_alert", true)
        } else {
            prefs.getBoolean("default_alert", true)
        }
        return ComplicationSlotConfig(
            channelId = ch,
            metric = ComplicationMetricOption.fromId(metricId),
            alertOverrideEnabled = alertOverride
        )
    }

    fun saveSlotConfig(context: Context, complicationId: Int, cfg: ComplicationSlotConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("slot_${complicationId}_ch", cfg.channelId)
            .putString("slot_${complicationId}_metric", cfg.metric.id)
            .putBoolean("slot_${complicationId}_alert", cfg.alertOverrideEnabled)
            .putString("default_ch", cfg.channelId)
            .putString("default_metric", cfg.metric.id)
            .putBoolean("default_alert", cfg.alertOverrideEnabled)
            .apply()
    }
}

class ComplicationConfigActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(createNormalizedWatchContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val complicationId = intent.getIntExtra(
            ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID,
            -1
        )
        val store = LocalHistoryStore(applicationContext)
        val settings = store.loadSettings()
        val initialCfg = ComplicationConfigStore.loadSlotConfig(applicationContext, complicationId)

        val roomChoices = listOf(
            "gateway" to (settings.roomConfigs["gateway"]?.customName ?: "Living room (GW1200)"),
            "ch1" to (settings.roomConfigs["ch1"]?.customName ?: "Master bedroom (Ch 1)"),
            "ch2" to (settings.roomConfigs["ch2"]?.customName ?: "Balcony garden (Ch 2)")
        )

        setContent {
            EcowittWatchTheme {
                var selectedChannel by remember { mutableStateOf(initialCfg.channelId) }
                var selectedMetric by remember { mutableStateOf(initialCfg.metric) }
                var alertOverride by remember { mutableStateOf(initialCfg.alertOverrideEnabled) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WatchOledBlack)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Complication slot",
                        color = WatchCoolCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Select room",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    roomChoices.forEach { (chId, name) ->
                        val isSel = selectedChannel == chId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF173654) else WatchSurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) WatchCoolCyan else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedChannel = chId }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select metric",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ComplicationMetricOption.entries.forEach { metric ->
                        val isSel = selectedMetric == metric
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF173654) else WatchSurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) WatchCoolCyan else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedMetric = metric }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = metric.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WatchSurfaceCard)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alert override",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Highlight any room breaching a threshold",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                        Switch(
                            checked = alertOverride,
                            onCheckedChange = { alertOverride = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WatchComfortEmerald)
                            .clickable {
                                ComplicationConfigStore.saveSlotConfig(
                                    applicationContext,
                                    complicationId,
                                    ComplicationSlotConfig(
                                        channelId = selectedChannel,
                                        metric = selectedMetric,
                                        alertOverrideEnabled = alertOverride
                                    )
                                )
                                runCatching {
                                    val requester = ComplicationDataSourceUpdateRequester.create(
                                        applicationContext,
                                        ComponentName(applicationContext, EcowittComplicationService::class.java)
                                    )
                                    if (complicationId >= 0) {
                                        requester.requestUpdate(complicationId)
                                    } else {
                                        requester.requestUpdateAll()
                                    }
                                }
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save complication",
                            color = Color(0xFF04221F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
