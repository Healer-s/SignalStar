package com.clean.signalstar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clean.signalstar.core.data.AppSettings
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.service.SignalRadarForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settingsRepository = SettingsRepository(application)

    // 全局持久保活的 Settings StateFlow，彻底避免 Tab 切换销毁重建导致闪烁与 initial 重置
    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    fun setPermanent24hEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPermanent24hEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                SignalRadarForegroundService.startPermanent(context)
            } else {
                SignalRadarForegroundService.stop(context)
            }
        }
    }

    fun setAgileDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAgileDurationMinutes(minutes)
        }
    }

    fun setAutoStopAfterTrigger(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStopAfterTrigger(enabled)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }
}
