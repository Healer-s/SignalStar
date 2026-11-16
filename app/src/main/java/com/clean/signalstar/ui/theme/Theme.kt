package com.clean.signalstar.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBlue,
    onPrimary = OneUiLightSurface,
    primaryContainer = SamsungBlueDark,
    background = OneUiDarkBackground,
    surface = OneUiDarkSurface,
    surfaceVariant = OneUiDarkCard,
    outline = OneUiDarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SamsungBlue,
    onPrimary = OneUiLightSurface,
    primaryContainer = SamsungBlueLight,
    background = OneUiLightBackground,
    surface = OneUiLightSurface,
    surfaceVariant = OneUiLightCard,
    outline = OneUiLightCardBorder
)

val OneUiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),    // One UI 标志性大圆角卡片
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SignalStarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = OneUiShapes,
        content = content
    )
}
