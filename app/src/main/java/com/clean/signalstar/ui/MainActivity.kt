package com.clean.signalstar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.clean.signalstar.SignalStarApp
import com.clean.signalstar.core.data.SettingsRepository
import com.clean.signalstar.core.scanner.RadarManager
import com.clean.signalstar.ui.i18n.AppLanguage
import com.clean.signalstar.ui.i18n.LocalStrings
import com.clean.signalstar.ui.i18n.resolveAppStrings
import com.clean.signalstar.ui.screens.diagnostics.DiagnosticsScreen
import com.clean.signalstar.ui.screens.home.HomeScreen
import com.clean.signalstar.ui.screens.radar.RadarScreen
import com.clean.signalstar.ui.screens.routine.RoutineGuideScreen
import com.clean.signalstar.ui.screens.rules.RulesScreen
import com.clean.signalstar.ui.theme.SamsungBlue
import com.clean.signalstar.ui.theme.SignalStarTheme

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("控制", Icons.Rounded.Dashboard),
    RADAR("雷达", Icons.Rounded.Radar),
    RULES("规则", Icons.Rounded.Rule)
}

enum class SubScreen {
    ROUTINE,
    DIAGNOSTICS
}

class MainActivity : ComponentActivity() {

    private lateinit var radarManager: RadarManager
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        radarManager = SignalStarApp.instance.radarManager
        mainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            SignalStarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(
                        radarManager = radarManager,
                        viewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    radarManager: RadarManager,
    viewModel: MainViewModel
) {
    val settings by viewModel.settingsState.collectAsState()
    val appStrings = remember(settings.appLanguage) {
        resolveAppStrings(AppLanguage.fromCode(settings.appLanguage))
    }

    CompositionLocalProvider(LocalStrings provides appStrings) {
        var selectedTab by remember { mutableStateOf(MainTab.HOME) }
        var currentSubScreen by remember { mutableStateOf<SubScreen?>(null) }

        // 拦截物理/手势返回键，优先退出二级指引/日志页面
        if (currentSubScreen != null) {
            androidx.activity.compose.BackHandler {
                currentSubScreen = null
            }
        }

        // 动态权限申请
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // Android 11 及以下系统底层扫描蓝牙必须绑定定位权限；Android 12+ 已通过 neverForLocation 解耦，仅在使用 Wi-Fi 功能时按需申请
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }

        val s = LocalStrings.current

        Scaffold(
            bottomBar = {
                // 二级页面时收起底部导航栏，保证沉浸式阅读与操作空间
                if (currentSubScreen == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainTab.values().forEach { tab ->
                            val tabLabel = when (tab) {
                                MainTab.HOME -> s.tabControl
                                MainTab.RADAR -> s.tabRadar
                                MainTab.RULES -> s.tabRules
                            }
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tabLabel) },
                                label = { Text(tabLabel) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SamsungBlue,
                                    selectedTextColor = SamsungBlue,
                                    indicatorColor = SamsungBlue.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                if (currentSubScreen != null) {
                    when (currentSubScreen) {
                        SubScreen.ROUTINE -> RoutineGuideScreen(onBack = { currentSubScreen = null })
                        SubScreen.DIAGNOSTICS -> DiagnosticsScreen(onBack = { currentSubScreen = null })
                        null -> {}
                    }
                } else {
                    when (selectedTab) {
                        MainTab.HOME -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToRules = { selectedTab = MainTab.RULES },
                            onNavigateToGuide = { currentSubScreen = SubScreen.ROUTINE },
                            onNavigateToLogs = { currentSubScreen = SubScreen.DIAGNOSTICS }
                        )
                        MainTab.RADAR -> RadarScreen(
                            radarManager = radarManager,
                            ruleRepository = SignalStarApp.instance.ruleRepository
                        )
                        MainTab.RULES -> RulesScreen(
                            ruleRepository = SignalStarApp.instance.ruleRepository,
                            radarManager = radarManager
                        )
                    }
                }
            }
        }
    }
}
