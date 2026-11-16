package com.clean.signalstar.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String) {
    AUTO("AUTO"),
    ZH("ZH"),
    EN("EN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

/**
 * 集中式多语言文案定义（唯一数据源）
 * 所有页面与弹窗均通过 LocalStrings.current 读取，禁止在 UI 组件内硬编码中英文分支。
 */
interface AppStrings {
    val isEnglish: Boolean
    // Common
    val close: String
    val cancel: String
    val backDesc: String
    val copyBtn: String
    val signalTypeBt: String
    val signalTypeBtFull: String
    val signalTypeWifi: String
    val signalTypeWifiHotspot: String
    // Bottom Tabs
    val tabControl: String
    val tabRadar: String
    val tabRules: String
    // Home Screen
    val homeTitle: String
    val homeSubtitle: String
    val guideIconDesc: String
    val logsIconDesc: String
    val languageTitle: String
    val languageSubtitle: String
    val langAuto: String
    val langAutoDesc: String
    val langZh: String
    val langEn: String
    val permanent24hTitle: String
    val permanent24hDescOn: String
    val permanent24hDescOff: String
    val status24hTitle: String
    val status24hDesc: String
    val statusAgileActiveFmt: (mins: Int, secs: Int) -> String
    val statusAgileDesc: String
    val statusIdleTitle: String
    val statusIdleDesc: String
    val btnStartAgile: String
    val btnStopSleep: String
    val agileCardTitle: String
    val agileLockedBy24h: String
    val agileCardDesc: String
    val agileDurationLabel: String
    val agileDurationUnitFmt: (minutes: Int) -> String
    val autoStopTitle: String
    val autoStopDesc: String
    val batteryTitle: String
    val batteryDesc: String
    val batteryGoSettings: String
    val batteryDismiss: String
    val prompt24hTitle: String
    val prompt24hLine1: String
    val prompt24hLine2: String
    val prompt24hConfirm: String
    val prompt24hCancel: String
    // Radar Screen
    val radarTitle: String
    val radarDaemonSubtitle: String
    val radarScanningSubtitle: String
    val radarIdleSubtitle: String
    val daemon24hBadge: String
    val daemonAgileBadge: String
    val scanStartDesc: String
    val scanStopDesc: String
    val filterAll: String
    val filterRuled: String
    val filterBt: String
    val filterWifi: String
    val hiddenSsid: String
    val radarRuleSummary: (count: Int, firstName: String) -> String
    val btnSetRule: String
    val btnAddMoreRule: String
    val radarEmptyIdle: String
    val radarEmptyRuled: String
    val radarEmptyScanning: String
    val radarStartBtn: String
    // Rules Screen
    val rulesTitle: String
    val rulesNewDesc: String
    val rulesCountSummary: (enabled: Int, total: Int) -> String
    val rulesEmpty: String
    val rulesBackupBtn: String
    val rulesByMac: String
    val rulesMatchFmt: (pattern: String, type: String, mac: String?) -> String
    val rulesLeaveDefaultPrefix: String
    val rulesTagWithLeaveFmt: (triggerTag: String, leaveTag: String) -> String
    val rulesTagOnlyFmt: (triggerTag: String) -> String
    val rulesNeverTriggered: String
    val rulesLastTriggeredFmt: (time: String) -> String
    val rulesThresholdSummaryFmt: (threshold: Int, lastText: String) -> String
    val rulesScanning: String
    val rulesTestBtn: String
    val rulesTestDesc: String
    // Backup Dialog
    val backupDialogTitle: String
    val backupDialogSubtitle: (count: Int) -> String
    val backupExportJson: String
    val backupImportJson: String
    val backupOrTextSection: String
    val backupCopyText: String
    val backupShareText: String
    val backupPasteClipboard: String
    val backupImportTextBtn: String
    // Hardware Test Result Dialog
    val testPassedTitle: String
    val testPassedSubtitle: String
    val testRowMatchedDevice: String
    val testRowDetectedDevice: String
    val testRowLiveSignal: String
    val testRowThreshold: String
    val testRowNotifyTag: String
    val testThresholdMetFmt: (threshold: Int) -> String
    val testThresholdShortFmt: (threshold: Int, diff: Int) -> String
    val testPassedDisabledTip: String
    val testPassedEnabledTip: String
    val testPassedOkBtn: String
    val testWeakTitle: String
    val testWeakSubtitle: String
    val testWeakTipFmt: (suggested: Int) -> String
    val testWeakLowerBtnFmt: (suggested: Int) -> String
    val testWeakAdjustedToastFmt: (suggested: Int) -> String
    val testMockBtn: String
    val testWeakMockSentToast: String
    val testNotFoundTitle: String
    val testNotFoundSubtitle: String
    val testNotFoundBoundMacFmt: (mac: String) -> String
    val testNotFoundNoPattern: String
    val testNotFoundRowType: String
    val testNotFoundRowCondition: String
    val testNotFoundRowThreshold: String
    val testNotFoundTip: String
    val testNotFoundMockBtn: String
    val testNotFoundMockSentToastFmt: (tag: String) -> String
    // Rule Edit Dialog
    val editCreateFromDeviceTitle: String
    val editCreateFromDeviceDefaultName: (idTail: String) -> String
    val editCreateFromDeviceNamed: (name: String) -> String
    val editCreateFromDeviceDefaultTag: (idTail4: String) -> String
    val editNewRuleTitle: String
    val editNewRuleSubtitle: String
    val editNewRuleDefaultTagBle: String
    val editNewRuleDefaultTagWifi: String
    val editEditRuleTitle: String
    val editEditRuleSubtitle: String
    val editBtPrefixLabel: String
    val editBtPrefixOptionalLabel: String
    val editBtPrefixShortLabel: String
    val editWifiSsidLabel: String
    val editWifiSsidOptionalLabel: String
    val editRuleNameLabel: String
    val editRuleNamePlaceholderBt: String
    val editRuleNamePlaceholderWifi: String
    val editBindMacTitleBt: String
    val editBindMacTitleWifi: String
    val editBindMacDescOn: String
    val editBindMacDescOff: String
    val editTargetMacLabelBt: String
    val editTargetMacLabelWifi: String
    val editTriggerTagLabel: String
    val editTriggerTagPlaceholder: String
    val editTriggerRssiTitle: String
    val editDistanceClose: String
    val editDistanceNear: String
    val editDistanceFar: String
    val editLiveRssiFmt: (rssi: Int) -> String
    val editSetSuggestedFmt: (suggested: Int) -> String
    val editLeaveToggleTitle: String
    val editLeaveToggleDesc: String
    val editLeaveTagLabel: String
    val editLeaveTagPlaceholderFmt: (fallbackTag: String) -> String
    val editSaveRuleBtn: String
    // Diagnostics Screen
    val diagTitle: String
    val diagSubtitle: String
    val diagClearDesc: String
    val diagClearedTriggersToast: String
    val diagClearedLogsToast: String
    val diagTabTriggersFmt: (count: Int) -> String
    val diagTabLogsFmt: (count: Int) -> String
    val diagDebugSwitchTitle: String
    val diagDebugSwitchDescOn: String
    val diagDebugSwitchDescOff: String
    val diagCopiedTriggersToast: String
    val diagCopiedLogsToast: String
    val diagCopyTriggersBtn: String
    val diagCopyLogsBtn: String
    val diagShareChooserTitle: String
    val diagShareBtn: String
    val diagEmptyTriggers: String
    val diagEmptyLogsDisabled: String
    val diagEmptyLogsEnabled: String
    val diagCardRuleTriggered: String
    // Routine Guide Screen
    val guideTitle: String
    val guideSubtitle: String
    val guidePowerTitle: String
    val guidePowerSubtitle: String
    val guideModeATitle: String
    val guideModeABadge: String
    val guideModeADesc: String
    val guideModeBTitle: String
    val guideModeBBadge: String
    val guideModeBDesc: String
    val guideModeCTitle: String
    val guideModeCBadge: String
    val guideModeCDesc: String
    val guideStep1Title: String
    val guideStep1Intro: String
    val guideIfLabel: String
    val guideStep1If: String
    val guideStep1ThenLabel: String
    val guideStep1Then1: String
    val guideStep1Then2: String
    val guideStep1Tip: String
    val guideStep2Title: String
    val guideStep2Intro: String
    val guideStep2If1: String
    val guideStep2If2: String
    val guideThenLabel: String
    val guideStep2Then: String
    val guideStep3Title: String
    val guideStep3Intro: String
    val guideStep3ActionLabel: String
    val guideStep3CopiedToast: String
    val guideStep4Title: String
    val guideStep4Intro: String
    val guideStep4CopiedToast: String
    // Version Dialog & Extra Banners / Toasts
    val versionDialogTitle: String
    val versionLine1: String
    val versionLine2: String
    val versionNoBrowserToast: String
    val versionGithubBtn: String
    val versionGiteeBtn: String
    val versionUpToDateFmt: (version: String) -> String
    val radarPermMissing: String
    val radarBtOff: String
    val radarLocOff: String
    val radarGoEnableBtn: String
    val radarDaemonStopHintToast: String
    val radarDaemonSwitchedBackToast: String
    val radarEmptyIdleDesc: String
    val radarEmptyRuledDesc: String
    val radarEmptyScanningDesc: String
    val rulesExportedJsonToast: String
    val rulesExportFailedFmt: (err: String) -> String
    val rulesImportedJsonFmt: (count: Int) -> String
    val rulesImportFailed: String
    val rulesReadFailedFmt: (err: String) -> String
    val rulesBgLocBanner: String
    val rulesGoSettingsBtn: String
    val rulesCopiedJsonFmt: (count: Int) -> String
    val rulesShareBackupTitle: String
    val rulesImportedTextFmt: (count: Int) -> String
    val rulesEditDesc: String
    val rulesDeleteDesc: String
    val rulesClipboardEmptyToast: String
    val editFallbackTriggerTag: String
    val editFallbackRuleNameBt: String
    val editFallbackRuleNameWifi: String
    // Clear Confirm & About
    val diagClearConfirmTitleTriggers: String
    val diagClearConfirmTitleLogs: String
    val diagClearConfirmMsg: String
    val diagClearConfirmBtn: String
    val aboutMenuTitle: String
    val aboutMenuSubtitleFmt: (version: String) -> String
    val aboutDialogTitle: String
    val aboutDialogDesc: String
    // Core / Service / Logs / Notifications
    val logRuntimeDebugEnabled: String
    val logExportTriggersHeader: String
    val logExportRuntimeHeader: String
    val logExportTimePrefix: String
    val logExportTriggerTag: String
    val logExportRuntimeTag: String
    val logServiceActionFmt: (action: String?) -> String
    val logServiceStarted24hFmt: (count: Int) -> String
    val logServiceStartedAgileFmt: (minutes: Int) -> String
    val logServiceAgileTimeout: String
    val logServiceHardwarePolicyFmt: (bleOn: Boolean, wifiOn: Boolean) -> String
    val logServiceStopping: String
    val logServiceTaskRemoved: String
    val logBootCheck: String
    val logBootStart24h: String
    val logBootKeepSleep: String
    val logRoutineBroadcastFmt: (action: String?) -> String
    val logEngineOfflineFmt: (deviceId: String) -> String
    val logEngineReappearedFmt: (ruleName: String, sec: Long) -> String
    val logEngineDebounceFmt: (ruleName: String, remainSec: Long) -> String
    val logEngineEnterZoneFmt: (ruleName: String, deviceId: String, rssi: Int, threshold: Int) -> String
    val logEngineLeaveZoneFmt: (ruleName: String, rssi: Int, exitThreshold: Int) -> String
    val logEngineTriggeredFmt: (ruleName: String, rssi: Int, deviceId: String, tag: String) -> String
    val leaveTagDefaultFmt: (triggerTag: String) -> String
    val logEngineLeaveTriggeredFmt: (ruleName: String, deviceId: String, tag: String) -> String
    val notifTriggerTitle: String
    val notifLeaveTitle: String
    val notifAutomationSubtext: String
    val notifSignalStrengthFmt: (rssi: Int) -> String
    val notifLeaveSignalAttenuatedFmt: (rssi: Int) -> String
    val notifLeaveDeviceOffline: String
    val notifSentLogFmt: (text: String) -> String
    val notifBroadcastLogFmt: (action: String, tag: String) -> String
    val notifAutoRevokedLogFmt: (id: Int) -> String
    val notifSendErrorLogFmt: (msg: String?) -> String
    val notifLeaveSentLogFmt: (text: String) -> String
    val notifLeaveSendErrorLogFmt: (msg: String?) -> String
    val notifFg24hTitle: String
    val notifFgRemainingFmt: (minutes: Int) -> String
    val notifFgAgileTitleFmt: (remainText: String) -> String
    val notifFg24hBodyFmt: (count: Int) -> String
    val notifFgAgileBody: String
    val notifFgStopAction: String
    val batteryUnrestrictedGuideToast: String
    val ruleImportEmptyError: String
    val ruleImportInvalidError: String
    val ruleImportParseError: String
    val wifiCurrentHotspot: String
    val wifiHiddenSsid: String
    val logAppAutoStop: String
    val logAppBootResume24h: String
    val logEnginePausedTestTriggeredFmt: (ruleName: String, rssi: Int, deviceId: String, tag: String) -> String
    val shortcutAgileToast: String
    val shortcut24hToast: String
    val shortcutStopToast: String
    val logWifiBroadcastFmt: (updated: Boolean, screenOn: Boolean, deltaSec: Long) -> String
    val aboutSupportTitle: String
    val aboutSupportSubtitle: String
    val aboutQrWechat: String
    val aboutQrAlipay: String
}

val LocalStrings = staticCompositionLocalOf { ZhStrings }

@Volatile
var activeAppStrings: AppStrings = ZhStrings

fun resolveAppStrings(language: AppLanguage): AppStrings {
    val resolved = when (language) {
        AppLanguage.ZH -> ZhStrings
        AppLanguage.EN -> EnStrings
        AppLanguage.AUTO -> {
            val sysLang = Locale.getDefault().language
            if (sysLang.startsWith("zh", ignoreCase = true)) ZhStrings else EnStrings
        }
    }
    activeAppStrings = resolved
    return resolved
}
