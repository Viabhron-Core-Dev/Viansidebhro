package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import com.example.util.AppLogger
import kotlinx.coroutines.*
import kotlin.math.max

class NetSpeedManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onSpeedUpdate: (down: Long, up: Long) -> Unit,
    private val onDailyDataUpdate: (mobileMb: Long, wifiMb: Long) -> Unit
) {
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastTotalRx = TrafficStats.getTotalRxBytes()
    private var lastTotalTx = TrafficStats.getTotalTxBytes()
    private var lastMobileRx = TrafficStats.getMobileRxBytes()
    private var lastMobileTx = TrafficStats.getMobileTxBytes()
    private var lastTime = System.currentTimeMillis()
    
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLogger.d("NetSpeedManager", "Speed polling started")
        
        lastTotalRx = TrafficStats.getTotalRxBytes()
        lastTotalTx = TrafficStats.getTotalTxBytes()
        lastMobileRx = TrafficStats.getMobileRxBytes()
        lastMobileTx = TrafficStats.getMobileTxBytes()
        lastTime = System.currentTimeMillis()
        
        val baselineMobileRx = prefs.getLong("daily_mobile_rx_baseline", 0L)
        val baselineMobileTx = prefs.getLong("daily_mobile_tx_baseline", 0L)
        val baselineWifiRx = prefs.getLong("daily_wifi_rx_baseline", 0L)
        val baselineWifiTx = prefs.getLong("daily_wifi_tx_baseline", 0L)
        
        if (baselineMobileRx == 0L && baselineWifiRx == 0L) {
            val initialMobileRx = TrafficStats.getMobileRxBytes()
            val initialMobileTx = TrafficStats.getMobileTxBytes()
            val initialWifiRx = TrafficStats.getTotalRxBytes() - initialMobileRx
            val initialWifiTx = TrafficStats.getTotalTxBytes() - initialMobileTx
            
            prefs.edit()
                .putLong("daily_mobile_rx_baseline", initialMobileRx)
                .putLong("daily_mobile_tx_baseline", initialMobileTx)
                .putLong("daily_wifi_rx_baseline", initialWifiRx)
                .putLong("daily_wifi_tx_baseline", initialWifiTx)
                .apply()
        }
        
        job = coroutineScope.launch {
            while (isActive && isRunning) {
                delay(1000)
                updateSpeed()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job?.cancel()
        job = null
        AppLogger.d("NetSpeedManager", "Speed polling stopped")
    }
    
    private suspend fun updateSpeed() {
        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()
        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTime = System.currentTimeMillis()
        
        if (currentTotalRx == TrafficStats.UNSUPPORTED.toLong()) {
            return
        }

        val timeDiff = currentTime - lastTime
        if (timeDiff <= 0) return

        val totalRxDiff = max(0L, currentTotalRx - lastTotalRx)
        val totalTxDiff = max(0L, currentTotalTx - lastTotalTx)
        val mobileRxDiff = max(0L, currentMobileRx - lastMobileRx)
        val mobileTxDiff = max(0L, currentMobileTx - lastMobileTx)
        
        val wifiRxDiff = max(0L, totalRxDiff - mobileRxDiff)
        val wifiTxDiff = max(0L, totalTxDiff - mobileTxDiff)

        val rxSec = (totalRxDiff * 1000) / timeDiff
        val txSec = (totalTxDiff * 1000) / timeDiff
        
        withContext(Dispatchers.Main) {
            onSpeedUpdate(rxSec, txSec)
        }
        
        var dailyMobileRx = prefs.getLong("daily_mobile_rx", 0) + mobileRxDiff
        var dailyMobileTx = prefs.getLong("daily_mobile_tx", 0) + mobileTxDiff
        var dailyWifiRx = prefs.getLong("daily_wifi_rx", 0) + wifiRxDiff
        var dailyWifiTx = prefs.getLong("daily_wifi_tx", 0) + wifiTxDiff
        
        prefs.edit()
            .putLong("daily_mobile_rx", dailyMobileRx)
            .putLong("daily_mobile_tx", dailyMobileTx)
            .putLong("daily_wifi_rx", dailyWifiRx)
            .putLong("daily_wifi_tx", dailyWifiTx)
            .apply()
            
        val mobileMb = (dailyMobileRx + dailyMobileTx) / (1024 * 1024)
        val wifiMb = (dailyWifiRx + dailyWifiTx) / (1024 * 1024)

        withContext(Dispatchers.Main) {
            onDailyDataUpdate(mobileMb, wifiMb)
        }

        lastTotalRx = currentTotalRx
        lastTotalTx = currentTotalTx
        lastMobileRx = currentMobileRx
        lastMobileTx = currentMobileTx
        lastTime = currentTime
    }
}
