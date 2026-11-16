package com.clean.signalstar.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.service.SignalRadarForegroundService
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            LogRepository.log("BootReceiver", activeAppStrings.logBootCheck)
            CoroutineScope(Dispatchers.Default).launch {
                val settings = SettingsRepository(context).settingsFlow.first()
                if (settings.isPermanent24hEnabled) {
                    LogRepository.log("BootReceiver", activeAppStrings.logBootStart24h)
                    SignalRadarForegroundService.startPermanent(context)
                } else {
                    LogRepository.log("BootReceiver", activeAppStrings.logBootKeepSleep)
                }
            }
        }
    }
}
