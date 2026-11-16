package com.clean.signalstar.core.bridge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.clean.signalstar.R
import com.clean.signalstar.SignalStarApp
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.ui.MainActivity
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class OneUiNotificationBridge(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Default)
    private val notificationIdCounter = AtomicInteger(1000)

    /**
     * 发送静默特征通知供三星「模式与日常程序」读取拦截
     * 关键点：
     * 1. 内容必须包含唯一特征标签：`[Trigger: ${rule.triggerTag}]`
     * 2. 属于低优先级静默通道，不震动不响铃
     * 3. 在 autoDismissSeconds 秒后自动从通知栏销毁，保持系统整洁
     */
    @SuppressLint("NotificationPermission")
    fun sendTriggerNotification(
        rule: SignalRule,
        detectedRssi: Int,
        autoDismissSeconds: Int = 15
    ) {
        // 使用规则 ID 映射稳定唯一的 Notification ID，即使极短时间内并发，也会更新同一张卡片，绝不在通知栏重复堆叠！
        val notificationId = 2000 + (rule.id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % 10000)

        val s = activeAppStrings
        val contentText = "[Trigger: ${rule.triggerTag}] ${rule.name} (${s.notifSignalStrengthFmt(detectedRssi)})"

        val notification = NotificationCompat.Builder(context, SignalStarApp.CHANNEL_TRIGGER_NOTIFY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(s.notifTriggerTitle)
            .setContentText(contentText)
            .setSubText(s.notifAutomationSubtext)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            LogRepository.log("Bridge", s.notifSentLogFmt(contentText), isTrigger = false)

            // 发送系统标准广播，供 Tasker / MacroDroid / 自动任务拦截
            val broadcastIntent = Intent(ACTION_TRIGGER_BROADCAST).apply {
                putExtra(EXTRA_RULE_ID, rule.id)
                putExtra(EXTRA_RULE_NAME, rule.name)
                putExtra(EXTRA_TRIGGER_TAG, rule.triggerTag)
                putExtra(EXTRA_RSSI, detectedRssi)
                putExtra(EXTRA_SIGNAL_TYPE, rule.signalType.name)
            }
            context.sendBroadcast(broadcastIntent)
            LogRepository.log("Bridge", s.notifBroadcastLogFmt(ACTION_TRIGGER_BROADCAST, rule.triggerTag))

            // 自动定时撤销通知
            if (autoDismissSeconds > 0) {
                scope.launch {
                    delay(autoDismissSeconds * 1000L)
                    notificationManager.cancel(notificationId)
                    LogRepository.log("Bridge", activeAppStrings.notifAutoRevokedLogFmt(notificationId))
                }
            }
        } catch (e: Exception) {
            LogRepository.log("Bridge", s.notifSendErrorLogFmt(e.message))
        }
    }

    /**
     * 发送“离开信号范围”静默通知供三星日常程序拦截：[Leave: 标签]
     */
    @SuppressLint("NotificationPermission")
    fun sendLeaveNotification(
        rule: SignalRule,
        detectedRssi: Int?,
        autoDismissSeconds: Int = 15
    ) {
        val s = activeAppStrings
        val effectiveLeaveTag = rule.leaveTag.trim().ifEmpty { s.leaveTagDefaultFmt(rule.triggerTag) }
        val notificationId = 12000 + (rule.id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % 10000)
        val rssiDesc = if (detectedRssi != null) s.notifLeaveSignalAttenuatedFmt(detectedRssi) else s.notifLeaveDeviceOffline
        val contentText = "[Leave: $effectiveLeaveTag] ${rule.name} ($rssiDesc)"

        val notification = NotificationCompat.Builder(context, SignalStarApp.CHANNEL_TRIGGER_NOTIFY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(s.notifLeaveTitle)
            .setContentText(contentText)
            .setSubText(s.notifAutomationSubtext)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            LogRepository.log("Bridge", s.notifLeaveSentLogFmt(contentText), isTrigger = false)

            val broadcastIntent = Intent(ACTION_TRIGGER_BROADCAST).apply {
                putExtra(EXTRA_RULE_ID, rule.id)
                putExtra(EXTRA_RULE_NAME, rule.name)
                putExtra(EXTRA_TRIGGER_TAG, effectiveLeaveTag)
                putExtra(EXTRA_RSSI, detectedRssi ?: -127)
                putExtra(EXTRA_SIGNAL_TYPE, rule.signalType.name)
            }
            context.sendBroadcast(broadcastIntent)

            if (autoDismissSeconds > 0) {
                scope.launch {
                    delay(autoDismissSeconds * 1000L)
                    notificationManager.cancel(notificationId)
                }
            }
        } catch (e: Exception) {
            LogRepository.log("Bridge", s.notifLeaveSendErrorLogFmt(e.message))
        }
    }

    /**
     * 构建前台服务的常驻通知
     */
    fun buildForegroundNotification(
        isPermanent24h: Boolean,
        remainingMinutes: Int? = null,
        activeRulesCount: Int = 0
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止感知的快捷 Intent
        val stopIntent = Intent(context, RoutineControlReceiver::class.java).apply {
            action = RoutineControlReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val s = activeAppStrings
        val title = if (isPermanent24h) {
            s.notifFg24hTitle
        } else {
            val remainText = if (remainingMinutes != null && remainingMinutes > 0) {
                s.notifFgRemainingFmt(remainingMinutes)
            } else ""
            s.notifFgAgileTitleFmt(remainText)
        }

        val content = if (isPermanent24h) {
            s.notifFg24hBodyFmt(activeRulesCount)
        } else {
            s.notifFgAgileBody
        }

        return NotificationCompat.Builder(context, SignalStarApp.CHANNEL_RADAR_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .addAction(0, s.notifFgStopAction, stopPendingIntent)
            .build()
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 9527

        const val ACTION_TRIGGER_BROADCAST = "com.clean.signalstar.ACTION_TRIGGER"
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_RULE_NAME = "extra_rule_name"
        const val EXTRA_TRIGGER_TAG = "extra_trigger_tag"
        const val EXTRA_RSSI = "extra_rssi"
        const val EXTRA_SIGNAL_TYPE = "extra_signal_type"
    }
}
