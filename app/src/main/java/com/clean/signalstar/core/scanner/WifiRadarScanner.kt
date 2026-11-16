package com.clean.signalstar.core.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.filter.MovingAverageFilter
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WifiRadarScanner(
    private val context: Context,
    private val filter: MovingAverageFilter = MovingAverageFilter()
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _deviceFlow = MutableSharedFlow<DiscoveredDevice>(replay = 0, extraBufferCapacity = 128)
    val deviceFlow: SharedFlow<DiscoveredDevice> = _deviceFlow.asSharedFlow()

    private var isScanning = false
    private var lastHardwareScanTriggerTime = 0L
    private var lastBroadcastElapsed = 0L
    private val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val deltaSec = if (lastBroadcastElapsed > 0L) (nowElapsed - lastBroadcastElapsed) / 1000L else 0L
            lastBroadcastElapsed = nowElapsed
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            val screenOn = powerManager?.isInteractive == true
            LogRepository.log("WifiRadar", "System Wi-Fi scan broadcast (updated=$updated, screenOn=$screenOn, delta=${deltaSec}s)")
            // 无论 EXTRA_RESULTS_UPDATED 为 true 还是被系统节流返回 false，都读取系统底层最新扫描缓存与当前连接实时信号
            handleScanResults()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        if (isScanning) {
            refreshScanResults()
            return true
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled) {
            Log.w(TAG, "Wi-Fi is not enabled")
            return false
        }

        try {
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(wifiScanReceiver, intentFilter)
            }

            isScanning = true
            // 立即触发扫描并同步读取系统已有扫描结果与当前连接热点
            triggerScan(force = true)
            handleScanResults()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Wi-Fi scan: ${e.message}")
            isScanning = false
            return false
        }
    }

    fun stopScan() {
        if (!isScanning) return
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver not registered or error: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    fun isScanning(): Boolean = isScanning

    /**
     * 触发硬件扫描：
     * Android 9+ 限制前台应用每 2 分钟最多调用 4 次 startScan()（即平均每 30 秒 1 次）。
     * 若之前每 3 秒无脑调用一次，会在前 12 秒内耗尽系统配额，导致后续被系统节流锁死！
     * 因此此处默认控制硬件触发间隔 >= 15 秒，同时每次调用都会刷新读取系统最新扫描缓存与连接态实时 RSSI。
     */
    @SuppressLint("MissingPermission")
    fun triggerScan(force: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        var triggered = false
        if (force || (now - lastHardwareScanTriggerTime >= 15_000L)) {
            lastHardwareScanTriggerTime = now
            triggered = try {
                @Suppress("DEPRECATION")
                wifiManager?.startScan() ?: false
            } catch (e: Exception) {
                false
            }
        }
        // 同步刷新一次扫描结果与实时连接信号，确保 15s 清理周期内设备时间戳不断续！
        handleScanResults()
        return triggered
    }

    /**
     * 立即从系统拉取最新 Wi-Fi 列表与当前已连接 Wi-Fi 的实时信号强度
     */
    fun refreshScanResults() {
        handleScanResults()
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResults() {
        try {
            val nowMs = System.currentTimeMillis()
            val bootElapsedUs = SystemClock.elapsedRealtime() * 1000L
            val emittedBssids = HashSet<String>()

            // 1. 优先读取当前已连接 Wi-Fi 的实时信号 (不受系统 30s 扫描节流限制，毫秒级实时更新)
            @Suppress("DEPRECATION")
            val connInfo = wifiManager?.connectionInfo
            val connBssid = connInfo?.bssid
            if (!connBssid.isNullOrBlank() &&
                connBssid != "02:00:00:00:00:00" &&
                connBssid != "00:00:00:00:00:00" &&
                connInfo.rssi in -120..-1
            ) {
                val rawSsid = connInfo.ssid?.removePrefix("\"")?.removeSuffix("\"").orEmpty()
                val cleanSsid = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") activeAppStrings.wifiCurrentHotspot else rawSsid
                val filteredRssi = filter.filter(connBssid, connInfo.rssi)
                val connDevice = DiscoveredDevice(
                    id = connBssid,
                    name = cleanSsid,
                    type = SignalType.WIFI,
                    rawRssi = connInfo.rssi,
                    filteredRssi = filteredRssi,
                    lastSeenTimestamp = nowMs,
                    frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) connInfo.frequency else null
                )
                emittedBssids.add(connBssid.uppercase())
                _deviceFlow.tryEmit(connDevice)
            }

            // 2. 读取周边扫描到的所有 Wi-Fi 热点列表
            val results: List<ScanResult>? = wifiManager?.scanResults
            results?.forEach { scanResult ->
                val bssid = scanResult.BSSID ?: return@forEach
                // 过滤掉硬件层面超过 90 秒未曾刷新的陈旧幽灵缓存（防止已断电路由器残留）
                val ageUs = bootElapsedUs - scanResult.timestamp
                if (scanResult.timestamp > 0L && ageUs > 90_000_000L && !emittedBssids.contains(bssid.uppercase())) {
                    return@forEach
                }

                val ssid = if (scanResult.SSID.isNullOrBlank()) activeAppStrings.wifiHiddenSsid else scanResult.SSID
                // 若是当前已连接的热点，以 connectionInfo 的实时信号为准，但补充正确的 SSID 名称
                val isCurrentConn = emittedBssids.contains(bssid.uppercase())
                val rawRssi = if (isCurrentConn && connInfo != null && connInfo.rssi in -120..-1) {
                    connInfo.rssi
                } else {
                    scanResult.level
                }
                val filteredRssi = filter.filter(bssid, rawRssi)

                val device = DiscoveredDevice(
                    id = bssid,
                    name = ssid,
                    type = SignalType.WIFI,
                    rawRssi = rawRssi,
                    filteredRssi = filteredRssi,
                    lastSeenTimestamp = nowMs,
                    frequency = scanResult.frequency
                )
                _deviceFlow.tryEmit(device)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for scanResults: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling scan results: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WifiRadarScanner"
    }
}
