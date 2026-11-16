package com.clean.signalstar.core.filter

import java.util.concurrent.ConcurrentHashMap

/**
 * 移动平均与快速贴近响应防抖滤波器
 * - 平时过滤 3~6dBm 的微小环境波动
 * - 当检测到信号突然增强 (>= 10dBm，即用户贴近蓝牙或走近门禁瞬间)，立即快速跟随最新强信号，消除贴近滞后！
 */
class MovingAverageFilter(
    private val windowSize: Int = 3,
    private val exponentialAlpha: Float = 0.65f
) {
    private val historyMap = ConcurrentHashMap<String, MutableList<Int>>()
    private val lastFilteredMap = ConcurrentHashMap<String, Float>()

    /**
     * 输入瞬时原始 RSSI，返回加权平滑后的 RSSI
     */
    fun filter(key: String, rawRssi: Int): Int {
        val list = historyMap.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            val prev = lastFilteredMap[key]

            // 快速贴近响应：若当前信号比历史滤波值突然增强 >= 10 dBm（例如拿近蓝牙设备），清空旧弱信号拖累，立即响应！
            if (prev != null && (rawRssi - prev) >= 10f) {
                list.clear()
                list.add(rawRssi)
                lastFilteredMap[key] = rawRssi.toFloat()
                return rawRssi
            }

            list.add(rawRssi)
            if (list.size > windowSize) {
                list.removeAt(0)
            }
            val windowAvg = list.average().toFloat()

            val base = prev ?: rawRssi.toFloat()
            val filtered = base + exponentialAlpha * (windowAvg - base)
            lastFilteredMap[key] = filtered

            return Math.round(filtered)
        }
    }

    /**
     * 清理某设备的滤波状态
     */
    fun clear(key: String) {
        historyMap.remove(key)
        lastFilteredMap.remove(key)
    }

    /**
     * 清理全部
     */
    fun clearAll() {
        historyMap.clear()
        lastFilteredMap.clear()
    }
}
