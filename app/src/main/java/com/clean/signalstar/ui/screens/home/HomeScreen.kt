package com.clean.signalstar.ui.screens.home

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clean.signalstar.ui.i18n.AppLanguage
import com.clean.signalstar.ui.i18n.LocalStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clean.signalstar.R
import com.clean.signalstar.core.battery.PowerHelper
import com.clean.signalstar.core.data.AppSettings
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.service.ServiceRunMode
import com.clean.signalstar.core.service.SignalRadarForegroundService
import com.clean.signalstar.core.update.AppReleaseInfo
import com.clean.signalstar.core.update.UpdateCheckResult
import com.clean.signalstar.core.update.UpdateChecker
import com.clean.signalstar.ui.components.OneUiCard
import com.clean.signalstar.ui.components.OneUiSwitch
import com.clean.signalstar.ui.components.UpdateDialog
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen
import com.clean.signalstar.ui.theme.SignalRed
import com.clean.signalstar.ui.theme.SignalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.clean.signalstar.ui.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToRules: () -> Unit,
    onNavigateToGuide: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = LocalStrings.current

    val settings by viewModel.settingsState.collectAsState()
    val currentMode by SignalRadarForegroundService.currentMode.collectAsState()
    val remainingSeconds by SignalRadarForegroundService.remainingSeconds.collectAsState()

    var isBatteryOptimized by remember { mutableStateOf(!PowerHelper.isIgnoringBatteryOptimizations(context)) }
    var isBatteryCardDismissed by remember { mutableStateOf(false) }
    var showPermanentPromptDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateConfirmDialog by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var ignoredVersion by remember { mutableStateOf<String?>(null) }

    val packageInfo = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }
    val currentVersionName = packageInfo?.versionName ?: "1.0.0"

    fun triggerCheckUpdate(isManual: Boolean) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        CoroutineScope(Dispatchers.Main).launch {
            val result = UpdateChecker.checkUpdate(currentVersionName)
            isCheckingUpdate = false
            when (result) {
                is UpdateCheckResult.HasUpdate -> {
                    if (isManual || result.release.tagName != ignoredVersion) {
                        availableRelease = result.release
                    }
                }
                is UpdateCheckResult.AlreadyLatest -> {
                    if (isManual) {
                        Toast.makeText(context, "当前已是最新版本 (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (isManual) {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryOptimized = !PowerHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 标志性大标题留白与右上角快捷功能入口
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.homeTitle,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Text(
                        text = s.homeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showLanguageDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = s.languageTitle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SamsungBlue.copy(alpha = 0.12f))
                            .clickable { onNavigateToGuide() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = s.guideIconDesc,
                            tint = SamsungBlue,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SignalGreen.copy(alpha = 0.12f))
                            .clickable { onNavigateToLogs() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = s.logsIconDesc,
                            tint = SignalGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 核心卡片 1：强制后台 24 小时运行菜单主键
        item {
            OneUiCard(
                backgroundColor = if (settings.isPermanent24hEnabled) {
                    SamsungBlue.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                borderColor = if (settings.isPermanent24hEnabled) {
                    SamsungBlue.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (settings.isPermanent24hEnabled) SamsungBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AllInclusive,
                            contentDescription = null,
                            tint = if (settings.isPermanent24hEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.permanent24hTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.isPermanent24hEnabled) {
                                s.permanent24hDescOn
                            } else {
                                s.permanent24hDescOff
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    OneUiSwitch(
                        checked = settings.isPermanent24hEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPermanentPromptDialog = true
                            } else {
                                viewModel.setPermanent24hEnabled(false)
                            }
                        }
                    )
                }
            }
        }

        // 核心卡片 2：当前实时运行状态看板
        item {
            OneUiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusDotColor = when (currentMode) {
                        ServiceRunMode.PERMANENT_24H -> SignalGreen
                        ServiceRunMode.AGILE_TIMED -> SignalYellow
                        ServiceRunMode.IDLE -> SignalRed
                    }
                    val statusTitle = when (currentMode) {
                        ServiceRunMode.PERMANENT_24H -> s.status24hTitle
                        ServiceRunMode.AGILE_TIMED -> {
                            val mins = remainingSeconds / 60
                            val secs = remainingSeconds % 60
                            s.statusAgileActiveFmt(mins, secs)
                        }
                        ServiceRunMode.IDLE -> s.statusIdleTitle
                    }
                    val statusDesc = when (currentMode) {
                        ServiceRunMode.PERMANENT_24H -> s.status24hDesc
                        ServiceRunMode.AGILE_TIMED -> s.statusAgileDesc
                        ServiceRunMode.IDLE -> s.statusIdleDesc
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮组
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentMode == ServiceRunMode.IDLE) {
                        Button(
                            onClick = {
                                SignalRadarForegroundService.startAgile(context, settings.agileDurationMinutes)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                        ) {
                            Icon(Icons.Rounded.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.btnStartAgile)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (currentMode == ServiceRunMode.PERMANENT_24H) {
                                    viewModel.setPermanent24hEnabled(false)
                                }
                                SignalRadarForegroundService.stop(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = SignalRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.btnStopSleep, color = SignalRed)
                        }
                    }
                }
            }
        }

        // 核心卡片 3：敏捷限时模式与自动化联动配置（Samsung One UI 原生置灰锁定态：卡片不缩放、页面零跳动）
        item {
            val isLockedBy24h = settings.isPermanent24hEnabled
            val agileContentAlpha by animateFloatAsState(
                targetValue = if (isLockedBy24h) 0.38f else 1f,
                animationSpec = tween(durationMillis = 220),
                label = "AgileCardContentAlpha"
            )

            OneUiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.agileCardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(agileContentAlpha)
                    )

                    if (isLockedBy24h) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SamsungBlue.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = s.agileLockedBy24h,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SamsungBlue
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(agileContentAlpha)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = s.agileCardDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = s.agileDurationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 120, 240).forEach { minutes ->
                            FilterChip(
                                selected = settings.agileDurationMinutes == minutes,
                                onClick = {
                                    if (!isLockedBy24h) {
                                        viewModel.setAgileDurationMinutes(minutes)
                                    }
                                },
                                label = {
                                    Text(s.agileDurationUnitFmt(minutes))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SamsungBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.autoStopTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = s.autoStopDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        OneUiSwitch(
                            checked = settings.autoStopAfterTrigger,
                            enabled = !isLockedBy24h,
                            onCheckedChange = { checked ->
                                if (!isLockedBy24h) {
                                    viewModel.setAutoStopAfterTrigger(checked)
                                }
                            }
                        )
                    }
                }
            }
        }

        // 电池未受限白名单提醒 (多品牌手机防杀关键点)
        if (isBatteryOptimized && !isBatteryCardDismissed) {
            item {
                OneUiCard(
                    backgroundColor = SignalYellow.copy(alpha = 0.1f),
                    borderColor = SignalYellow.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryAlert,
                            contentDescription = null,
                            tint = SignalYellow,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.batteryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.batteryDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                PowerHelper.requestIgnoreBatteryOptimizations(context)
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = Color.Black)
                        ) {
                            Text(s.batteryGoSettings, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                isBatteryCardDismissed = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(s.batteryDismiss)
                        }
                    }
                }
            }
        }

        // 关于 SignalStar 精简入口卡片
        item {
            OneUiCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUpdateConfirmDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SamsungBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = s.aboutMenuTitle,
                                tint = SamsungBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = s.aboutMenuTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.aboutMenuSubtitleFmt(currentVersionName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 底部安全留白
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showUpdateConfirmDialog) {
        var zoomedQr by remember { mutableStateOf<Pair<String, Int>?>(null) }

        Dialog(
            onDismissRequest = { showUpdateConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 顶部 One UI 标题与版本胶囊
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SamsungBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = SamsungBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = s.aboutDialogTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SamsungBlue.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v$currentVersionName",
                                        color = SamsungBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = s.aboutDialogDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // 零网络隐私说明与官方发布源卡片
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = s.versionLine1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.versionLine2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    showUpdateConfirmDialog = false
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://github.com/Healer-s/SignalStar/releases")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, s.versionNoBrowserToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                            ) {
                                Text(s.versionGithubBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    showUpdateConfirmDialog = false
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://gitee.com/healer1012/SignalStar/releases")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, s.versionNoBrowserToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(s.versionGiteeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    // 赞赏与支持（双二维码区域）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = SignalRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = s.aboutSupportTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = s.aboutSupportSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(
                                Triple(s.aboutQrWechat, R.drawable.qr_wechat, Color(0xFF07C160)),
                                Triple(s.aboutQrAlipay, R.drawable.qr_alipay, Color(0xFF1677FF))
                            ).forEach { (label, resId, accentColor) ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { zoomedQr = label to resId }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(104.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = label,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // One UI 风格全宽关闭按钮
                    Button(
                        onClick = { showUpdateConfirmDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(s.close, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        zoomedQr?.let { (label, resId) ->
            Dialog(onDismissRequest = { zoomedQr = null }) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = label,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Button(
                            onClick = { zoomedQr = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(s.close, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    availableRelease?.let { release ->
        UpdateDialog(
            release = release,
            onDismiss = { availableRelease = null },
            onIgnoreVersion = { tag ->
                ignoredVersion = tag
                availableRelease = null
                Toast.makeText(context, "已忽略 $tag 版本的更新提醒", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPermanentPromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPermanentPromptDialog = false },
            title = {
                Text(
                    s.prompt24hTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = s.prompt24hLine1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = s.prompt24hLine2,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SamsungBlue
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPermanent24hEnabled(true)
                        showPermanentPromptDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                ) {
                    Text(s.prompt24hConfirm)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showPermanentPromptDialog = false }
                ) {
                    Text(s.prompt24hCancel)
                }
            }
        )
    }

    if (showLanguageDialog) {
        val currentLang = AppLanguage.fromCode(settings.appLanguage)
        Dialog(
            onDismissRequest = { showLanguageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SamsungBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = s.languageTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = s.languageSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val options = listOf(
                        Triple(AppLanguage.AUTO, s.langAuto, s.langAutoDesc),
                        Triple(AppLanguage.ZH, s.langZh, "Simplified Chinese"),
                        Triple(AppLanguage.EN, s.langEn, "English")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (lang, title, desc) ->
                            val selected = currentLang == lang
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) SamsungBlue.copy(alpha = 0.10f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .clickable {
                                        viewModel.setAppLanguage(lang.code)
                                        showLanguageDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        viewModel.setAppLanguage(lang.code)
                                        showLanguageDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = SamsungBlue)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) SamsungBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { showLanguageDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(s.close, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
