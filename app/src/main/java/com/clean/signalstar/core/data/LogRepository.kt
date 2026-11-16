package com.clean.signalstar.core.data

import android.content.Context
import android.content.SharedPreferences
import com.clean.signalstar.core.model.LogEntry
import com.clean.signalstar.ui.i18n.activeAppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private const val MAX_LOGS = 250
    private const val MAX_TRIGGER_HISTORY = 100
    private const val PREFS_NAME = "signalstar_trigger_history_prefs"
    private const val KEY_TRIGGER_HISTORY = "trigger_history_json"
    private const val KEY_RUNTIME_LOG_ENABLED = "runtime_log_enabled"

    private var prefs: SharedPreferences? = null

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _triggerHistory = MutableStateFlow<List<LogEntry>>(emptyList())
    val triggerHistory: StateFlow<List<LogEntry>> = _triggerHistory.asStateFlow()

    // 默认关闭运行调试日志抓取（0 内存分配与 0 流唤醒开销），仅在用户手动开启排查问题时抓取
    private val _runtimeLogEnabled = MutableStateFlow(false)
    val runtimeLogEnabled: StateFlow<Boolean> = _runtimeLogEnabled.asStateFlow()

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _runtimeLogEnabled.value = prefs?.getBoolean(KEY_RUNTIME_LOG_ENABLED, false) ?: false
        loadPersistedTriggers()
    }

    fun setRuntimeLogEnabled(enabled: Boolean) {
        _runtimeLogEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_RUNTIME_LOG_ENABLED, enabled)?.apply()
        if (enabled) {
            log("System", activeAppStrings.logRuntimeDebugEnabled)
        }
    }

    private fun loadPersistedTriggers() {
        val raw = prefs?.getString(KEY_TRIGGER_HISTORY, null) ?: return
        try {
            val array = JSONArray(raw)
            val loaded = mutableListOf<LogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tag = obj.optString("tag", "Trigger")
                // 过滤旧版本 Bridge 重复记录的触发通知项，保证 1 次触发只对应 1 条记录
                if (tag == "Bridge") continue
                loaded.add(
                    LogEntry(
                        id = obj.optString("id", "${obj.optLong("timestamp")}_$i"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        tag = tag,
                        message = obj.optString("message", ""),
                        isTrigger = true
                    )
                )
            }
            _triggerHistory.value = loaded
        } catch (_: Exception) {
        }
    }

    private fun savePersistedTriggers(list: List<LogEntry>) {
        val p = prefs ?: return
        try {
            val array = JSONArray()
            list.forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("tag", entry.tag)
                    put("message", entry.message)
                }
                array.put(obj)
            }
            p.edit().putString(KEY_TRIGGER_HISTORY, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun log(tag: String, message: String, isTrigger: Boolean = false) {
        // 如果不是触发记录且未开启调试运行日志抓取，直接短路返回（0 内存分配、0 功耗开销）
        if (!isTrigger && !_runtimeLogEnabled.value) return

        val entry = LogEntry(
            tag = tag,
            message = message,
            isTrigger = isTrigger
        )
        if (_runtimeLogEnabled.value || isTrigger) {
            val current = _logs.value.toMutableList()
            current.add(0, entry)
            if (current.size > MAX_LOGS) {
                current.removeAt(current.size - 1)
            }
            _logs.value = current
        }

        if (isTrigger) {
            val triggers = _triggerHistory.value.toMutableList()
            triggers.add(0, entry)
            if (triggers.size > MAX_TRIGGER_HISTORY) {
                triggers.removeAt(triggers.size - 1)
            }
            _triggerHistory.value = triggers
            savePersistedTriggers(triggers)
        }
    }

    @Synchronized
    fun clearRuntimeLogs() {
        _logs.value = emptyList()
    }

    @Synchronized
    fun clearTriggerHistory() {
        _triggerHistory.value = emptyList()
        prefs?.edit()?.remove(KEY_TRIGGER_HISTORY)?.apply()
    }

    @Synchronized
    fun clear() {
        clearRuntimeLogs()
    }

    fun exportAsText(onlyTriggers: Boolean = false): String {
        val s = activeAppStrings
        val sb = StringBuilder()
        sb.append(if (onlyTriggers) s.logExportTriggersHeader else s.logExportRuntimeHeader)
        sb.append("${s.logExportTimePrefix}${dateTimeFormat.format(Date())}\n\n")
        val targetList = if (onlyTriggers) _triggerHistory.value else _logs.value
        targetList.reversed().forEach { entry ->
            val time = dateTimeFormat.format(Date(entry.timestamp))
            val prefix = if (entry.isTrigger) s.logExportTriggerTag else s.logExportRuntimeTag
            sb.append("$time $prefix [${entry.tag}] ${entry.message}\n")
        }
        return sb.toString()
    }
}
