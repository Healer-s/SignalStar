package com.clean.signalstar.core.model

import java.util.UUID

enum class SignalType {
    BLE,
    WIFI
}

enum class MatchType {
    NAME_PREFIX,          // 设备名称前缀 (如 Gate_BLE, Beacon)
    MAC_EXACT,            // 物理 MAC / BSSID 地址精确匹配
    IBEACON,              // iBeacon UUID 匹配
    MANUFACTURER_DATA,    // 厂商自定义广播 Hex 匹配
    WIFI_SSID,            // Wi-Fi 名称匹配
    WIFI_BSSID            // Wi-Fi 路由器物理地址
}

data class BeaconInfo(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int
)

data class DiscoveredDevice(
    val id: String,                         // MAC 地址或 BSSID
    val name: String,                       // 设备名称或 SSID
    val type: SignalType,                   // BLE 或 WIFI
    val rawRssi: Int,                       // 瞬时 RSSI
    val filteredRssi: Int = rawRssi,        // 滤波后平滑 RSSI
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val beaconInfo: BeaconInfo? = null,     // iBeacon 详情
    val manufacturerHex: String? = null,    // 厂商数据 Hex 串
    val frequency: Int? = null              // Wi-Fi 频段 (MHz)
) {
    val displayRssi: String
        get() = "${filteredRssi} dBm"

    val signalQualityPercent: Int
        get() {
            // RSSI 范围通常在 -100 到 -40 之间
            return ((filteredRssi + 100).coerceIn(0, 60) * 100 / 60)
        }
}

data class SignalRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val signalType: SignalType,
    val matchType: MatchType,
    val matchPattern: String,
    val bindMac: Boolean = false,          // 是否强制绑定唯一物理 MAC / BSSID 地址（解决同名设备冲突）
    val macAddress: String? = null,        // 绑定的物理 MAC / BSSID 地址
    val enterRssiThreshold: Int = -70,     // 进入触发门限 (dBm)，例如 >= -70
    val exitRssiThreshold: Int = -85,      // 离开门限 (dBm)，例如 <= -85
    val cooldownSeconds: Int = 120,        // 冷却时间 (秒)
    val triggerTag: String,                // 三星日常程序识别标签，如 "小区门禁靠近"
    val triggerOnLeave: Boolean = false,   // 是否在离开信号范围时也发送 [Leave: 标签] 通知
    val leaveTag: String = "",             // 离开时发送的识别标签（留空则自动使用 "离开_${triggerTag}"）
    val enabled: Boolean = true,
    val lastTriggeredAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isCoolingDown(): Boolean {
        if (lastTriggeredAt == 0L) return false
        val elapsed = (System.currentTimeMillis() - lastTriggeredAt) / 1000
        return elapsed < cooldownSeconds
    }

    fun remainingCooldownSeconds(): Long {
        if (!isCoolingDown()) return 0
        val elapsed = (System.currentTimeMillis() - lastTriggeredAt) / 1000
        return (cooldownSeconds - elapsed).coerceAtLeast(0)
    }

    /**
     * 判断当前扫描到的设备是否命中本规则（优先校验物理 MAC/BSSID，再匹配名称/UUID/特征码）
     */
    fun matchesDevice(device: DiscoveredDevice): Boolean {
        if (signalType != device.type) return false
        val boundMac = macAddress?.replace(":", "")?.replace("-", "")?.trim().orEmpty()
        if (bindMac && boundMac.isNotEmpty()) {
            val cleanDeviceMac = device.id.replace(":", "").replace("-", "").trim()
            return cleanDeviceMac.equals(boundMac, ignoreCase = true)
        }

        val rawPattern = matchPattern.trim()
        if (rawPattern.isEmpty()) return false

        return when (matchType) {
            MatchType.NAME_PREFIX -> {
                val cleanDeviceMac = device.id.replace(":", "").trim()
                val cleanPatternMac = rawPattern.replace(":", "").trim()
                device.name.startsWith(rawPattern, ignoreCase = true) ||
                    (cleanPatternMac.length == 12 && cleanDeviceMac.equals(cleanPatternMac, ignoreCase = true))
            }
            MatchType.MAC_EXACT -> {
                val cleanDevice = device.id.replace(":", "").trim()
                val cleanPattern = rawPattern.replace(":", "").trim()
                cleanDevice.equals(cleanPattern, ignoreCase = true)
            }
            MatchType.IBEACON -> {
                val beacon = device.beaconInfo
                beacon != null && beacon.uuid.equals(rawPattern, ignoreCase = true)
            }
            MatchType.MANUFACTURER_DATA -> {
                val hex = device.manufacturerHex
                hex != null && hex.contains(rawPattern, ignoreCase = true)
            }
            MatchType.WIFI_SSID -> {
                val cleanDeviceMac = device.id.replace(":", "").trim()
                val cleanPatternMac = rawPattern.replace(":", "").trim()
                device.name.equals(rawPattern, ignoreCase = true) ||
                    (cleanPatternMac.length == 12 && cleanDeviceMac.equals(cleanPatternMac, ignoreCase = true))
            }
            MatchType.WIFI_BSSID -> {
                val cleanDevice = device.id.replace(":", "").trim()
                val cleanPattern = rawPattern.replace(":", "").trim()
                cleanDevice.equals(cleanPattern, ignoreCase = true)
            }
        }
    }
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isTrigger: Boolean = false
)
