package com.clean.signalstar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clean.signalstar.SignalStarApp
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.MatchType
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.ui.i18n.LocalStrings
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen

/**
 * 从扫描雷达中一键提取设备并创建规则
 */
@Composable
fun DeviceRuleCreationDialog(
    device: DiscoveredDevice,
    onDismiss: () -> Unit,
    onConfirm: (SignalRule) -> Unit
) {
    val s = LocalStrings.current
    val isUnnamed = device.name.isBlank() ||
        device.name.startsWith("BLE-") ||
        device.name.startsWith("<") ||
        device.name == "未知蓝牙设备" ||
        device.name == "隐藏网络"
    var ruleName by remember {
        mutableStateOf(
            if (isUnnamed) s.editCreateFromDeviceDefaultName(device.id.takeLast(5))
            else s.editCreateFromDeviceNamed(device.name)
        )
    }
    var pattern by remember {
        mutableStateOf(
            if (device.beaconInfo != null) device.beaconInfo.uuid
            else if (isUnnamed) device.id
            else device.name
        )
    }
    // 若设备无名称或为通用名，默认自动开启 MAC 绑定；否则预填 MAC 供用户按需一键开启
    var bindMac by remember { mutableStateOf(isUnnamed) }
    var macAddress by remember { mutableStateOf(device.id) }
    var triggerTag by remember {
        mutableStateOf(
            if (isUnnamed) s.editCreateFromDeviceDefaultTag(device.id.replace(":", "").takeLast(4))
            else device.name.replace(" ", "_").take(16)
        )
    }
    var enterThreshold by remember { mutableStateOf((device.filteredRssi - 3).coerceIn(-95, -50)) }
    var triggerOnLeave by remember { mutableStateOf(false) }
    var leaveTag by remember { mutableStateOf("") }

    BaseRuleEditDialog(
        title = s.editCreateFromDeviceTitle,
        subtitle = "${if (device.type == SignalType.BLE) s.signalTypeBt else s.signalTypeWifi}: ${device.name} (${device.id})",
        icon = if (device.type == SignalType.BLE) Icons.Rounded.Bluetooth else Icons.Rounded.Wifi,
        iconTint = if (device.type == SignalType.BLE) SamsungBlue else SignalGreen,
        ruleName = ruleName,
        onRuleNameChange = { ruleName = it },
        selectedSignalType = device.type,
        onSignalTypeChange = null, // 从扫描提取时锁定类型
        matchPattern = pattern,
        matchPatternLabel = if (device.type == SignalType.BLE) s.editBtPrefixLabel else s.editWifiSsidLabel,
        onMatchPatternChange = { pattern = it },
        bindMac = bindMac,
        onBindMacChange = { bindMac = it },
        macAddress = macAddress,
        onMacAddressChange = { macAddress = it },
        triggerTag = triggerTag,
        onTriggerTagChange = { triggerTag = it },
        triggerOnLeave = triggerOnLeave,
        onTriggerOnLeaveChange = { triggerOnLeave = it },
        leaveTag = leaveTag,
        onLeaveTagChange = { leaveTag = it },
        currentRssi = device.filteredRssi,
        threshold = enterThreshold,
        onThresholdChange = { enterThreshold = it },
        onDismiss = onDismiss,
        onConfirm = {
            val matchType = if (device.type == SignalType.BLE) {
                if (device.beaconInfo != null) MatchType.IBEACON
                else if (bindMac && isUnnamed) MatchType.MAC_EXACT
                else MatchType.NAME_PREFIX
            } else {
                if (bindMac && isUnnamed) MatchType.WIFI_BSSID else MatchType.WIFI_SSID
            }

            val finalTriggerTag = triggerTag.ifBlank { s.editFallbackTriggerTag }
            val rule = SignalRule(
                name = ruleName.ifBlank { device.name },
                signalType = device.type,
                matchType = matchType,
                matchPattern = pattern.trim(),
                bindMac = bindMac,
                macAddress = macAddress.trim().ifBlank { null },
                enterRssiThreshold = enterThreshold,
                exitRssiThreshold = enterThreshold - 15,
                triggerTag = finalTriggerTag,
                triggerOnLeave = triggerOnLeave,
                leaveTag = leaveTag.trim()
            )
            onConfirm(rule)
        }
    )
}

