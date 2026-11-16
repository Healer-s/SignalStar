package com.clean.signalstar.core.battery

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.clean.signalstar.ui.i18n.activeAppStrings

object PowerHelper {
    private const val TAG = "PowerHelper"
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 判断当前是否已被允许忽略电池优化（即获得无限制后台运行权限）
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 弹出系统原生“忽略电池优化”申请对话框或跳转设置页
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName

        // 统一直接打开本应用的系统详情页 (各大品牌 100% 成功唤起，内含“电池/省电策略”选项)
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.widget.Toast.makeText(context, activeAppStrings.batteryUnrestrictedGuideToast, android.widget.Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_APPLICATION_DETAILS_SETTINGS failed", e)
        }

        // 兜底尝试原生电池优化页面
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS failed", e)
        }
    }

    /**
     * 适配各大国产厂商与三星的自启动/后台电池管理页
     */
    private fun tryOpenManufacturerPowerSettings(context: Context): Boolean {
        val intentList = listOf(
            // 小米 (MIUI / HyperOS)
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
            // 华为 / 荣耀
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            // OPPO / 一加 (ColorOS)
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.PowerConsumptionActivity")),
            // vivo / iQOO (OriginOS / FuntouchOS)
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // 三星 (One UI)
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
        )

        for (intent in intentList) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // 当前手机不是该品牌或该页面不存在，继续尝试下一个
            }
        }
        return false
    }

    /**
     * 打开应用详情设置页 (供用户进入“电池 ➔ 无限制/未受限”)
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open app settings", e)
        }
    }

    /**
     * 安全持有部分唤醒锁 (PARTIAL_WAKE_LOCK)
     */
    @SuppressLint("WakelockTimeout")
    @Synchronized
    fun acquireWakeLock(context: Context, tag: String = "SignalStar:RadarLock") {
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10 * 60 * 1000L)
                Log.d(TAG, "WakeLock acquired: $tag")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    /**
     * 安全释放唤醒锁
     */
    @Synchronized
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
}
