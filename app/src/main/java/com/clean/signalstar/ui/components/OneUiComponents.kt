package com.clean.signalstar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalGreen
import com.clean.signalstar.ui.theme.SignalRed
import com.clean.signalstar.ui.theme.SignalYellow

@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * 1:1 像素级复刻 Samsung One UI 系统设置原生开关 (取色与比例自 One UI 实机设置界面)
 * - 尺寸：轨道 42dp × 23.5dp (黄金比例 1.79:1)，圆钮恒定 19.5dp (占比 83%，关闭时不缩小！)
 * - 配色：开启态 #377AFF (One UI 原生开关蓝)，关闭态 #A3A2A7 (One UI 中性实心灰，衬托纯白圆钮)
 * - 间距：自带左侧 16dp 安全留白，彻底解决开关与左侧说明文字贴得太近的问题
 */
@Composable
fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> Color(0xFF377AFF)
            isDark -> Color(0xFF5A5A60)
            else -> Color(0xFFA3A2A7)
        },
        animationSpec = tween(durationMillis = 180),
        label = "OneUiSwitchTrackColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.5.dp else 2.dp,
        animationSpec = tween(durationMillis = 180),
        label = "OneUiSwitchThumbOffset"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(23.5.dp)
                .clip(CircleShape)
                .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled && onCheckedChange != null,
                    role = Role.Switch,
                    onClick = { onCheckedChange?.invoke(!checked) }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(19.5.dp)
                    .shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun SignalTypeIcon(
    type: SignalType,
    modifier: Modifier = Modifier
) {
    val bgColor = if (type == SignalType.BLE) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
    val iconColor = if (type == SignalType.BLE) Color(0xFF3B82F6) else Color(0xFF10B981)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (type == SignalType.BLE) Icons.Rounded.Bluetooth else Icons.Rounded.Wifi,
            contentDescription = type.name,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SignalStrengthBar(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        rssi >= -65 -> SignalGreen
        rssi >= -80 -> SignalYellow
        else -> SignalRed
    }

    val bars = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -82 -> 2
        else -> 1
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val barHeight = (i * 4).dp
            val isActive = i <= bars
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
            if (i < 4) Spacer(modifier = Modifier.width(2.dp))
        }
    }
}