/**
 * 手动新建感知规则（支持自由选择 蓝牙 或 Wi-Fi 网络，以及可选绑定 MAC 地址）
 */
@Composable
fun ManualRuleCreationDialog(
    onDismiss: () -> Unit,
    onConfirm: (SignalRule) -> Unit
) {
    val s = LocalStrings.current
    var signalType by remember { mutableStateOf(SignalType.BLE) }
    var ruleName by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var bindMac by remember { mutableStateOf(false) }
    var macAddress by remember { mutableStateOf("") }
    var triggerTag by remember { mutableStateOf(s.editNewRuleDefaultTagBle) }
    var triggerOnLeave by remember { mutableStateOf(false) }
    var leaveTag by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(-72) }

    BaseRuleEditDialog(
        title = s.editNewRuleTitle,
        subtitle = s.editNewRuleSubtitle,
        icon = Icons.Rounded.Sensors,
        iconTint = if (signalType == SignalType.BLE) SamsungBlue else SignalGreen,
        ruleName = ruleName,
        onRuleNameChange = { ruleName = it },
        selectedSignalType = signalType,
        onSignalTypeChange = { newType ->
            signalType = newType
            if (newType == SignalType.WIFI) {
                triggerTag = s.editNewRuleDefaultTagWifi
                threshold = -75
            } else {
                triggerTag = s.editNewRuleDefaultTagBle
                threshold = -72
            }
        },
        matchPattern = pattern,
        matchPatternLabel = if (signalType == SignalType.BLE) s.editBtPrefixOptionalLabel else s.editWifiSsidOptionalLabel,
        onMatchPatternChange = { pattern = it },
        bindMac = bindMac,
        onBindMacChange = { bindMac = it },
        macAddress = macAddress,
        onMacAddressChange = { macAddress = it },
        triggerTag = triggerTag,
        onTriggerTagChange = { triggerTag = it },
        triggerOnLeave = triggerOnLeave,
        onTriggerOnLeaveChange = { triggerOnLeave = it },
        leaveTag = leaveTag,
        onLeaveTagChange = { leaveTag = it },
        currentRssi = null,
        threshold = threshold,
        onThresholdChange = { threshold = it },
        onDismiss = onDismiss,
        onConfirm = {
            val matchType = if (signalType == SignalType.BLE) MatchType.NAME_PREFIX else MatchType.WIFI_SSID
            val rule = SignalRule(
                name = ruleName.ifBlank { if (signalType == SignalType.BLE) s.editFallbackRuleNameBt else s.editFallbackRuleNameWifi },
                signalType = signalType,
                matchType = matchType,
                matchPattern = pattern.trim(),
                bindMac = bindMac,
                macAddress = macAddress.trim().ifBlank { null },
                enterRssiThreshold = threshold,
                exitRssiThreshold = threshold - 15,
                triggerTag = triggerTag.ifBlank { s.editFallbackTriggerTag },
                triggerOnLeave = triggerOnLeave,
                leaveTag = leaveTag.trim()
            )
            onConfirm(rule)
        }
    )
}

/**
 * 编辑已有的感知规则（支持实时 RSSI 数值标定、完整修改所有参数及离开触发开关）
 */
