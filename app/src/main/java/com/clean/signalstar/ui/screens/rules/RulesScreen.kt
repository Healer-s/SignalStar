package com.clean.signalstar.ui.screens.rules

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clean.signalstar.SignalStarApp
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.data.RuleRepository
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.MatchType
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.core.scanner.RadarManager
import com.clean.signalstar.ui.components.OneUiCard
import com.clean.signalstar.ui.components.OneUiSwitch
import com.clean.signalstar.ui.components.SignalTypeIcon
import com.clean.signalstar.ui.i18n.LocalStrings
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen
import com.clean.signalstar.ui.theme.SignalRed
import com.clean.signalstar.ui.theme.SignalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class RuleHardwareTestResult {
    data class Passed(val rule: SignalRule, val device: DiscoveredDevice) : RuleHardwareTestResult()
    data class WeakSignal(val rule: SignalRule, val device: DiscoveredDevice) : RuleHardwareTestResult()
    data class NotFound(val rule: SignalRule) : RuleHardwareTestResult()
}

@Composable
fun RulesScreen(
    ruleRepository: RuleRepository,
    radarManager: RadarManager? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = LocalStrings.current
    val uiScope = rememberCoroutineScope()
    val rules by ruleRepository.rulesFlow.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<SignalRule?>(null) }
    var testingRuleId by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<RuleHardwareTestResult?>(null) }

    // 原生 SAF .json 文件导出与导入启动器（零存储权限）
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            uiScope.launch {
                try {
                    val json = ruleRepository.exportRulesJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, s.rulesExportedJsonToast, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, s.rulesExportFailedFmt(e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uiScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }.orEmpty()
                    val res = ruleRepository.importRulesJson(json)
                    res.onSuccess { count ->
                        Toast.makeText(context, s.rulesImportedJsonFmt(count), Toast.LENGTH_SHORT).show()
                        showBackupDialog = false
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: s.rulesImportFailed, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, s.rulesReadFailedFmt(e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun checkBgLocationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    var hasBgLocation by remember { mutableStateOf(checkBgLocationGranted()) }
    val wifiLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasBgLocation = checkBgLocationGranted()
    }
    fun ensureLocationPermissionForWifi() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            wifiLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBgLocation = checkBgLocationGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showWifiBgLocationWarning = !hasBgLocation && rules.any { it.enabled && it.signalType == SignalType.WIFI }

    fun runLiveHardwareTest(rule: SignalRule) {
        if (testingRuleId != null) return
        testingRuleId = rule.id
        uiScope.launch {
            // 1. 重置该规则的冷却与在场锁，确保实测能立即重新评估
            SignalStarApp.instance.detectionEngine.resetRuleState(rule.id)
            ruleRepository.updateRule(rule.copy(lastTriggeredAt = 0L))

            // 2. 启动或复用实时硬件雷达扫描
            if (radarManager != null) {
                if (!radarManager.isScanning.value) {
                    radarManager.startScanning(lowLatency = true)
                    radarManager.onRadarScreenLeft() // 开启 3 分钟自动休眠保护
                } else {
                    radarManager.wifiScanner.triggerScan()
                }

                // 若当前列表里尚未扫到，等待最多 1.6 秒让硬件抓取最新广播帧
                var matched = radarManager.devices.value
                    .filter { rule.matchesDevice(it) }
                    .maxByOrNull { it.filteredRssi }

                if (matched == null) {
                    repeat(4) {
                        delay(400)
                        matched = radarManager.devices.value
                            .filter { rule.matchesDevice(it) }
                            .maxByOrNull { it.filteredRssi }
                        if (matched != null) return@repeat
                    }
                }

                testingRuleId = null
                val foundDevice = matched
                if (foundDevice == null) {
                    testResult = RuleHardwareTestResult.NotFound(rule)
                } else if (foundDevice.filteredRssi >= rule.enterRssiThreshold) {
                    // 真机信号达标！若规则已启用则交由全局 DetectionEngine 闭环触发防重；若规则当前已暂停开关，则直接发一次实测通知方便验证
                    if (rule.enabled) {
                        SignalStarApp.instance.detectionEngine.evaluate(foundDevice, 15)
                    } else {
                        LogRepository.log(
                            "Engine",
                            s.logEnginePausedTestTriggeredFmt(rule.name, foundDevice.filteredRssi, foundDevice.id, rule.triggerTag),
                            isTrigger = true
                        )
                        SignalStarApp.instance.notificationBridge.sendTriggerNotification(
                            rule = rule,
                            detectedRssi = foundDevice.filteredRssi,
                            autoDismissSeconds = 15
                        )
                    }
                    testResult = RuleHardwareTestResult.Passed(rule, foundDevice)
                } else {
                    testResult = RuleHardwareTestResult.WeakSignal(rule, foundDevice)
                }
            } else {
                testingRuleId = null
                testResult = RuleHardwareTestResult.NotFound(rule)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = SamsungBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = s.rulesNewDesc)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.rulesTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = s.rulesCountSummary(rules.count { it.enabled }, rules.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(SamsungBlue.copy(alpha = 0.12f))
                            .clickable { showBackupDialog = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ImportExport,
                            contentDescription = s.rulesBackupBtn,
                            tint = SamsungBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = s.rulesBackupBtn,
                            color = SamsungBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showWifiBgLocationWarning) {
                item {
                    OneUiCard(
                        backgroundColor = SignalYellow.copy(alpha = 0.14f),
                        borderColor = SignalYellow.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = SignalYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = s.rulesBgLocBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(s.rulesGoSettingsBtn, color = SamsungBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.rulesEmpty,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    RuleItemCard(
                        rule = rule,
                        isTesting = (testingRuleId == rule.id),
                        onClick = { ruleToEdit = rule },
                        onTest = { runLiveHardwareTest(rule) },
                        onToggle = { enabled ->
                            SignalStarApp.instance.detectionEngine.resetRuleState(rule.id)
                            if (enabled && rule.signalType == SignalType.WIFI) {
                                ensureLocationPermissionForWifi()
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                ruleRepository.toggleRule(rule.id, enabled)
                            }
                        },
                        onDelete = {
                            SignalStarApp.instance.detectionEngine.resetRuleState(rule.id)
                            CoroutineScope(Dispatchers.IO).launch {
                                ruleRepository.deleteRule(rule.id)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    if (showCreateDialog) {
        com.clean.signalstar.ui.components.ManualRuleCreationDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newRule ->
                if (newRule.signalType == SignalType.WIFI) {
                    ensureLocationPermissionForWifi()
                }
                CoroutineScope(Dispatchers.IO).launch {
                    ruleRepository.addRule(newRule)
                }
                showCreateDialog = false
            }
        )
    }

    ruleToEdit?.let { rule ->
        com.clean.signalstar.ui.components.EditExistingRuleDialog(
            rule = rule,
            onDismiss = { ruleToEdit = null },
            onConfirm = { updatedRule ->
                if (updatedRule.signalType == SignalType.WIFI) {
                    ensureLocationPermissionForWifi()
                }
                SignalStarApp.instance.detectionEngine.resetRuleState(updatedRule.id)
                CoroutineScope(Dispatchers.IO).launch {
                    ruleRepository.updateRule(updatedRule.copy(lastTriggeredAt = 0L))
                }
                ruleToEdit = null
            }
        )
    }

    if (showBackupDialog) {
        RuleBackupDialog(
            rulesCount = rules.size,
            onExportFile = {
                val dateTag = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                exportJsonLauncher.launch("SignalStar_rules_${dateTag}.json")
            },
            onImportFile = {
                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onExportCopy = {
                uiScope.launch {
                    val json = ruleRepository.exportRulesJson()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SignalStar Rules", json))
                    Toast.makeText(context, s.rulesCopiedJsonFmt(rules.size), Toast.LENGTH_SHORT).show()
                }
            },
            onExportShare = {
                uiScope.launch {
                    val json = ruleRepository.exportRulesJson()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, json)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, s.rulesShareBackupTitle))
                }
            },
            onImportJson = { rawJson ->
                uiScope.launch {
                    val res = ruleRepository.importRulesJson(rawJson)
                    res.onSuccess { count ->
                        Toast.makeText(context, s.rulesImportedTextFmt(count), Toast.LENGTH_SHORT).show()
                        showBackupDialog = false
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: s.rulesImportFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    testResult?.let { result ->
        when (result) {
            is RuleHardwareTestResult.Passed -> {
                OneUiTestResultDialog(
                    icon = Icons.Rounded.CheckCircle,
                    iconTint = SignalGreen,
                    title = s.testPassedTitle,
                    subtitle = s.testPassedSubtitle,
                    rows = listOf(
                        s.testRowMatchedDevice to "${result.device.name} (${result.device.id})",
                        s.testRowLiveSignal to "${result.device.filteredRssi} dBm",
                        s.testRowThreshold to s.testThresholdMetFmt(result.rule.enterRssiThreshold),
                        s.testRowNotifyTag to "[Trigger: ${result.rule.triggerTag}]"
                    ),
                    tipText = if (!result.rule.enabled) s.testPassedDisabledTip else s.testPassedEnabledTip,
                    tipColor = if (!result.rule.enabled) SignalYellow else SignalGreen,
                    primaryButtonText = s.testPassedOkBtn,
                    primaryButtonColor = SignalGreen,
                    onPrimaryClick = { testResult = null },
                    onDismiss = { testResult = null }
                )
            }

            is RuleHardwareTestResult.WeakSignal -> {
                val diff = result.rule.enterRssiThreshold - result.device.filteredRssi
                val suggestedThreshold = (result.device.filteredRssi - 2).coerceIn(-95, -45)
                OneUiTestResultDialog(
                    icon = Icons.Rounded.Warning,
                    iconTint = SignalYellow,
                    title = s.testWeakTitle,
                    subtitle = s.testWeakSubtitle,
                    rows = listOf(
                        s.testRowDetectedDevice to "${result.device.name} (${result.device.id})",
                        s.testRowLiveSignal to "${result.device.filteredRssi} dBm",
                        s.testRowThreshold to s.testThresholdShortFmt(result.rule.enterRssiThreshold, diff)
                    ),
                    tipText = s.testWeakTipFmt(suggestedThreshold),
                    tipColor = SamsungBlue,
                    primaryButtonText = s.testWeakLowerBtnFmt(suggestedThreshold),
                    primaryButtonColor = SamsungBlue,
                    onPrimaryClick = {
                        val updated = result.rule.copy(
                            enterRssiThreshold = suggestedThreshold,
                            lastTriggeredAt = 0L
                        )
                        SignalStarApp.instance.detectionEngine.resetRuleState(updated.id)
                        CoroutineScope(Dispatchers.IO).launch {
                            ruleRepository.updateRule(updated)
                        }
                        Toast.makeText(
                            context,
                            s.testWeakAdjustedToastFmt(suggestedThreshold),
                            Toast.LENGTH_SHORT
                        ).show()
                        testResult = null
                    },
                    secondaryButtonText = s.testMockBtn,
                    onSecondaryClick = {
                        SignalStarApp.instance.notificationBridge.sendTriggerNotification(
                            rule = result.rule,
                            detectedRssi = result.device.filteredRssi,
                            autoDismissSeconds = 15
                        )
                        Toast.makeText(context, s.testWeakMockSentToast, Toast.LENGTH_SHORT).show()
                        testResult = null
                    },
                    dismissButtonText = s.close,
                    onDismiss = { testResult = null }
                )
            }

            is RuleHardwareTestResult.NotFound -> {
                val matchDesc = if (result.rule.bindMac && !result.rule.macAddress.isNullOrBlank()) {
                    s.testNotFoundBoundMacFmt(result.rule.macAddress)
                } else {
                    result.rule.matchPattern.ifBlank { s.testNotFoundNoPattern }
                }
                OneUiTestResultDialog(
                    icon = Icons.Rounded.SearchOff,
                    iconTint = SamsungBlue,
                    title = s.testNotFoundTitle,
                    subtitle = s.testNotFoundSubtitle,
                    rows = listOf(
                        s.testNotFoundRowType to if (result.rule.signalType == SignalType.BLE) s.signalTypeBtFull else s.signalTypeWifiHotspot,
                        s.testNotFoundRowCondition to matchDesc,
                        s.testNotFoundRowThreshold to "≥ ${result.rule.enterRssiThreshold} dBm"
                    ),
                    tipText = s.testNotFoundTip,
                    tipColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    primaryButtonText = s.testNotFoundMockBtn,
                    primaryButtonColor = SamsungBlue,
                    onPrimaryClick = {
                        SignalStarApp.instance.notificationBridge.sendTriggerNotification(
                            rule = result.rule,
                            detectedRssi = result.rule.enterRssiThreshold,
                            autoDismissSeconds = 15
                        )
                        Toast.makeText(
                            context,
                            s.testNotFoundMockSentToastFmt(result.rule.triggerTag),
                            Toast.LENGTH_SHORT
                        ).show()
                        testResult = null
                    },
                    dismissButtonText = s.close,
                    onDismiss = { testResult = null }
                )
            }
        }
    }
}

@Composable
private fun OneUiTestResultDialog(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    rows: List<Pair<String, String>>,
    tipText: String,
    tipColor: Color,
    primaryButtonText: String,
    primaryButtonColor: Color,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // 顶部标题与图标区（与新建/编辑规则弹窗保持 1:1 Samsung One UI 视觉风格）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 参数诊断卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rows.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = if (value.contains(":")) FontFamily.Monospace else FontFamily.Default,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 提示条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tipColor.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "💡 $tipText",
                        style = MaterialTheme.typography.bodySmall,
                        color = tipColor
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部按钮区
                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryButtonColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(primaryButtonText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (dismissButtonText != null) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(dismissButtonText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = onSecondaryClick,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SamsungBlue.copy(alpha = 0.14f),
                                contentColor = SamsungBlue
                            )
                        ) {
                            Text(secondaryButtonText, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                } else if (dismissButtonText != null) {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryButtonColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(primaryButtonText, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(dismissButtonText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                } else {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryButtonColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(primaryButtonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleItemCard(
    rule: SignalRule,
    isTesting: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val s = LocalStrings.current
    OneUiCard(onClick = onClick) {
        val contentAlpha = if (rule.enabled) 1f else 0.5f
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.alpha(contentAlpha)) {
                SignalTypeIcon(type = rule.signalType)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha)
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val macArg = if (
                    rule.bindMac &&
                    !rule.macAddress.isNullOrBlank() &&
                    !rule.matchPattern.equals(rule.macAddress, ignoreCase = true)
                ) rule.macAddress else null
                val typeText = if (rule.signalType == SignalType.BLE) s.signalTypeBt else s.signalTypeWifi
                val patternText = rule.matchPattern.ifBlank { s.rulesByMac }
                Text(
                    text = s.rulesMatchFmt(patternText, typeText, macArg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (rule.triggerOnLeave) {
                        val effectiveLeave = rule.leaveTag.trim().ifEmpty { "${s.rulesLeaveDefaultPrefix}${rule.triggerTag}" }
                        s.rulesTagWithLeaveFmt(rule.triggerTag, effectiveLeave)
                    } else {
                        s.rulesTagOnlyFmt(rule.triggerTag)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SamsungBlue,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OneUiSwitch(
                checked = rule.enabled,
                onCheckedChange = onToggle
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lastTriggerText = if (rule.lastTriggeredAt > 0) {
                s.rulesLastTriggeredFmt(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(rule.lastTriggeredAt)))
            } else {
                s.rulesNeverTriggered
            }
            Text(
                text = s.rulesThresholdSummaryFmt(rule.enterRssiThreshold, lastTriggerText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SamsungBlue.copy(alpha = 0.12f))
                        .clickable(enabled = !isTesting) { onTest() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = SamsungBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = s.rulesScanning,
                                color = SamsungBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = s.rulesTestDesc,
                                tint = SamsungBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = s.rulesTestBtn,
                                color = SamsungBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = s.rulesEditDesc,
                        tint = SamsungBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = s.rulesDeleteDesc,
                        tint = SignalRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleBackupDialog(
    rulesCount: Int,
    onExportFile: () -> Unit,
    onImportFile: () -> Unit,
    onExportCopy: () -> Unit,
    onExportShare: () -> Unit,
    onImportJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    var importText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SamsungBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ImportExport, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.backupDialogTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = s.backupDialogSubtitle(rulesCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 主操作：原生 .json 文件导出与导入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExportFile,
                        enabled = rulesCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SamsungBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.backupExportJson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onImportFile,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.backupImportJson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = s.backupOrTextSection,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExportCopy,
                        enabled = rulesCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SamsungBlue.copy(alpha = 0.12f),
                            contentColor = SamsungBlue
                        )
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.backupCopyText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportShare,
                        enabled = rulesCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.backupShareText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            if (clipText.isNotBlank()) {
                                importText = clipText
                            } else {
                                Toast.makeText(context, s.rulesClipboardEmptyToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(s.backupPasteClipboard, fontSize = 12.sp, color = SamsungBlue, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("JSON [...] ", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(s.close, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onImportJson(importText) },
                        enabled = importText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SamsungBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text(s.backupImportTextBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
