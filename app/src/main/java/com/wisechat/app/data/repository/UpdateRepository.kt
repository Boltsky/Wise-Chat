package com.wisechat.app.data.repository

import android.util.Log
import com.wisechat.app.BuildConfig
import com.wisechat.app.data.preferences.PreferencesManager
import com.wisechat.app.domain.model.AppUpdateInfo
import com.wisechat.app.domain.model.UpdateChannel
import com.wisechat.app.domain.model.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val preferencesManager: PreferencesManager? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun checkForUpdate(
        channel: UpdateChannel? = null,
        force: Boolean = false
    ): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val effectiveChannel = channel ?: preferencesManager?.updateChannel?.first() ?: UpdateChannel.STABLE
            val now = System.currentTimeMillis()

            // 1. Try primary raw manifest (fast CDN, no rate limit)
            val manifestResult = fetchRawVersionManifest()
            val remoteInfo = if (manifestResult.isSuccess) {
                manifestResult.getOrNull()
            } else {
                // Fallback to GitHub Commits API
                Log.w(TAG, "Raw version.json failed: ${manifestResult.exceptionOrNull()?.message}. Falling back to GitHub Commits API.")
                fetchFromGitHubCommitsApi().getOrNull()
            }

            if (remoteInfo == null) {
                val errorMsg = manifestResult.exceptionOrNull()?.localizedMessage
                    ?: "Unable to connect to GitHub update service"
                return@withContext Result.failure(RuntimeException(errorMsg))
            }

            // Save last checked timestamp
            preferencesManager?.setLastUpdateCheckTimestamp(now)

            // Determine if update is available
            val isNewerCode = remoteInfo.remoteVersionCode > BuildConfig.VERSION_CODE
            val isNewerCommit = effectiveChannel == UpdateChannel.ALL_COMMITS &&
                    remoteInfo.commitSha.isNotBlank() &&
                    !isSameCommit(remoteInfo.commitSha, BuildConfig.GIT_COMMIT_HASH)

            val isUpdateAvailable = isNewerCode || isNewerCommit

            val finalInfo = remoteInfo.copy(isUpdateAvailable = isUpdateAvailable)

            if (isUpdateAvailable) {
                // If not forced check, check if user dismissed this exact version
                val dismissedCode = preferencesManager?.dismissedUpdateVersionCode?.first() ?: 0
                if (!force && dismissedCode == finalInfo.remoteVersionCode && !finalInfo.forceUpdate && !isNewerCommit) {
                    Result.success(UpdateCheckResult.UpToDate(finalInfo))
                } else {
                    Result.success(UpdateCheckResult.UpdateAvailable(finalInfo))
                }
            } else {
                Result.success(UpdateCheckResult.UpToDate(finalInfo))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check for update failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun isSameCommit(sha1: String, sha2: String): Boolean {
        if (sha1.isBlank() || sha2.isBlank() || sha1 == "unknown" || sha2 == "unknown") return true
        val clean1 = sha1.trim().lowercase()
        val clean2 = sha2.trim().lowercase()
        return clean1.startsWith(clean2) || clean2.startsWith(clean1)
    }

    private fun fetchRawVersionManifest(): Result<AppUpdateInfo> {
        return try {
            val request = Request.Builder()
                .url(RAW_VERSION_JSON_URL)
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code} from raw manifest"))
                }
                val body = response.body?.string() ?: ""
                val info = parseVersionManifestJson(body)
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromGitHubCommitsApi(): Result<AppUpdateInfo> {
        return try {
            val request = Request.Builder()
                .url(GITHUB_COMMITS_API_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code} from GitHub API"))
                }
                val body = response.body?.string() ?: ""
                val info = parseGitHubCommitJson(body)
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseVersionManifestJson(jsonString: String): AppUpdateInfo {
        val json = JSONObject(jsonString)
        val versionCode = json.optInt("versionCode", 1)
        val versionName = json.optString("versionName", "1.0.0")
        val commitSha = json.optString("commitSha", "")
        val releaseTitle = json.optString("releaseTitle", "Wise Chat Update")
        val releaseNotes = json.optString("releaseNotes", "New performance and feature updates.")
        val downloadUrl = json.optString("downloadUrl", DEFAULT_DOWNLOAD_URL)
        val githubUrl = json.optString("githubReleaseUrl", GITHUB_REPO_URL)
        val forceUpdate = json.optBoolean("forceUpdate", false)
        val publishedAt = json.optString("publishedAt", "")

        return AppUpdateInfo(
            remoteVersionCode = versionCode,
            remoteVersionName = versionName,
            commitSha = commitSha,
            releaseTitle = releaseTitle,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            githubUrl = githubUrl,
            forceUpdate = forceUpdate,
            publishedAt = publishedAt,
            isUpdateAvailable = false
        )
    }

    fun parseGitHubCommitJson(jsonString: String): AppUpdateInfo {
        val json = JSONObject(jsonString)
        val sha = json.optString("sha", "")
        val shortSha = if (sha.length >= 7) sha.substring(0, 7) else sha

        var commitMessage = "New update committed to repository."
        var commitDate = ""
        val commitObj = json.optJSONObject("commit")
        if (commitObj != null) {
            commitMessage = commitObj.optString("message", commitMessage)
            val authorObj = commitObj.optJSONObject("author")
            commitDate = authorObj?.optString("date", "") ?: ""
        }

        return AppUpdateInfo(
            remoteVersionCode = BuildConfig.VERSION_CODE, // Commit API does not report versionCode
            remoteVersionName = BuildConfig.VERSION_NAME,
            commitSha = shortSha,
            releaseTitle = "New GitHub Commit ($shortSha)",
            releaseNotes = commitMessage,
            downloadUrl = DEFAULT_DOWNLOAD_URL,
            githubUrl = "$GITHUB_REPO_URL/commit/$sha",
            forceUpdate = false,
            publishedAt = commitDate,
            isUpdateAvailable = false
        )
    }

    companion object {
        private const val TAG = "UpdateRepository"
        const val RAW_VERSION_JSON_URL = "https://raw.githubusercontent.com/Boltsky/Wise-Chat/main/version.json"
        const val GITHUB_COMMITS_API_URL = "https://api.github.com/repos/Boltsky/Wise-Chat/commits/main"
        const val GITHUB_REPO_URL = "https://github.com/Boltsky/Wise-Chat"
        const val DEFAULT_DOWNLOAD_URL = "https://github.com/Boltsky/Wise-Chat/raw/main/apk/wise-chat-release.apk"
        private const val USER_AGENT = "WiseChat-Android-App"
    }
}