@Composable
fun EditExistingRuleDialog(
    rule: SignalRule,
    onDismiss: () -> Unit,
    onConfirm: (SignalRule) -> Unit
) {
    var ruleName by remember { mutableStateOf(rule.name) }
    var signalType by remember { mutableStateOf(rule.signalType) }
    var pattern by remember { mutableStateOf(rule.matchPattern) }
    var bindMac by remember { mutableStateOf(rule.bindMac) }
    var macAddress by remember { mutableStateOf(rule.macAddress ?: "") }
    var triggerTag by remember { mutableStateOf(rule.triggerTag) }
    var triggerOnLeave by remember { mutableStateOf(rule.triggerOnLeave) }
    var leaveTag by remember { mutableStateOf(rule.leaveTag) }
    var threshold by remember { mutableStateOf(rule.enterRssiThreshold) }

    val radarManager = SignalStarApp.instance.radarManager
    val liveDevices by radarManager.devices.collectAsState()

    // 打开编辑弹窗时开启实时辅助扫描以便边走动边标定 dBm，关闭弹窗后按 3 分钟防耗电策略自动休眠
    DisposableEffect(Unit) {
        radarManager.onRadarScreenEntered()
        if (!radarManager.isScanning.value) {
            radarManager.startScanning(
                scanBle = (rule.signalType == SignalType.BLE),
                scanWifi = (rule.signalType == SignalType.WIFI),
                lowLatency = true
            )
        }
        onDispose {
            radarManager.onRadarScreenLeft()
        }
    }

    val livePreviewRule = remember(rule, signalType, pattern, bindMac, macAddress) {
        rule.copy(
            signalType = signalType,
            matchPattern = pattern.trim(),
            bindMac = bindMac,
            macAddress = macAddress.trim().ifBlank { null }
        )
    }
    val liveRssi = remember(liveDevices, livePreviewRule) {
        liveDevices.filter { livePreviewRule.matchesDevice(it) }.maxOfOrNull { it.filteredRssi }
    }

    val s = LocalStrings.current
    BaseRuleEditDialog(
        title = s.editEditRuleTitle,
        subtitle = s.editEditRuleSubtitle,
        icon = Icons.Rounded.Edit,
        iconTint = if (signalType == SignalType.BLE) SamsungBlue else SignalGreen,
        ruleName = ruleName,
        onRuleNameChange = { ruleName = it },
        selectedSignalType = signalType,
        onSignalTypeChange = { signalType = it },
        matchPattern = pattern,
        matchPatternLabel = if (signalType == SignalType.BLE) s.editBtPrefixShortLabel else s.editWifiSsidLabel,
        onMatchPatternChange = { pattern = it },
        bindMac = bindMac,
        onBindMacChange = { bindMac = it },
        macAddress = macAddress,
        onMacAddressChange = { macAddress = it },
        triggerTag = triggerTag,
        onTriggerTagChange = { triggerTag = it },
        triggerOnLeave = triggerOnLeave,
        onTriggerOnLeaveChange = { triggerOnLeave = it },
        leaveTag = leaveTag,
        onLeaveTagChange = { leaveTag = it },
        currentRssi = liveRssi,
        threshold = threshold,
        onThresholdChange = { threshold = it },
        onDismiss = onDismiss,
        onConfirm = {
            val updated = rule.copy(
                name = ruleName.ifBlank { rule.name },
                signalType = signalType,
                matchPattern = pattern.trim(),
                bindMac = bindMac,
                macAddress = macAddress.trim().ifBlank { null },
                enterRssiThreshold = threshold,
                exitRssiThreshold = threshold - 15,
                triggerTag = triggerTag.ifBlank { s.editFallbackTriggerTag },
                triggerOnLeave = triggerOnLeave,
                leaveTag = leaveTag.trim()
            )
            onConfirm(updated)
        }
    )
}

