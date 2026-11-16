package com.clean.signalstar.core.engine

import android.content.Context
import com.clean.signalstar.core.bridge.OneUiNotificationBridge
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.data.RuleRepository
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.MatchType
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

enum class PresenceState {
    OUTSIDE,  // 处于感应区外
    INSIDE    // 已处于感应区内 (持续逗留)
}

/**
 * 带有“6dBm 黄金施密特滞后区间”、“按物理设备独立状态机”、“2包弱信号确认离开”与“10秒离线自动归位”的自动化判定引擎
 * 彻底解决“拿开蓝牙再贴近蓝牙不触发”的冷却锁死与门限过宽问题！
 */
class DetectionEngine(
    private val context: Context,
    private val ruleRepository: RuleRepository,
    private val notificationBridge: OneUiNotificationBridge,
    private val onTriggerSuccess: ((rule: SignalRule) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()

    // 按 (ruleId + "_" + deviceMac) 独立维护每个物理设备的在场状态
    private val rulePresenceMap = ConcurrentHashMap<String, PresenceState>()

    // 记录每个 (ruleId + "_" + deviceMac) 连续收到低于离开门限弱信号包的次数 (满 2 次即确认拿开/离开)
    private val weakPacketCountMap = ConcurrentHashMap<String, Int>()

    // 记录每个 (ruleId + "_" + deviceMac) 最后一次被扫描到的时间戳 (毫秒)
    private val lastSeenTimestampMap = ConcurrentHashMap<String, Long>()

    // 内存中记录每条规则最近一次触发的时间戳 (毫秒)，防止极短时间内并发双发
    private val lastTriggerTimestampMap = ConcurrentHashMap<String, Long>()

    // 内存热缓存规则，实时监听 DataStore 变更
    @Volatile
    private var cachedRules: List<SignalRule> = emptyList()

    init {
        scope.launch {
            ruleRepository.rulesFlow.collect { rules ->
                val oldMap = cachedRules.associateBy { it.id }
                for (newRule in rules) {
                    val oldRule = oldMap[newRule.id]
                    if (oldRule == null ||
                        (!oldRule.enabled && newRule.enabled) ||
                        (oldRule.lastTriggeredAt != 0L && newRule.lastTriggeredAt == 0L) ||
                        oldRule.matchPattern != newRule.matchPattern ||
                        oldRule.bindMac != newRule.bindMac ||
                        oldRule.macAddress != newRule.macAddress ||
                        oldRule.enterRssiThreshold != newRule.enterRssiThreshold ||
                        oldRule.triggerTag != newRule.triggerTag ||
                        oldRule.triggerOnLeave != newRule.triggerOnLeave ||
                        oldRule.leaveTag != newRule.leaveTag
                    ) {
                        resetRuleState(newRule.id)
                    }
                }
                cachedRules = rules
            }
        }
    }

    /**
     * 当某个设备因超时离线被雷达移除时，自动将其对应的所有规则状态重置为 OUTSIDE（已离开）
     */
    fun onDeviceLost(deviceId: String) {
        val suffix = "_${deviceId.uppercase()}"
        val rulesById = cachedRules.associateBy { it.id }
        for (key in rulePresenceMap.keys) {
            if (key.endsWith(suffix)) {
                if (rulePresenceMap[key] == PresenceState.INSIDE) {
                    rulePresenceMap[key] = PresenceState.OUTSIDE
                    weakPacketCountMap[key] = 0
                    LogRepository.log("Engine", activeAppStrings.logEngineOfflineFmt(deviceId))
                    val ruleId = key.removeSuffix(suffix)
                    val rule = rulesById[ruleId]
                    if (rule != null && rule.enabled && rule.triggerOnLeave) {
                        triggerLeaveRule(rule, deviceId, null, 15)
                    }
                }
            }
        }
    }

    /**
     * 判定传入的设备是否击中任何启用的规则
     */
    fun evaluate(device: DiscoveredDevice, autoDismissSeconds: Int = 15) {
        val rules = cachedRules
        val activeRules = rules.filter { it.enabled && it.signalType == device.type }
        if (activeRules.isEmpty()) return

        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()

                for (rule in activeRules) {
                    if (isMatch(rule, device)) {
                        val stateKey = "${rule.id}_${device.id.uppercase()}"
                        val prevSeen = lastSeenTimestampMap[stateKey] ?: 0L
                        lastSeenTimestampMap[stateKey] = now

                        var currentState = rulePresenceMap.getOrDefault(stateKey, PresenceState.OUTSIDE)

                        // 若此前处于 INSIDE，但蓝牙已消失超过 10 秒（或 Wi-Fi 消失超过 45 秒），说明期间拿开/断电过，自动归位为 OUTSIDE
                        val lostTimeoutMs = if (device.type == SignalType.BLE) BLE_LOST_RESET_MS else WIFI_LOST_RESET_MS
                        if (currentState == PresenceState.INSIDE && prevSeen > 0L && (now - prevSeen) > lostTimeoutMs) {
                            currentState = PresenceState.OUTSIDE
                            rulePresenceMap[stateKey] = PresenceState.OUTSIDE
                            weakPacketCountMap[stateKey] = 0
                            LogRepository.log(
                                "Engine",
                                activeAppStrings.logEngineReappearedFmt(rule.name, (now - prevSeen) / 1000)
                            )
                        }

                        val rssi = device.filteredRssi
                        // 采用 6 dBm 黄金施密特滞后门限（距离翻倍信号衰减约 6dBm）：确保拿开蓝牙几米远即可顺利重置为 OUTSIDE！
                        val effectiveExitThreshold = (rule.enterRssiThreshold - 6).coerceAtLeast(rule.exitRssiThreshold)

                        // 1. 判定“进入/贴近”：信号达到或超过进入门限
                        if (rssi >= rule.enterRssiThreshold) {
                            // 只要收到达标强信号，立即清零弱包计数
                            weakPacketCountMap[stateKey] = 0

                            if (currentState == PresenceState.OUTSIDE) {
                                // 既然状态已经是 OUTSIDE（确认此前已经拿开/离开过），只需 5 秒极短防并发硬防抖即可立即触发，绝不用 120s 冷却卡死测试！
                                val lastTrigger = lastTriggerTimestampMap[rule.id] ?: 0L
                                val inHardDebounce = (now - lastTrigger) < HARD_DEBOUNCE_MS

                                if (inHardDebounce) {
                                    val remain = ((HARD_DEBOUNCE_MS - (now - lastTrigger)) / 1000L).coerceAtLeast(1)
                                    LogRepository.log(
                                        "Engine",
                                        activeAppStrings.logEngineDebounceFmt(rule.name, remain)
                                    )
                                } else {
                                    // 真正触发并锁定为 INSIDE（只要不拿开，一直呆在旁边绝不重复触发）
                                    rulePresenceMap[stateKey] = PresenceState.INSIDE
                                    lastTriggerTimestampMap[rule.id] = now
                                    LogRepository.log(
                                        "Engine",
                                        activeAppStrings.logEngineEnterZoneFmt(rule.name, device.id, rssi, rule.enterRssiThreshold)
                                    )
                                    triggerRule(rule, device, rssi, autoDismissSeconds)
                                }
                            } else {
                                // currentState == PresenceState.INSIDE：
                                // 设备一直放在旁边未拿开，保持静默不重复触发
                            }
                        }
                        // 2. 判定“拿开/离开”：信号衰减至低于有效离开门限 (enterThreshold - 6 dBm)，连续 2 包确认即可重置为 OUTSIDE
                        else if (rssi <= effectiveExitThreshold) {
                            if (currentState == PresenceState.INSIDE) {
                                val weakCount = (weakPacketCountMap[stateKey] ?: 0) + 1
                                weakPacketCountMap[stateKey] = weakCount
                                if (weakCount >= EXIT_CONFIRM_PACKETS) {
                                    rulePresenceMap[stateKey] = PresenceState.OUTSIDE
                                    weakPacketCountMap[stateKey] = 0
                                    LogRepository.log(
                                        "Engine",
                                        activeAppStrings.logEngineLeaveZoneFmt(rule.name, rssi, effectiveExitThreshold)
                                    )
                                    if (rule.triggerOnLeave) {
                                        triggerLeaveRule(rule, device.id, rssi, autoDismissSeconds)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 重置指定规则的所有在场状态与内存冷却锁
     */
    fun resetRuleState(ruleId: String) {
        val prefix = "${ruleId}_"
        rulePresenceMap.keys.removeIf { it.startsWith(prefix) || it == ruleId }
        weakPacketCountMap.keys.removeIf { it.startsWith(prefix) || it == ruleId }
        lastSeenTimestampMap.keys.removeIf { it.startsWith(prefix) || it == ruleId }
        lastTriggerTimestampMap.remove(ruleId)
    }

    private fun isMatch(rule: SignalRule, device: DiscoveredDevice): Boolean {
        // 1. 如果开启了「限定物理 MAC / BSSID 地址」开关：直接以唯一物理 MAC 地址为准
        val boundMac = rule.macAddress?.replace(":", "")?.replace("-", "")?.trim().orEmpty()
        if (rule.bindMac && boundMac.isNotEmpty()) {
            val cleanDeviceMac = device.id.replace(":", "").replace("-", "").trim()
            return cleanDeviceMac.equals(boundMac, ignoreCase = true)
        }

        // 2. 常规特征匹配（兼容用户直接在匹配框输入 MAC 地址的情况）
        val rawPattern = rule.matchPattern.trim()
        if (rawPattern.isEmpty()) {
            return rule.bindMac && boundMac.isNotEmpty()
        }

        return when (rule.matchType) {
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
                device.type == SignalType.WIFI && (
                    device.name.equals(rawPattern, ignoreCase = true) ||
                        (cleanPatternMac.length == 12 && cleanDeviceMac.equals(cleanPatternMac, ignoreCase = true))
                    )
            }
            MatchType.WIFI_BSSID -> {
                val cleanDevice = device.id.replace(":", "").trim()
                val cleanPattern = rawPattern.replace(":", "").trim()
                device.type == SignalType.WIFI && cleanDevice.equals(cleanPattern, ignoreCase = true)
            }
        }
    }

    private suspend fun triggerRule(rule: SignalRule, device: DiscoveredDevice, rssi: Int, autoDismissSeconds: Int) {
        LogRepository.log(
            "Engine",
            activeAppStrings.logEngineTriggeredFmt(rule.name, rssi, device.id, rule.triggerTag),
            isTrigger = true
        )

        // 1. 发送特征通知到系统自动化
        notificationBridge.sendTriggerNotification(
            rule = rule,
            detectedRssi = rssi,
            autoDismissSeconds = autoDismissSeconds
        )

        // 2. 更新规则的最后触发时间
        ruleRepository.recordRuleTriggered(rule.id)

        // 3. 回调通知上层
        onTriggerSuccess?.invoke(rule)
    }

    private fun triggerLeaveRule(rule: SignalRule, deviceId: String, rssi: Int?, autoDismissSeconds: Int) {
        val effectiveLeaveTag = rule.leaveTag.trim().ifEmpty { activeAppStrings.leaveTagDefaultFmt(rule.triggerTag) }
        LogRepository.log(
            "Engine",
            activeAppStrings.logEngineLeaveTriggeredFmt(rule.name, deviceId, effectiveLeaveTag),
            isTrigger = true
        )
        notificationBridge.sendLeaveNotification(
            rule = rule,
            detectedRssi = rssi,
            autoDismissSeconds = autoDismissSeconds
        )
    }

    companion object {
        // 连续 2 包低于离开门限 (enterThreshold - 6dBm) 即确认拿开/离开
        private const val EXIT_CONFIRM_PACKETS = 2

        // 5 秒极短并发防抖（只要确认拿开重置为 OUTSIDE 后再贴近，超过 5 秒即可立即再次触发！）
        private const val HARD_DEBOUNCE_MS = 5_000L

        // 蓝牙信号消失超过 10 秒后重现，自动归位为 OUTSIDE
        private const val BLE_LOST_RESET_MS = 10_000L

        // Wi-Fi 信号消失超过 45 秒后重现，自动归位为 OUTSIDE
        private const val WIFI_LOST_RESET_MS = 45_000L
    }
}
