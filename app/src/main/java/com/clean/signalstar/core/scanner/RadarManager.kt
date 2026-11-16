package com.clean.signalstar.core.scanner

import android.content.Context
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.filter.MovingAverageFilter
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.core.service.ServiceRunMode
import com.clean.signalstar.core.service.SignalRadarForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class RadarManager(private val context: Context) {

    private val filter = MovingAverageFilter()
    val bleScanner = BleRadarScanner(context, filter)
    val wifiScanner = WifiRadarScanner(context, filter)

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _deviceMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    // 单个设备扫描事件流，供自动化检测引擎高能效判定，避免遍历全量设备列表
    private val _deviceEventFlow = MutableSharedFlow<DiscoveredDevice>(extraBufferCapacity = 64)
    val deviceEventFlow: SharedFlow<DiscoveredDevice> = _deviceEventFlow.asSharedFlow()

    // 设备离线消失事件流（当蓝牙设备拿远断联超过10秒被清理时发出，供引擎自动将状态重置回 OUTSIDE）
    private val _deviceLostFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val deviceLostFlow: SharedFlow<String> = _deviceLostFlow.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var cleanupJob: Job? = null
    private var autoStopIdleJob: Job? = null
    private var isScanBleActive = false
    private var isScanWifiActive = false
    private var isLowLatencyMode = false

    init {
        // 收集 BLE 信号
        scope.launch {
            bleScanner.deviceFlow.collect { device ->
                onDeviceDetected(device)
            }
        }
        // 收集 Wi-Fi 信号
        scope.launch {
            wifiScanner.deviceFlow.collect { device ->
                onDeviceDetected(device)
            }
        }
    }

    private fun onDeviceDetected(device: DiscoveredDevice) {
        _deviceMap[device.id] = device
        updateList()
        _deviceEventFlow.tryEmit(device)
    }

    private fun updateList() {
        val sortedList = _deviceMap.values.toList().sortedByDescending { it.filteredRssi }
        _devices.value = sortedList
    }

    fun startScanning(scanBle: Boolean = true, scanWifi: Boolean = true, lowLatency: Boolean = false) {
        if (_isScanning.value) {
            updateScanTargets(
                scanBle = isScanBleActive || scanBle,
                scanWifi = isScanWifiActive || scanWifi,
                lowLatency = lowLatency
            )
            if (scanWifi) {
                wifiScanner.refreshScanResults()
            }
            return
        }
        _isScanning.value = true
        isScanBleActive = scanBle
        isScanWifiActive = scanWifi
        isLowLatencyMode = lowLatency

        LogRepository.log(
            "RadarManager",
            "Start scanning: lowLatency=$lowLatency, ble=$scanBle, wifi=$scanWifi${if (!lowLatency && scanWifi) " (wifi=passive-broadcast)" else ""}"
        )

        if (scanBle) {
            bleScanner.startScan(lowLatency)
        }
        if (scanWifi) {
            wifiScanner.startScan()
        }

        // 定期清理超时的设备（BLE 10秒离线重置，Wi-Fi 45秒超时）
        // 24h 低功耗后台模式下：完全停止定时轮询 Wi-Fi（仅靠 SCAN_RESULTS_AVAILABLE_ACTION 被动接收），彻底消除亮屏每分钟定位蓝点！
        cleanupJob = scope.launch {
            while (isActive) {
                val loopIntervalMs = if (isLowLatencyMode) 3_000L else 15_000L
                delay(loopIntervalMs)

                // 若系统蓝牙曾被用户临时关闭又重新打开，自动检测并恢复底层 BLE 扫描
                if (isScanBleActive && !bleScanner.isScanning()) {
                    bleScanner.startScan(lowLatency = isLowLatencyMode)
                }

                if (isScanWifiActive && isLowLatencyMode) {
                    wifiScanner.triggerScan()
                }

                if (_deviceMap.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    var changed = false
                    val iterator = _deviceMap.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        val timeoutMs = if (entry.value.type == SignalType.WIFI) 45_000L else 10_000L
                        if (now - entry.value.lastSeenTimestamp > timeoutMs) {
                            val lostId = entry.key
                            filter.clear(lostId)
                            iterator.remove()
                            _deviceLostFlow.tryEmit(lostId)
                            changed = true
                        }
                    }
                    if (changed) {
                        updateList()
                    }
                }
            }
        }
    }

    /**
     * 根据当前启用的规则类型，动态按需启闭底层硬件扫描（智能功耗裁剪）
     */
    fun updateScanTargets(scanBle: Boolean, scanWifi: Boolean, lowLatency: Boolean = false) {
        if (!_isScanning.value) return
        isScanBleActive = scanBle
        isScanWifiActive = scanWifi
        isLowLatencyMode = lowLatency

        LogRepository.log("RadarManager", "Update targets: lowLatency=$lowLatency, ble=$scanBle, wifi=$scanWifi")

        if (scanBle) {
            bleScanner.startScan(lowLatency)
        } else if (bleScanner.isScanning()) {
            bleScanner.stopScan()
        }

        if (scanWifi && !wifiScanner.isScanning()) {
            wifiScanner.startScan()
        } else if (!scanWifi && wifiScanner.isScanning()) {
            wifiScanner.stopScan()
        }
    }

    fun stopScanning() {
        autoStopIdleJob?.cancel()
        autoStopIdleJob = null
        if (!_isScanning.value) return
        _isScanning.value = false
        isScanBleActive = false
        isScanWifiActive = false
        bleScanner.stopScan()
        wifiScanner.stopScan()
        cleanupJob?.cancel()
        cleanupJob = null
    }

    /**
     * 当用户进入雷达界面时调用：立即取消离开页面后的自动关闭倒计时；
     * 仅在后台已启用 Wi-Fi 规则或用户停留在雷达「Wi-Fi」页签时才开启 Wi-Fi 扫描，其余情况绝不耗电扫 Wi-Fi
     */
    fun onRadarScreenEntered(scanWifiOnRadar: Boolean = false) {
        autoStopIdleJob?.cancel()
        autoStopIdleJob = null
        if (_isScanning.value && (isScanWifiActive || scanWifiOnRadar)) {
            if (!wifiScanner.isScanning()) {
                wifiScanner.startScan()
            } else {
                wifiScanner.triggerScan(force = true)
            }
        }
    }

    /**
     * 雷达页签切换（是否停留在 Wi-Fi 标签）：仅停留在 Wi-Fi 页签时临时扫描 Wi-Fi，切走立即停止（除非后台已有启用的 Wi-Fi 规则）
     */
    fun setRadarWifiTabActive(active: Boolean) {
        if (!_isScanning.value) return
        if (active) {
            if (!wifiScanner.isScanning()) {
                wifiScanner.startScan()
            } else {
                wifiScanner.triggerScan(force = true)
            }
        } else if (!isScanWifiActive && wifiScanner.isScanning()) {
            wifiScanner.stopScan()
        }
    }

    /**
     * 当用户切出雷达界面时调用：
     * - 若后台无 Wi-Fi 规则，立即停止临时 Wi-Fi 扫描；
     * - 若未开启后台守护（IDLE 态），离开满 30 秒后自动停止全部扫描以省电
     */
    fun onRadarScreenLeft(timeoutMs: Long = 30_000L) {
        autoStopIdleJob?.cancel()
        if (!_isScanning.value) return
        if (!isScanWifiActive && wifiScanner.isScanning()) {
            wifiScanner.stopScan()
        }
        autoStopIdleJob = scope.launch {
            delay(timeoutMs)
            // 如果此时后台守护服务(24h或敏捷模式)正在运行，绝不关停后台雷达扫描！
            if (SignalRadarForegroundService.currentMode.value == ServiceRunMode.IDLE) {
                stopScanning()
                clearDevices()
            }
        }
    }

    fun clearDevices() {
        _deviceMap.clear()
        filter.clearAll()
        _devices.value = emptyList()
    }
}