@Composable
private fun BaseRuleEditDialog(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    ruleName: String,
    onRuleNameChange: (String) -> Unit,
    selectedSignalType: SignalType,
    onSignalTypeChange: ((SignalType) -> Unit)?,
    matchPattern: String,
    matchPatternLabel: String,
    onMatchPatternChange: (String) -> Unit,
    bindMac: Boolean,
    onBindMacChange: (Boolean) -> Unit,
    macAddress: String,
    onMacAddressChange: (String) -> Unit,
    triggerTag: String,
    onTriggerTagChange: (String) -> Unit,
    triggerOnLeave: Boolean,
    onTriggerOnLeaveChange: (Boolean) -> Unit,
    leaveTag: String,
    onLeaveTagChange: (String) -> Unit,
    currentRssi: Int?,
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val s = LocalStrings.current
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
                // 顶部标题与图标区
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
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 信号类型切换 Chip (BLE 蓝牙 vs Wi-Fi)
                if (onSignalTypeChange != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = selectedSignalType == SignalType.BLE,
                            onClick = { onSignalTypeChange(SignalType.BLE) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(s.signalTypeBtFull) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamsungBlue,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = selectedSignalType == SignalType.WIFI,
                            onClick = { onSignalTypeChange(SignalType.WIFI) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(s.signalTypeWifi) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SignalGreen,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 输入框 1：规则名称
                OneUiStyledTextField(
                    value = ruleName,
                    onValueChange = onRuleNameChange,
                    label = s.editRuleNameLabel,
                    placeholder = if (selectedSignalType == SignalType.BLE) s.editRuleNamePlaceholderBt else s.editRuleNamePlaceholderWifi,
                    leadingIcon = Icons.Rounded.NearMe
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 输入框 2：匹配目标名称
                OneUiStyledTextField(
                    value = matchPattern,
                    onValueChange = onMatchPatternChange,
                    label = matchPatternLabel,
                    placeholder = if (selectedSignalType == SignalType.BLE) "e.g. Gate_BLE" else "e.g. Home-5G",
                    leadingIcon = Icons.Rounded.Sensors
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 开关 + 输入框：限定物理 MAC / BSSID 地址（解决同名设备冲突）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedSignalType == SignalType.BLE) s.editBindMacTitleBt else s.editBindMacTitleWifi,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (bindMac) s.editBindMacDescOn else s.editBindMacDescOff,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        OneUiSwitch(
                            checked = bindMac,
                            onCheckedChange = onBindMacChange
                        )
                    }

                    AnimatedVisibility(visible = bindMac) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OneUiStyledTextField(
                                value = macAddress,
                                onValueChange = onMacAddressChange,
                                label = if (selectedSignalType == SignalType.BLE) s.editTargetMacLabelBt else s.editTargetMacLabelWifi,
                                placeholder = "AA:BB:CC:11:22:33",
                                leadingIcon = Icons.Rounded.Fingerprint
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 输入框 3：日常程序特征标签
                OneUiStyledTextField(
                    value = triggerTag,
                    onValueChange = onTriggerTagChange,
                    label = s.editTriggerTagLabel,
                    placeholder = s.editTriggerTagPlaceholder,
                    leadingIcon = Icons.Rounded.Label
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 信号门限可视化滑块卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.editTriggerRssiTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val distanceHint = when {
                            threshold >= -65 -> s.editDistanceClose
                            threshold >= -78 -> s.editDistanceNear
                            else -> s.editDistanceFar
                        }
                        Text(
                            text = "$threshold dBm · $distanceHint",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen
                        )
                    }

                    if (currentRssi != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.editLiveRssiFmt(currentRssi),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen
                            )
                            val suggested = (currentRssi - 2).coerceIn(-95, -45)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background((if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen).copy(alpha = 0.14f))
                                    .clickable { onThresholdChange(suggested) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = s.editSetSuggestedFmt(suggested),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen
                                )
                            }
                        }
                    }

                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { onThresholdChange(it.toInt()) },
                        valueRange = -95f..-45f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen,
                            activeTrackColor = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 可选「离开信号范围」也触发通知（[Leave: 标签]）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.editLeaveToggleTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.editLeaveToggleDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        OneUiSwitch(
                            checked = triggerOnLeave,
                            onCheckedChange = onTriggerOnLeaveChange
                        )
                    }

                    AnimatedVisibility(visible = triggerOnLeave) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OneUiStyledTextField(
                                value = leaveTag,
                                onValueChange = onLeaveTagChange,
                                label = s.editLeaveTagLabel,
                                placeholder = s.editLeaveTagPlaceholderFmt(triggerTag.ifBlank { "Trigger" }),
                                leadingIcon = Icons.Rounded.Label
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部双按钮：对称平衡
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        Text(s.cancel, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSignalType == SignalType.BLE) SamsungBlue else SignalGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(s.editSaveRuleBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OneUiStyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true
    )
}
