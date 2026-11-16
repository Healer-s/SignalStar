package com.clean.signalstar.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clean.signalstar.core.data.LogRepository
import com.clean.signalstar.core.model.LogEntry
import com.clean.signalstar.ui.components.OneUiCard
import com.clean.signalstar.ui.components.OneUiSwitch
import com.clean.signalstar.ui.i18n.LocalStrings
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen
import com.clean.signalstar.ui.theme.SignalRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val logs by LogRepository.logs.collectAsState()
    val triggerHistory by LogRepository.triggerHistory.collectAsState()
    val runtimeLogEnabled by LogRepository.runtimeLogEnabled.collectAsState()

    // 0 = 触发历史记录 (默认展示，持久化保存), 1 = 全部运行日志
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val activeList = if (selectedTab == 0) triggerHistory else logs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = s.backDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = s.diagTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = s.diagSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            IconButton(
                onClick = { showClearConfirmDialog = true },
                enabled = activeList.isNotEmpty(),
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (activeList.isNotEmpty()) SignalRed.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = s.diagClearDesc,
                    tint = if (activeList.isNotEmpty()) SignalRed
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Samsung One UI 风格双分段切换栏：触发记录 vs 运行日志
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 0) SamsungBlue else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.diagTabTriggersFmt(triggerHistory.size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 1) SamsungBlue else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.diagTabLogsFmt(logs.size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 当切换到「运行日志」选项卡时，展示调试日志抓取开关（默认关闭，0 功耗开销）
        if (selectedTab == 1) {
            OneUiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.diagDebugSwitchTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (runtimeLogEnabled) s.diagDebugSwitchDescOn else s.diagDebugSwitchDescOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (runtimeLogEnabled) SamsungBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    OneUiSwitch(
                        checked = runtimeLogEnabled,
                        onCheckedChange = { enabled ->
                            LogRepository.setRuntimeLogEnabled(enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // 操作栏：一键复制 / 一键分享导出
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val text = LogRepository.exportAsText(onlyTriggers = (selectedTab == 0))
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SignalStar Logs", text))
                    Toast.makeText(
                        context,
                        if (selectedTab == 0) s.diagCopiedTriggersToast else s.diagCopiedLogsToast,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (selectedTab == 0) s.diagCopyTriggersBtn else s.diagCopyLogsBtn)
            }

            Button(
                onClick = {
                    val text = LogRepository.exportAsText(onlyTriggers = (selectedTab == 0))
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, s.diagShareChooserTitle))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.diagShareBtn)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                selectedTab == 0 -> s.diagEmptyTriggers
                                !runtimeLogEnabled -> s.diagEmptyLogsDisabled
                                else -> s.diagEmptyLogsEnabled
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(activeList, key = { it.id }) { log ->
                    LogCard(log = log, showFullDate = (selectedTab == 0 || log.isTrigger), triggeredLabel = s.diagCardRuleTriggered)
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = if (selectedTab == 0) s.diagClearConfirmTitleTriggers else s.diagClearConfirmTitleLogs,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = s.diagClearConfirmMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        if (selectedTab == 0) {
                            LogRepository.clearTriggerHistory()
                            Toast.makeText(context, s.diagClearedTriggersToast, Toast.LENGTH_SHORT).show()
                        } else {
                            LogRepository.clearRuntimeLogs()
                            Toast.makeText(context, s.diagClearedLogsToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalRed)
                ) {
                    Text(s.diagClearConfirmBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(s.cancel)
                }
            }
        )
    }
}

@Composable
private fun LogCard(log: LogEntry, showFullDate: Boolean = false, triggeredLabel: String = "🎯 Rule Triggered") {
    val pattern = if (showFullDate) "MM-dd HH:mm:ss" else "HH:mm:ss.SSS"
    val timeStr = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(log.timestamp))

    OneUiCard(
        backgroundColor = if (log.isTrigger) SignalGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
        borderColor = if (log.isTrigger) SignalGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (log.isTrigger) triggeredLabel else "[${log.tag}]",
                fontWeight = FontWeight.Bold,
                color = if (log.isTrigger) SignalGreen else SamsungBlue,
                fontSize = 12.sp
            )
            Text(
                text = timeStr,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (log.isTrigger) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = log.message,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
