package com.clean.signalstar.core.update

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val releasePageUrl: String,
    val downloadUrl: String?,
    val publishedAt: String?,
    val source: String
)

sealed class UpdateCheckResult {
    data class HasUpdate(val release: AppReleaseInfo) : UpdateCheckResult()
    data class AlreadyLatest(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val DEFAULT_REPO = "Healer-s/SignalStar"

    private val gson = Gson()

    suspend fun checkUpdate(
        currentVersionName: String,
        repoPath: String = DEFAULT_REPO
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        // 1. 优先尝试 GitHub Releases API
        val githubResult = queryGitHub(repoPath)
        if (githubResult != null) {
            return@withContext evaluateRelease(currentVersionName, githubResult)
        }

        // 2. 若 GitHub 不可达（国内常见网络环境），自动回退尝试 Gitee API
        val giteeResult = queryGitee(repoPath)
        if (giteeResult != null) {
            return@withContext evaluateRelease(currentVersionName, giteeResult)
        }

        // 3. 两者均失败或尚无发布版本
        return@withContext UpdateCheckResult.AlreadyLatest(currentVersionName)
    }

    private fun evaluateRelease(currentVersion: String, release: AppReleaseInfo): UpdateCheckResult {
        return if (isNewerVersion(currentVersion, release.tagName)) {
            UpdateCheckResult.HasUpdate(release)
        } else {
            UpdateCheckResult.AlreadyLatest(currentVersion)
        }
    }

    fun isNewerVersion(currentVersion: String, remoteTag: String): Boolean {
        try {
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")

            val currentParts = cleanCurrent.split(".").mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
            val remoteParts = cleanRemote.split(".").mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }

            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val curr = currentParts.getOrElse(i) { 0 }
                val rem = remoteParts.getOrElse(i) { 0 }
                if (rem > curr) return true
                if (rem < curr) return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Version compare error: ${e.message}")
        }
        return false
    }

    private fun queryGitHub(repoPath: String): AppReleaseInfo? {
        val urlString = "https://api.github.com/repos/$repoPath/releases/latest"
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "SignalStar-App")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val json = reader.readText()
                reader.close()

                val obj = gson.fromJson(json, JsonObject::class.java)
                val tagName = obj.get("tag_name")?.asString ?: return null
                val title = obj.get("name")?.asString ?: tagName
                val body = obj.get("body")?.asString ?: "优化系统性能与修复已知问题"
                val htmlUrl = obj.get("html_url")?.asString ?: "https://github.com/$repoPath"
                val publishedAt = obj.get("published_at")?.asString?.take(10)

                var downloadUrl: String? = null
                val assets = obj.getAsJsonArray("assets")
                if (assets != null && assets.size() > 0) {
                    for (asset in assets) {
                        val assetObj = asset.asJsonObject
                        val name = assetObj.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = assetObj.get("browser_download_url")?.asString
                            break
                        }
                    }
                }

                AppReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v").removePrefix("V"),
                    title = title,
                    changelog = body,
                    releasePageUrl = htmlUrl,
                    downloadUrl = downloadUrl ?: htmlUrl,
                    publishedAt = publishedAt,
                    source = "GitHub"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub check skipped: ${e.message}")
            null
        }
    }

    private fun queryGitee(repoPath: String): AppReleaseInfo? {
        val urlString = "https://gitee.com/api/v5/repos/$repoPath/releases/latest"
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SignalStar-App")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val json = reader.readText()
                reader.close()

                val obj = gson.fromJson(json, JsonObject::class.java)
                val tagName = obj.get("tag_name")?.asString ?: return null
                val title = obj.get("name")?.asString ?: tagName
                val body = obj.get("body")?.asString ?: "优化系统性能与修复已知问题"
                val htmlUrl = "https://gitee.com/$repoPath/releases"
                val createdAt = obj.get("created_at")?.asString?.take(10)

                var downloadUrl: String? = null
                val assets = obj.getAsJsonArray("assets")
                if (assets != null && assets.size() > 0) {
                    for (asset in assets) {
                        val assetObj = asset.asJsonObject
                        val name = assetObj.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = assetObj.get("browser_download_url")?.asString
                            break
                        }
                    }
                }

                AppReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v").removePrefix("V"),
                    title = title,
                    changelog = body,
                    releasePageUrl = htmlUrl,
                    downloadUrl = downloadUrl ?: htmlUrl,
                    publishedAt = createdAt,
                    source = "Gitee"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Gitee check skipped: ${e.message}")
            null
        }
    }
}
