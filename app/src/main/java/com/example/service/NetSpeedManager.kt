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
        
        job = coroutineScope.launch {
            syncSystemDataUsage()
            while (isActive && isRunning) {
                delay(1000)
                updateSpeed()
            }
        }
    }

    private suspend fun syncSystemDataUsage() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        val hasPermission = if (mode == android.app.AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }

        if (!hasPermission) return
        
        try {
            val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as android.app.usage.NetworkStatsManager
            val cal = java.util.Calendar.getInstance()
            val end = cal.timeInMillis
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.clear(java.util.Calendar.MINUTE)
            cal.clear(java.util.Calendar.SECOND)
            cal.clear(java.util.Calendar.MILLISECOND)
            val start = cal.timeInMillis
            
            var mobileBytes = 0L
            var wifiBytes = 0L
            
            try {
                val bucket = manager.querySummaryForDevice(android.net.NetworkCapabilities.TRANSPORT_WIFI, null, start, end)
                wifiBytes = bucket.rxBytes + bucket.txBytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                val bucket = manager.querySummaryForDevice(android.net.NetworkCapabilities.TRANSPORT_CELLULAR, null, start, end)
                mobileBytes = bucket.rxBytes + bucket.txBytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            prefs.edit()
                .putLong("daily_mobile_rx", mobileBytes)
                .putLong("daily_mobile_tx", 0L)
                .putLong("daily_wifi_rx", wifiBytes)
                .putLong("daily_wifi_tx", 0L)
                .apply()
                
            withContext(Dispatchers.Main) {
                onDailyDataUpdate(mobileBytes / (1024 * 1024), wifiBytes / (1024 * 1024))
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
