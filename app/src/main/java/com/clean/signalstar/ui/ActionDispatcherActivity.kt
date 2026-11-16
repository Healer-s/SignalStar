package com.clean.signalstar.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.clean.signalstar.core.bridge.RoutineControlReceiver
import com.clean.signalstar.core.service.SignalRadarForegroundService
import com.clean.signalstar.ui.i18n.activeAppStrings

/**
 * 专为三星「模式与日常程序」快捷方式打造的静默执行调度器
 * 无 UI 界面，接收动作后纯后台启动对应服务并立即 finish，绝不打扰用户当前屏幕！
 */
class ActionDispatcherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        val action = intent?.action ?: RoutineControlReceiver.ACTION_START_AGILE
        handleAction(action, intent)

        finish()
        overridePendingTransition(0, 0)
    }

    private fun handleAction(action: String, intent: Intent?) {
        val s = activeAppStrings
        when (action) {
            RoutineControlReceiver.ACTION_START_AGILE -> {
                val duration = intent?.getIntExtra(RoutineControlReceiver.EXTRA_DURATION_MINUTES, -1) ?: -1
                SignalRadarForegroundService.startAgile(this, duration)
                Toast.makeText(this, s.shortcutAgileToast, Toast.LENGTH_SHORT).show()
            }
            RoutineControlReceiver.ACTION_START_24H -> {
                SignalRadarForegroundService.startPermanent(this)
                Toast.makeText(this, s.shortcut24hToast, Toast.LENGTH_SHORT).show()
            }
            RoutineControlReceiver.ACTION_STOP -> {
                SignalRadarForegroundService.stop(this)
                Toast.makeText(this, s.shortcutStopToast, Toast.LENGTH_SHORT).show()
            }
            else -> {
                // 默认启动敏捷模式
                SignalRadarForegroundService.startAgile(this)
                Toast.makeText(this, s.shortcutAgileToast, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
