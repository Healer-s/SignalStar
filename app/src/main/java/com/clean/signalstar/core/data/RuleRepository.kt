package com.clean.signalstar.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clean.signalstar.core.model.MatchType
import com.clean.signalstar.core.model.SignalRule
import com.clean.signalstar.core.model.SignalType
import com.clean.signalstar.ui.i18n.activeAppStrings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "signalstar_rules_pref")

class RuleRepository(private val context: Context) {

    private val gson = Gson()
    private val rulesKey = stringPreferencesKey("signal_rules_json")

    val rulesFlow: Flow<List<SignalRule>> = context.dataStore.data.map { preferences ->
        val json = preferences[rulesKey]
        if (json.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<SignalRule>>() {}.type
                val parsed = gson.fromJson<List<SignalRule>>(json, type) ?: emptyList()
                parsed.filterNot { it.id == "preset_gate" || it.id == "preset_leave_home" }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveRules(rules: List<SignalRule>) {
        val json = gson.toJson(rules)
        context.dataStore.edit { preferences ->
            preferences[rulesKey] = json
        }
    }

    suspend fun addRule(rule: SignalRule) {
        context.dataStore.edit { preferences ->
            val currentList = getCurrentRulesList(preferences)
            val updated = currentList + rule
            preferences[rulesKey] = gson.toJson(updated)
        }
    }

    suspend fun updateRule(rule: SignalRule) {
        context.dataStore.edit { preferences ->
            val currentList = getCurrentRulesList(preferences)
            // 编辑保存规则时自动清零冷却时间戳，方便用户在当前 Wi-Fi/蓝牙环境下保存后立即触发验证！
            val resetRule = rule.copy(lastTriggeredAt = 0L)
            val updated = currentList.map { if (it.id == rule.id) resetRule else it }
            preferences[rulesKey] = gson.toJson(updated)
        }
    }

    suspend fun deleteRule(ruleId: String) {
        context.dataStore.edit { preferences ->
            val currentList = getCurrentRulesList(preferences)
            val updated = currentList.filterNot { it.id == ruleId }
            preferences[rulesKey] = gson.toJson(updated)
        }
    }

    suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentList = getCurrentRulesList(preferences)
            val updated = currentList.map {
                if (it.id == ruleId) it.copy(enabled = enabled, lastTriggeredAt = 0L) else it
            }
            preferences[rulesKey] = gson.toJson(updated)
        }
    }

    suspend fun recordRuleTriggered(ruleId: String) {
        context.dataStore.edit { preferences ->
            val currentList = getCurrentRulesList(preferences)
            val updated = currentList.map {
                if (it.id == ruleId) it.copy(lastTriggeredAt = System.currentTimeMillis()) else it
            }
            preferences[rulesKey] = gson.toJson(updated)
        }
    }

    private fun getCurrentRulesList(preferences: Preferences): List<SignalRule> {
        val json = preferences[rulesKey]
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SignalRule>>() {}.type
            val parsed = gson.fromJson<List<SignalRule>>(json, type) ?: emptyList()
            parsed.filterNot { it.id == "preset_gate" || it.id == "preset_leave_home" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 导出全部规则为 JSON 字符串（纯离线备份）
     */
    suspend fun exportRulesJson(): String {
        val current = rulesFlow.first()
        return gson.toJson(current)
    }

    /**
     * 从 JSON 字符串导入并合并规则（不覆盖现有不冲突规则，返回新增/更新条数）
     */
    suspend fun importRulesJson(rawJson: String): Result<Int> {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException(activeAppStrings.ruleImportEmptyError))
            }
            val type = object : TypeToken<List<SignalRule>>() {}.type
            val imported = gson.fromJson<List<SignalRule>>(trimmed, type)
                ?.filter { !it.name.isNullOrBlank() && !it.triggerTag.isNullOrBlank() }
                .orEmpty()
            if (imported.isEmpty()) {
                return Result.failure(IllegalArgumentException(activeAppStrings.ruleImportInvalidError))
            }
            context.dataStore.edit { preferences ->
                val currentList = getCurrentRulesList(preferences).toMutableList()
                val existingIds = currentList.map { it.id }.toMutableSet()
                for (rule in imported) {
                    val normalized = rule.copy(lastTriggeredAt = 0L)
                    if (existingIds.contains(normalized.id)) {
                        val idx = currentList.indexOfFirst { it.id == normalized.id }
                        if (idx >= 0) currentList[idx] = normalized
                    } else {
                        currentList.add(normalized)
                        existingIds.add(normalized.id)
                    }
                }
                preferences[rulesKey] = gson.toJson(currentList)
            }
            Result.success(imported.size)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(activeAppStrings.ruleImportParseError))
        }
    }
}
