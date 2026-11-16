package com.clean.signalstar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.clean.signalstar.core.bridge.OneUiNotificationBridge
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.data.RuleRepository
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.engine.DetectionEngine
import com.clean.signalstar.core.scanner.RadarManager
import com.clean.signalstar.core.service.ServiceRunMode
import com.clean.signalstar.core.service.SignalRadarForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SignalStarApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.Default + Job())

    lateinit var ruleRepository: RuleRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var notificationBridge: OneUiNotificationBridge
        private set

    lateinit var radarManager: RadarManager
        private set

    lateinit var detectionEngine: DetectionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        LogRepository.init(this)
        ruleRepository = RuleRepository(this)
        settingsRepository = SettingsRepository(this)
        notificationBridge = OneUiNotificationBridge(this)
        radarManager = RadarManager(this)

        detectionEngine = DetectionEngine(
            context = this,
            ruleRepository = ruleRepository,
            notificationBridge = notificationBridge,
            onTriggerSuccess = { _ ->
                appScope.launch {
                    val settings = settingsRepository.settingsFlow.first()
                    if (settings.autoStopAfterTrigger &&
                        SignalRadarForegroundService.currentMode.value == ServiceRunMode.AGILE_TIMED
                    ) {
                        LogRepository.log("RadarService", com.clean.signalstar.ui.i18n.activeAppStrings.logAppAutoStop)
                        delay(2000)
                        SignalRadarForegroundService.stop(this@SignalStarApp)
                    }
                }
            }
        )

        // 全局唯一事件管线：无论是在前台「雷达嗅探」页面开启扫描，还是后台「24h/敏捷」模式扫描，统一实时驱动规则触发！
        appScope.launch {
            radarManager.deviceEventFlow.collect { device ->
                detectionEngine.evaluate(device, 15)
            }
        }

        // 当设备拿远/断电超过 10 秒从雷达列表中移除时，立即通知引擎将其状态归位为 OUTSIDE，确保下次贴近 100% 触发！
        appScope.launch {
            radarManager.deviceLostFlow.collect { lostDeviceId ->
                detectionEngine.onDeviceLost(lostDeviceId)
            }
        }

        // 若用户已开启「强制后台 24 小时运行」，在 App 冷启动或覆盖升级安装后自动拉起前台常驻守护服务，防止界面开关与实际服务状态脱节
        appScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.isPermanent24hEnabled &&
                SignalRadarForegroundService.currentMode.value == ServiceRunMode.IDLE
            ) {
                LogRepository.log("App", com.clean.signalstar.ui.i18n.activeAppStrings.logAppBootResume24h)
                SignalRadarForegroundService.startPermanent(this@SignalStarApp)
            }
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 前台雷达监听常驻通道 (Low, 静默)
            val serviceChannel = NotificationChannel(
                CHANNEL_RADAR_SERVICE,
                getString(R.string.channel_radar_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_radar_service_desc)
                setShowBadge(false)
            }

            // 三星日常程序感知触发通道 (Low, 静默无震动，用于被日常程序读取通知文本)
            val triggerChannel = NotificationChannel(
                CHANNEL_TRIGGER_NOTIFY,
                getString(R.string.channel_trigger_notify),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_trigger_notify_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(triggerChannel)
        }
    }

    companion object {
        const val CHANNEL_RADAR_SERVICE = "signalstar_radar_service"
        const val CHANNEL_TRIGGER_NOTIFY = "signalstar_trigger_events"

        lateinit var instance: SignalStarApp
            private set
    }
}
