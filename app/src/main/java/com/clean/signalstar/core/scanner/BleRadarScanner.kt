package com.clean.signalstar.core.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.filter.MovingAverageFilter
import com.clean.signalstar.core.model.BeaconInfo
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.SignalType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BleRadarScanner(
    private val context: Context,
    private val filter: MovingAverageFilter = MovingAverageFilter()
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val _deviceFlow = MutableSharedFlow<DiscoveredDevice>(replay = 0, extraBufferCapacity = 128)
    val deviceFlow: SharedFlow<DiscoveredDevice> = _deviceFlow.asSharedFlow()

    // 缓存已解析出真实名称的蓝牙设备 (MAC -> 真实设备名)，解决主广播包(ADV_IND)不带名称导致名称在真实名与 BLE-XXXX 之间跳变漏判的 Bug！
    private val knownDeviceNames = ConcurrentHashMap<String, String>()

    @Volatile
    private var isScanning = false
    private var currentLowLatency = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            if (errorCode != ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                isScanning = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(lowLatency: Boolean = false): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            isScanning = false
            return false
        }

        if (isScanning) {
            // 若当前已经在扫描且模式一致，直接返回；若从后台平衡模式切换到前台低延迟模式(或反之)，无缝重启扫描切换灵敏度
            if (currentLowLatency == lowLatency) {
                return true
            } else {
                stopScan()
            }
        }

        bleScanner = bluetoothAdapter.bluetoothLeScanner
        if (bleScanner == null) {
            Log.w(TAG, "BluetoothLeScanner is not available")
            isScanning = false
            return false
        }

        val scanMode = if (lowLatency) {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        } else {
            ScanSettings.SCAN_MODE_LOW_POWER
        }

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(scanMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsBuilder.setLegacy(false)
        }

        try {
            bleScanner?.startScan(null, settingsBuilder.build(), scanCallback)
            isScanning = true
            currentLowLatency = lowLatency
            val modeName = if (lowLatency) "LOW_LATENCY" else "LOW_POWER"
            Log.d(TAG, "BLE Scan started, mode=$modeName")
            LogRepository.log("BleRadar", "BLE scan started (mode=$modeName)")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan: ${e.message}")
            isScanning = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan: ${e.message}")
            isScanning = false
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        try {
            bleScanner?.stopScan(scanCallback)
            Log.d(TAG, "BLE Scan stopped")
            LogRepository.log("BleRadar", "BLE scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    fun isScanning(): Boolean = isScanning && (bluetoothAdapter?.isEnabled == true)

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val rawRssi = result.rssi
        val record = result.scanRecord

        val filteredRssi = filter.filter(address, rawRssi)

        // 解析名称：优先从广播 Record 获取，其次从 BluetoothDevice 获取，最后回退到该 MAC 历史已缓存的真实名称
        var resolvedName = record?.deviceName?.trim()
        if (resolvedName.isNullOrBlank()) {
            try {
                resolvedName = device.name?.trim()
            } catch (_: SecurityException) {
            }
        }
        if (!resolvedName.isNullOrBlank()) {
            knownDeviceNames[address] = resolvedName
        } else {
            resolvedName = knownDeviceNames[address]
        }

        val finalDeviceName = if (resolvedName.isNullOrBlank()) {
            "BLE-${address.takeLast(5).replace(":", "")}"
        } else {
            resolvedName
        }

        // 解析 Manufacturer Data 与 iBeacon
        var beaconInfo: BeaconInfo? = null
        var manufacturerHex: String? = null

        val manufacturerData = record?.manufacturerSpecificData
        if (manufacturerData != null && manufacturerData.size() > 0) {
            val sb = StringBuilder()
            for (i in 0 until manufacturerData.size()) {
                val manufacturerId = manufacturerData.keyAt(i)
                val data = manufacturerData.valueAt(i)

                // 检查是否为 Apple iBeacon: ID 0x004C (76)
                if (manufacturerId == 0x004C && data != null && data.size >= 23 && data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
                    beaconInfo = parseIBeacon(data)
                }

                if (data != null && data.isNotEmpty()) {
                    sb.append(String.format("%04X:", manufacturerId))
                    sb.append(bytesToHex(data))
                    sb.append(";")
                }
            }
            if (sb.isNotEmpty()) {
                manufacturerHex = sb.toString()
            }
        }

        val discovered = DiscoveredDevice(
            id = address,
            name = finalDeviceName,
            type = SignalType.BLE,
            rawRssi = rawRssi,
            filteredRssi = filteredRssi,
            lastSeenTimestamp = System.currentTimeMillis(),
            beaconInfo = beaconInfo,
            manufacturerHex = manufacturerHex
        )

        _deviceFlow.tryEmit(discovered)
    }

    private fun parseIBeacon(data: ByteArray): BeaconInfo? {
        return try {
            val buffer = ByteBuffer.wrap(data)
            buffer.position(2)
            val mostSigBits = buffer.long
            val leastSigBits = buffer.long
            val uuid = UUID(mostSigBits, leastSigBits).toString().uppercase()

            val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
            val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
            val txPower = data[22].toInt()

            BeaconInfo(uuid = uuid, major = major, minor = minor, txPower = txPower)
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    companion object {
        private const val TAG = "BleRadarScanner"
    }
}
