package com.clean.signalstar.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.service.SignalRadarForegroundService
import com.clean.signalstar.ui.i18n.activeAppStrings

/**
 * 专为三星「模式与日常程序」及外部快捷方式打造的控制器接收器
 * 支持在日常程序中配置：
 * - 发送广播：com.clean.signalstar.ACTION_START_AGILE
 * - 发送广播：com.clean.signalstar.ACTION_STOP
 */
class RoutineControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received routine action: $action")
        LogRepository.log(TAG, activeAppStrings.logRoutineBroadcastFmt(action))

        when (action) {
            ACTION_START_AGILE, ACTION_START_SCAN -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_MINUTES, -1)
                SignalRadarForegroundService.startAgile(context, duration)
            }
            ACTION_START_24H -> {
                SignalRadarForegroundService.startPermanent(context)
            }
            ACTION_STOP, ACTION_STOP_SCAN -> {
                SignalRadarForegroundService.stop(context)
            }
        }
    }

    companion object {
        private const val TAG = "RoutineReceiver"

        const val ACTION_START_AGILE = "com.clean.signalstar.ACTION_START_AGILE"
        const val ACTION_START_SCAN = "com.clean.signalstar.ACTION_START_SCAN"
        const val ACTION_START_24H = "com.clean.signalstar.ACTION_START_24H"
        const val ACTION_STOP = "com.clean.signalstar.ACTION_STOP"
        const val ACTION_STOP_SCAN = "com.clean.signalstar.ACTION_STOP_SCAN"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    }
}
