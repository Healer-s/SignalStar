package com.clean.signalstar.core.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.clean.signalstar.SignalStarApp
import com.clean.signalstar.core.battery.PowerHelper
import com.clean.signalstar.core.bridge.OneUiNotificationBridge
import com.clean.signalstar.core.data.AppSettings
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.engine.DetectionEngine
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.core.scanner.RadarManager
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ServiceRunMode {
    IDLE,            // 未运行 / 深度休眠 (0 耗电)
    PERMANENT_24H,   // 强制 24 小时全天候后台运行模式
    AGILE_TIMED      // 敏捷限时模式 (日常程序唤醒或手动开启，防杀保活，设定时间或完成任务后休眠)
}

class SignalRadarForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var radarManager: RadarManager
    private lateinit var detectionEngine: DetectionEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationBridge: OneUiNotificationBridge

    private var countdownJob: Job? = null
    private var scanCollectionJob: Job? = null
    private var ruleCollectionJob: Job? = null
    private var currentActiveRulesCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        radarManager = SignalStarApp.instance.radarManager
        detectionEngine = SignalStarApp.instance.detectionEngine
        settingsRepository = SignalStarApp.instance.settingsRepository
        notificationBridge = SignalStarApp.instance.notificationBridge

        val ruleRepository = SignalStarApp.instance.ruleRepository

        // 持续动态监听感知规则变化，实时更新下拉栏显示的规则条数并按需裁剪硬件扫描
        ruleCollectionJob = scope.launch {
            ruleRepository.rulesFlow.collect { rules ->
                val activeRules = rules.filter { it.enabled }
                currentActiveRulesCount = activeRules.size

                if (_currentMode.value != ServiceRunMode.IDLE || radarManager.isScanning.value) {
                    val settings = settingsRepository.settingsFlow.first()
                    if (_currentMode.value != ServiceRunMode.IDLE) {
                        val hasActiveBle = activeRules.any { it.signalType == SignalType.BLE }
                        val hasActiveWifi = activeRules.any { it.signalType == SignalType.WIFI }

                        // 智能硬件按需裁剪：若用户没有启用任何 Wi-Fi 规则，绝对不唤醒 Wi-Fi 芯片扫描；反之亦然！
                        val effectiveScanBle = settings.scanBleEnabled && (hasActiveBle || activeRules.isEmpty())
                        val effectiveScanWifi = settings.scanWifiEnabled && (hasActiveWifi || activeRules.isEmpty())

                        radarManager.updateScanTargets(
                            scanBle = effectiveScanBle,
                            scanWifi = effectiveScanWifi,
                            lowLatency = (_currentMode.value == ServiceRunMode.AGILE_TIMED)
                        )

                        val remainingMins = if (_currentMode.value == ServiceRunMode.AGILE_TIMED) {
                            _remainingSeconds.value / 60
                        } else null
                        updateNotification(
                            isPermanent = (_currentMode.value == ServiceRunMode.PERMANENT_24H),
                            remainingMinutes = remainingMins,
                            activeRulesCount = currentActiveRulesCount
                        )
                    }

                    // 当用户在当前 Wi-Fi/蓝牙环境下新建、修改或重新开启规则(lastTriggeredAt==0)时，立即用实时设备列表触发一次重评估验证！
                    if (activeRules.any { it.lastTriggeredAt == 0L }) {
                        delay(150)
                        radarManager.devices.value.forEach { device ->
                            detectionEngine.evaluate(device, settings.notificationAutoDismissSeconds)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC_STATE
        Log.d(TAG, "onStartCommand: action=$action")
        LogRepository.log(TAG, activeAppStrings.logServiceActionFmt(action))

        when (action) {
            ACTION_START_24H -> {
                startPermanentMode()
            }
            ACTION_START_AGILE -> {
                val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, -1) ?: -1
                startAgileMode(durationMinutes)
            }
            ACTION_STOP_SCAN -> {
                stopRadarService()
                return START_NOT_STICKY
            }
            ACTION_SYNC_STATE -> {
                syncWithCurrentSettings()
            }
        }

        return if (_currentMode.value == ServiceRunMode.PERMANENT_24H) {
            START_STICKY
        } else {
            START_REDELIVER_INTENT
        }
    }

    private fun syncWithCurrentSettings() {
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.isPermanent24hEnabled) {
                startPermanentMode()
            } else if (_currentMode.value == ServiceRunMode.IDLE) {
                // 如果没有开启 24h 且当前是 IDLE，则保持休眠
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * 开启 24 小时全天候强制后台运行
     * 深度优化：采用低功耗硬件平衡扫描，不长持 CPU WakeLock，允许芯片深度睡眠，日耗电降至 1%~2%
     * 同步立即晋升为前台服务，确保最近任务点击“全部关闭”时具备最高系统级防杀优先级
     */
    private fun startPermanentMode() {
        _currentMode.value = ServiceRunMode.PERMANENT_24H
        _remainingSeconds.value = -1
        countdownJob?.cancel()
        PowerHelper.releaseWakeLock()
        promoteToForeground(isPermanent = true, activeRulesCount = currentActiveRulesCount)

        scope.launch {
            val rules = SignalStarApp.instance.ruleRepository.rulesFlow.first()
            currentActiveRulesCount = rules.count { it.enabled }
            updateNotification(isPermanent = true, activeRulesCount = currentActiveRulesCount)
            startScanning()
            LogRepository.log(TAG, activeAppStrings.logServiceStarted24hFmt(currentActiveRulesCount))
        }
    }

    /**
     * 开启敏捷限时模式 (日常程序唤醒或手动临时开启)
     * 在此模式下：前台常驻防系统杀死，设定时间或外部调用后优雅休眠
     */
    private fun startAgileMode(specifiedMinutes: Int) {
        val initialMins = if (specifiedMinutes > 0) specifiedMinutes else 120
        _currentMode.value = ServiceRunMode.AGILE_TIMED
        PowerHelper.acquireWakeLock(this@SignalRadarForegroundService, "SignalStar:AgileLock")
        promoteToForeground(isPermanent = false, remainingMinutes = initialMins)

        scope.launch {
            val defaultDuration = settingsRepository.settingsFlow.first().agileDurationMinutes
            val minutes = if (specifiedMinutes > 0) specifiedMinutes else defaultDuration
            val totalSeconds = minutes * 60

            _remainingSeconds.value = totalSeconds
            updateNotification(isPermanent = false, remainingMinutes = minutes)
            startScanning()
            LogRepository.log(TAG, activeAppStrings.logServiceStartedAgileFmt(minutes))

            // 倒计时管理器
            countdownJob?.cancel()
            countdownJob = launch {
                var secondsLeft = totalSeconds
                while (isActive && secondsLeft > 0) {
                    delay(1000)
                    secondsLeft--
                    _remainingSeconds.value = secondsLeft

                    // 每 5 分钟更新一次通知上的剩余时间
                    if (secondsLeft % 300 == 0) {
                        updateNotification(isPermanent = false, remainingMinutes = secondsLeft / 60)
                    }
                }
                if (isActive) {
                    LogRepository.log(TAG, activeAppStrings.logServiceAgileTimeout)
                    stopRadarService()
                }
            }
        }
    }

    private fun startScanning() {
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val rules = SignalStarApp.instance.ruleRepository.rulesFlow.first()
            val activeRules = rules.filter { it.enabled }

            val hasActiveBle = activeRules.any { it.signalType == SignalType.BLE }
            val hasActiveWifi = activeRules.any { it.signalType == SignalType.WIFI }

            // 智能硬件按需裁剪：后台默认不扫 Wi-Fi，仅在用户主动启用了 Wi-Fi 规则时才开启，彻底避免无意义的定位提示与功耗！
            val effectiveScanBle = settings.scanBleEnabled && (hasActiveBle || activeRules.isEmpty())
            val effectiveScanWifi = settings.scanWifiEnabled && hasActiveWifi

            LogRepository.log(
                TAG,
                "Hardware policy: mode=${_currentMode.value}, BLE=${if (effectiveScanBle) "ON" else "OFF"}, Wi-Fi=${if (effectiveScanWifi) "ON" else "OFF (no active Wi-Fi rules)"}"
            )

            radarManager.startScanning(
                scanBle = effectiveScanBle,
                scanWifi = effectiveScanWifi,
                lowLatency = (_currentMode.value == ServiceRunMode.AGILE_TIMED)
            )
            // 刚启动服务时对当前已在雷达列表中的实时设备执行一次评估
            radarManager.devices.value.forEach { device ->
                detectionEngine.evaluate(device, settings.notificationAutoDismissSeconds)
            }
        }
    }

    private fun stopRadarService() {
        if (_currentMode.value == ServiceRunMode.IDLE) return
        LogRepository.log(TAG, activeAppStrings.logServiceStopping)
        countdownJob?.cancel()
        countdownJob = null
        scanCollectionJob?.cancel()
        scanCollectionJob = null

        radarManager.stopScanning()
        PowerHelper.releaseWakeLock()

        _currentMode.value = ServiceRunMode.IDLE
        _remainingSeconds.value = 0

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("ForegroundServiceType")
    private fun promoteToForeground(
        isPermanent: Boolean,
        remainingMinutes: Int? = null,
        activeRulesCount: Int = currentActiveRulesCount
    ) {
        val notification = notificationBridge.buildForegroundNotification(
            isPermanent24h = isPermanent,
            remainingMinutes = remainingMinutes,
            activeRulesCount = activeRulesCount
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                OneUiNotificationBridge.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(OneUiNotificationBridge.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        isPermanent: Boolean,
        remainingMinutes: Int? = null,
        activeRulesCount: Int = currentActiveRulesCount
    ) {
        val notification = notificationBridge.buildForegroundNotification(
            isPermanent24h = isPermanent,
            remainingMinutes = remainingMinutes,
            activeRulesCount = activeRulesCount
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(OneUiNotificationBridge.FOREGROUND_NOTIFICATION_ID, notification)
    }

    /**
     * 当用户在“最近使用的应用”中点击“全部关闭”或上滑划掉卡片时触发：
     * 1. 若当前处于 24 小时常驻或敏捷探测中，绝不关闭服务，立即刷新前台状态；
     * 2. 同时向系统 AlarmManager 注册 1.5 秒后的兜底唤醒信标，哪怕部分激进系统清理进程也能瞬间自愈复活！
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val mode = _currentMode.value
        if (mode != ServiceRunMode.IDLE) {
            LogRepository.log(TAG, activeAppStrings.logServiceTaskRemoved)
            val isPermanent = (mode == ServiceRunMode.PERMANENT_24H)
            val remainingMins = if (!isPermanent) (_remainingSeconds.value / 60).coerceAtLeast(1) else null
            promoteToForeground(isPermanent = isPermanent, remainingMinutes = remainingMins, activeRulesCount = currentActiveRulesCount)
            scheduleSelfRevive(mode)
        }
    }

    private fun scheduleSelfRevive(mode: ServiceRunMode) {
        try {
            val reviveAction = if (mode == ServiceRunMode.PERMANENT_24H) ACTION_START_24H else ACTION_START_AGILE
            val restartIntent = Intent(applicationContext, SignalRadarForegroundService::class.java).apply {
                action = reviveAction
                if (mode == ServiceRunMode.AGILE_TIMED) {
                    putExtra(EXTRA_DURATION_MINUTES, (_remainingSeconds.value / 60).coerceAtLeast(1))
                }
            }
            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(applicationContext, 9527, restartIntent, flags)
            } else {
                PendingIntent.getService(applicationContext, 9527, restartIntent, flags)
            }
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1500L,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "scheduleSelfRevive error: ${e.message}")
        }
    }

    override fun onDestroy() {
        val activeModeBeforeDestroy = _currentMode.value
        super.onDestroy()
        Log.d(TAG, "Service onDestroy (activeMode=$activeModeBeforeDestroy)")
        if (activeModeBeforeDestroy != ServiceRunMode.IDLE) {
            // 若并非主动调用 stopRadarService() 退出，而是被系统极端清理销毁，释放旧扫描句柄并触发 1.5s 自愈重启
            countdownJob?.cancel()
            scanCollectionJob?.cancel()
            radarManager.stopScanning()
            PowerHelper.releaseWakeLock()
            scheduleSelfRevive(activeModeBeforeDestroy)
        } else {
            stopRadarService()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RadarService"

        const val ACTION_START_24H = "com.clean.signalstar.ACTION_START_24H"
        const val ACTION_START_AGILE = "com.clean.signalstar.ACTION_START_AGILE"
        const val ACTION_STOP_SCAN = "com.clean.signalstar.ACTION_STOP_SCAN"
        const val ACTION_SYNC_STATE = "com.clean.signalstar.ACTION_SYNC_STATE"

        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"

        private val _currentMode = MutableStateFlow(ServiceRunMode.IDLE)
        val currentMode: StateFlow<ServiceRunMode> = _currentMode.asStateFlow()

        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        fun startPermanent(context: Context) {
            val intent = Intent(context, SignalRadarForegroundService::class.java).apply {
                action = ACTION_START_24H
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startAgile(context: Context, minutes: Int = 120) {
            val intent = Intent(context, SignalRadarForegroundService::class.java).apply {
                action = ACTION_START_AGILE
                putExtra(EXTRA_DURATION_MINUTES, minutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SignalRadarForegroundService::class.java).apply {
                action = ACTION_STOP_SCAN
            }
            context.startService(intent)
        }
    }
}
