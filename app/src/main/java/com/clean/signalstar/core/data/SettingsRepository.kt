package com.clean.signalstar.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clean.signalstar.ui.i18n.AppLanguage
import com.clean.signalstar.ui.i18n.resolveAppStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "signalstar_settings_pref")

data class AppSettings(
    val isPermanent24hEnabled: Boolean = false,   // 强制后台 24 小时常驻运行开关
    val agileDurationMinutes: Int = 120,          // 日常程序/手动触发敏捷模式的持续运行时间 (分钟)
    val autoStopAfterTrigger: Boolean = false,    // 成功探测并触发后是否自动回退休眠
    val scanBleEnabled: Boolean = true,           // 是否启用 BLE 扫描
    val scanWifiEnabled: Boolean = true,          // 是否启用 Wi-Fi 扫描
    val notificationAutoDismissSeconds: Int = 15, // 静默通知自动撤销秒数
    val appLanguage: String = "AUTO"              // 应用语言: AUTO(跟随系统) / ZH / EN
)

class SettingsRepository(private val context: Context) {

    private val KEY_PERMANENT_24H = booleanPreferencesKey("is_permanent_24h_enabled")
    private val KEY_AGILE_DURATION = intPreferencesKey("agile_duration_minutes")
    private val KEY_AUTO_STOP_TRIGGER = booleanPreferencesKey("auto_stop_after_trigger")
    private val KEY_SCAN_BLE = booleanPreferencesKey("scan_ble_enabled")
    private val KEY_SCAN_WIFI = booleanPreferencesKey("scan_wifi_enabled")
    private val KEY_AUTO_DISMISS = intPreferencesKey("notification_auto_dismiss_seconds")
    private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { pref ->
        val lang = pref[KEY_APP_LANGUAGE] ?: "AUTO"
        resolveAppStrings(AppLanguage.fromCode(lang))
        AppSettings(
            isPermanent24hEnabled = pref[KEY_PERMANENT_24H] ?: false,
            agileDurationMinutes = pref[KEY_AGILE_DURATION] ?: 120,
            autoStopAfterTrigger = pref[KEY_AUTO_STOP_TRIGGER] ?: false,
            scanBleEnabled = pref[KEY_SCAN_BLE] ?: true,
            scanWifiEnabled = pref[KEY_SCAN_WIFI] ?: true,
            notificationAutoDismissSeconds = pref[KEY_AUTO_DISMISS] ?: 15,
            appLanguage = lang
        )
    }

    suspend fun setPermanent24hEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_PERMANENT_24H] = enabled }
    }

    suspend fun setAgileDurationMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_AGILE_DURATION] = minutes }
    }

    suspend fun setAutoStopAfterTrigger(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_STOP_TRIGGER] = enabled }
    }

    suspend fun setScanBleEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCAN_BLE] = enabled }
    }

    suspend fun setScanWifiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCAN_WIFI] = enabled }
    }

    suspend fun setNotificationAutoDismissSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[KEY_AUTO_DISMISS] = seconds }
    }

    suspend fun setAppLanguage(language: String) {
        resolveAppStrings(AppLanguage.fromCode(language))
        context.settingsDataStore.edit { it[KEY_APP_LANGUAGE] = language }
    }
}
