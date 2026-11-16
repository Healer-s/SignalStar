package com.clean.signalstar.ui.screens.radar

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clean.signalstar.core.data.RuleRepository
import com.clean.signalstar.core.model.DiscoveredDevice
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.core.scanner.RadarManager
import com.clean.signalstar.core.service.ServiceRunMode
import com.clean.signalstar.core.service.SignalRadarForegroundService
import com.clean.signalstar.ui.components.OneUiCard
import com.clean.signalstar.ui.components.SignalStrengthBar
import com.clean.signalstar.ui.components.SignalTypeIcon
import com.clean.signalstar.ui.i18n.EnStrings
import com.clean.signalstar.ui.i18n.LocalStrings
import com.clean.signalstar.ui.i18n.ZhStrings
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen
import com.clean.signalstar.ui.theme.SignalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class RadarFilter {
    ALL,
    RULED,
    BLE,
    WIFI
}

@Composable
fun RadarScreen(
    radarManager: RadarManager,
    ruleRepository: RuleRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = LocalStrings.current
    val devices by radarManager.devices.collectAsState()
    val rules by ruleRepository.rulesFlow.collectAsState(initial = emptyList())
    val isScanning by radarManager.isScanning.collectAsState()
    val serviceMode by SignalRadarForegroundService.currentMode.collectAsState()
    val isDaemonRunning = serviceMode != ServiceRunMode.IDLE

    var selectedFilter by remember { mutableStateOf(RadarFilter.ALL) }
    var deviceToCreateRule by remember { mutableStateOf<DiscoveredDevice?>(null) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    // 实时检查系统蓝牙、定位开关与关键权限：
    // Android 12+ 蓝牙扫描已通过 neverForLocation 解耦，仅在用户主动切换到 Wi-Fi 扫描/筛选时才要求定位权限与定位开关
    fun checkHardwarePrerequisites(filter: RadarFilter = selectedFilter): Pair<String?, Intent?> {
        val needLocationForScan = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) || (filter == RadarFilter.WIFI)
        val hasBlePerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasBlePerm || (needLocationForScan && !hasLocationPermission())) {
            return s.radarPermMissing to Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
        }

        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (btAdapter != null && !btAdapter.isEnabled && filter != RadarFilter.WIFI) {
            return s.radarBtOff to Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

        if (needLocationForScan) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm?.isLocationEnabled == true
            } else {
                @Suppress("DEPRECATION")
                lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }
            if (!isLocationEnabled) {
                return s.radarLocOff to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
        }

        return null to null
    }

    var prerequisiteIssue by remember { mutableStateOf(checkHardwarePrerequisites()) }

    val wifiLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        prerequisiteIssue = checkHardwarePrerequisites(selectedFilter)
        if (hasLocationPermission() && isScanning && selectedFilter == RadarFilter.WIFI) {
            radarManager.setRadarWifiTabActive(true)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                prerequisiteIssue = checkHardwarePrerequisites(selectedFilter)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 切回页面时按当前是否停留在 Wi-Fi 页签决定是否拉起临时 Wi-Fi；切出页面立即关闭临时 Wi-Fi，空闲 30 秒自动停止扫描
    DisposableEffect(Unit) {
        radarManager.onRadarScreenEntered(
            scanWifiOnRadar = (selectedFilter == RadarFilter.WIFI && hasLocationPermission())
        )
        onDispose {
            radarManager.onRadarScreenLeft()
        }
    }

    // 计算每台扫描到的设备是否已经绑定过规则
    val matchedRulesByDeviceId = remember(devices, rules) {
        devices.associate { device ->
            device.id to rules.filter { it.matchesDevice(device) }
        }
    }

    val ruledCount = remember(devices, matchedRulesByDeviceId) {
        devices.count { matchedRulesByDeviceId[it.id]?.isNotEmpty() == true }
    }
    val bleCount = remember(devices) { devices.count { it.type == SignalType.BLE } }
    val wifiCount = remember(devices) { devices.count { it.type == SignalType.WIFI } }

    // 过滤并自动将「已设规则」的设备置顶，其次按信号强弱降序排列
    val filteredDevices = remember(devices, selectedFilter, matchedRulesByDeviceId) {
        devices.filter { device ->
            when (selectedFilter) {
                RadarFilter.ALL -> true
                RadarFilter.RULED -> matchedRulesByDeviceId[device.id]?.isNotEmpty() == true
                RadarFilter.BLE -> device.type == SignalType.BLE
                RadarFilter.WIFI -> device.type == SignalType.WIFI
            }
        }.sortedWith(
            compareByDescending<DiscoveredDevice> { matchedRulesByDeviceId[it.id]?.isNotEmpty() == true }
                .thenByDescending { it.filteredRssi }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // 顶部留白与标题
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.radarTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when {
                        isDaemonRunning -> s.radarDaemonSubtitle
                        isScanning -> s.radarScanningSubtitle
                        else -> s.radarIdleSubtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isScanning || isDaemonRunning) SamsungBlue else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDaemonRunning) {
                // 后台 24h / 敏捷守护运行中：不显示误导性的红色停止按钮，改为清晰的守护状态胶囊
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SignalGreen.copy(alpha = 0.14f))
                        .clickable {
                            Toast.makeText(
                                context,
                                s.radarDaemonStopHintToast,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SignalGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (serviceMode == ServiceRunMode.PERMANENT_24H) s.daemon24hBadge else s.daemonAgileBadge,
                        color = SignalGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (isScanning) {
                            radarManager.stopScanning()
                            radarManager.clearDevices()
                        } else {
                            val canScanWifi = (selectedFilter == RadarFilter.WIFI) && hasLocationPermission()
                            if (selectedFilter == RadarFilter.WIFI && !hasLocationPermission()) {
                                wifiLocationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            radarManager.startScanning(
                                scanBle = true,
                                scanWifi = canScanWifi,
                                lowLatency = true
                            )
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isScanning) Color(0xFFEF4444).copy(alpha = 0.15f) else SamsungBlue.copy(alpha = 0.15f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isScanning) s.scanStopDesc else s.scanStartDesc,
                        tint = if (isScanning) Color(0xFFEF4444) else SamsungBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 系统前置条件异常提示条（权限/蓝牙/定位总开关未开时第一时间提醒普通用户）
        val (issueText, fixIntent) = prerequisiteIssue
        if (issueText != null) {
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
                        text = issueText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (fixIntent != null) {
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) { }
                            }
                        ) {
                            Text(s.radarGoEnableBtn, color = SamsungBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Samsung One UI 四分段筛选胶囊栏（100% 适配屏幕宽度，绝不溢出裁切）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val filterTabs = listOf(
                RadarFilter.ALL to "${s.filterAll}(${devices.size})",
                RadarFilter.RULED to "${s.filterRuled}($ruledCount)",
                RadarFilter.BLE to "${s.filterBt}($bleCount)",
                RadarFilter.WIFI to "${s.filterWifi}($wifiCount)"
            )
            filterTabs.forEach { (filter, label) ->
                val selected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) SamsungBlue else Color.Transparent)
                        .clickable {
                            selectedFilter = filter
                            val isWifiTab = (filter == RadarFilter.WIFI)
                            if (isWifiTab && !hasLocationPermission()) {
                                wifiLocationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            radarManager.setRadarWifiTabActive(isWifiTab && hasLocationPermission())
                            prerequisiteIssue = checkHardwarePrerequisites(filter)
                        }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 设备列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredDevices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 50.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    if (isScanning) SamsungBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .clickable {
                                    if (isScanning) {
                                        if (com.clean.signalstar.core.service.SignalRadarForegroundService.currentMode.value != com.clean.signalstar.core.service.ServiceRunMode.IDLE) {
                                            radarManager.bleScanner.startScan(lowLatency = false)
                                            Toast.makeText(context, s.radarDaemonSwitchedBackToast, Toast.LENGTH_SHORT).show()
                                        } else {
                                            radarManager.stopScanning()
                                            radarManager.clearDevices()
                                        }
                                    } else {
                                        radarManager.startScanning(lowLatency = true)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Rounded.Radar else Icons.Rounded.PlayArrow,
                                contentDescription = if (isScanning) s.scanStopDesc else s.scanStartDesc,
                                tint = if (isScanning) SamsungBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = when {
                                !isScanning -> s.radarEmptyIdle
                                selectedFilter == RadarFilter.RULED -> s.radarEmptyRuled
                                else -> s.radarEmptyScanning
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = when {
                                !isScanning -> s.radarEmptyIdleDesc
                                selectedFilter == RadarFilter.RULED -> s.radarEmptyRuledDesc
                                else -> s.radarEmptyScanningDesc
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        if (!isScanning) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { radarManager.startScanning(lowLatency = true) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SamsungBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.radarStartBtn, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredDevices, key = { it.id }) { device ->
                    DeviceRadarCard(
                        device = device,
                        matchedRules = matchedRulesByDeviceId[device.id].orEmpty(),
                        onCreateRuleClick = {
                            deviceToCreateRule = device
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // 点击一键生成规则的高质感 Dialog
    deviceToCreateRule?.let { device ->
        com.clean.signalstar.ui.components.DeviceRuleCreationDialog(
            device = device,
            onDismiss = { deviceToCreateRule = null },
            onConfirm = { rule ->
                CoroutineScope(Dispatchers.IO).launch {
                    ruleRepository.addRule(rule)
                }
                deviceToCreateRule = null
            }
        )
    }
}

@Composable
private fun DeviceRadarCard(
    device: DiscoveredDevice,
    matchedRules: List<SignalRule>,
    onCreateRuleClick: () -> Unit
) {
    val hasRule = matchedRules.isNotEmpty()
    OneUiCard(
        borderColor = if (hasRule) SamsungBlue.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalTypeIcon(type = device.type)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                val sCard = LocalStrings.current
                val displayName = when (device.name) {
                    ZhStrings.wifiHiddenSsid, EnStrings.wifiHiddenSsid -> sCard.hiddenSsid
                    ZhStrings.wifiCurrentHotspot, EnStrings.wifiCurrentHotspot -> sCard.wifiCurrentHotspot
                    else -> device.name
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.id,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                if (hasRule) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SamsungBlue.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = SamsungBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val ruleSummary = sCard.radarRuleSummary(matchedRules.size, matchedRules.first().name)
                        Text(
                            text = ruleSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = SamsungBlue,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (device.beaconInfo != null) {
                    Text(
                        text = "iBeacon: ${device.beaconInfo.uuid.take(8)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = SamsungBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalStrengthBar(rssi = device.filteredRssi)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = device.displayRssi,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onCreateRuleClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue.copy(alpha = 0.15f), contentColor = SamsungBlue),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    val sBtn = LocalStrings.current
                    Text(if (hasRule) sBtn.btnAddMoreRule else sBtn.btnSetRule, fontSize = 12.sp)
                }
            }
        }
    }
}


