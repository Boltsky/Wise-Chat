package com.rizzwise.app.data.repository

import android.util.Log
import com.rizzwise.app.BuildConfig
import com.rizzwise.app.data.preferences.PreferencesManager
import com.rizzwise.app.domain.model.AppUpdateInfo
import com.rizzwise.app.domain.model.UpdateChannel
import com.rizzwise.app.domain.model.UpdateCheckResult
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

            // 1. Multi-tiered manifest fetch:
            //    Tier A: Primary Release Hub on Vercel (https://boltsky-release-hub.vercel.app/api/updates/rizzwise/latest.json)
            //    Tier B: Vercel root version.json (https://boltsky-release-hub.vercel.app/version.json)
            //    Tier C: Edge CDN fallback on here.now (https://awake-zephyr-d9x5.here.now/api/updates/rizzwise/latest.json)
            //    Tier D: GitHub raw repository manifest (https://raw.githubusercontent.com/Boltsky/Wise-Chat/main/version.json)
            val manifestResult = fetchManifestFromUrl(RELEASE_HUB_UPDATE_URL, RELEASE_HUB_BASE_URL)
                .recoverCatching { fetchManifestFromUrl(RELEASE_HUB_VERSION_JSON_URL, RELEASE_HUB_BASE_URL).getOrThrow() }
                .recoverCatching { fetchManifestFromUrl(HERE_NOW_UPDATE_URL, HERE_NOW_BASE_URL).getOrThrow() }
                .recoverCatching { fetchManifestFromUrl(RAW_VERSION_JSON_URL, RELEASE_HUB_BASE_URL).getOrThrow() }

            val remoteInfo = if (manifestResult.isSuccess) {
                manifestResult.getOrNull()
            } else {
                // Tier D: Fallback to GitHub Commits API
                Log.w(TAG, "Edge and GitHub raw manifests failed: ${manifestResult.exceptionOrNull()?.message}. Falling back to GitHub Commits API.")
                fetchFromGitHubCommitsApi().getOrNull()
            }

            if (remoteInfo == null) {
                val errorMsg = manifestResult.exceptionOrNull()?.localizedMessage
                    ?: "Unable to connect to update services (Edge CDN & GitHub)"
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

    private fun fetchManifestFromUrl(
        url: String,
        baseUrl: String = RELEASE_HUB_BASE_URL
    ): Result<AppUpdateInfo> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code} from $url"))
                }
                val body = response.body?.string()?.trim() ?: ""
                if (body.isEmpty() || body.startsWith("<") || body == "Not found") {
                    return Result.failure(RuntimeException("Invalid non-JSON response from $url"))
                }
                val info = parseVersionManifestJson(body, baseUrl)
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

    fun parseVersionManifestJson(
        jsonString: String,
        baseUrl: String = RELEASE_HUB_BASE_URL
    ): AppUpdateInfo {
        val json = JSONObject(jsonString)
        val versionCode = json.optInt("versionCode", 1)
        val versionName = when {
            json.has("versionName") -> json.optString("versionName", "1.0.0")
            json.has("version") -> json.optString("version", "1.0.0")
            else -> "1.0.0"
        }
        val commitSha = json.optString("commitSha", "")

        val appName = json.optString("appName", "RizzWise")
        val releaseTitle = json.optString("releaseTitle", "$appName v$versionName")

        // Parse changelog / releaseNotes
        val releaseNotes = if (json.has("releaseNotes")) {
            json.optString("releaseNotes")
        } else {
            val changelogArr = json.optJSONArray("changelog")
            if (changelogArr != null && changelogArr.length() > 0) {
                (0 until changelogArr.length()).joinToString("\n") { idx ->
                    "• " + changelogArr.optString(idx)
                }
            } else {
                "New performance and feature updates."
            }
        }

        // Parse download URL with relative path resolution
        var downloadUrl = json.optString("downloadUrl", "")
        if (downloadUrl.isBlank()) {
            val platforms = json.optJSONObject("platforms")
            val androidObj = platforms?.optJSONObject("android")
            downloadUrl = androidObj?.optString("downloadUrl", "") ?: ""
        }
        if (downloadUrl.isBlank()) {
            downloadUrl = DEFAULT_DOWNLOAD_URL
        } else if (downloadUrl.startsWith("/")) {
            downloadUrl = "$baseUrl$downloadUrl"
        }

        // Parse GitHub / Release notes URL
        var githubUrl = json.optString("githubReleaseUrl", "")
        if (githubUrl.isBlank()) {
            githubUrl = json.optString("releaseNotesUrl", "")
        }
        if (githubUrl.isBlank()) {
            githubUrl = GITHUB_REPO_URL
        } else if (githubUrl.startsWith("/")) {
            githubUrl = "$baseUrl$githubUrl"
        }

        val forceUpdate = json.optBoolean("forceUpdate", json.optBoolean("mandatory", false))
        val publishedAt = json.optString("publishedAt", json.optString("releaseDate", ""))

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
        const val RELEASE_HUB_BASE_URL = "https://boltsky-release-hub.vercel.app"
        const val RELEASE_HUB_UPDATE_URL = "https://boltsky-release-hub.vercel.app/api/updates/rizzwise/latest.json"
        const val RELEASE_HUB_VERSION_JSON_URL = "https://boltsky-release-hub.vercel.app/version.json"
        const val RELEASE_HUB_DOWNLOAD_URL = "https://boltsky-release-hub.vercel.app/releases/rizzwise/rizzwise-release.apk"
        const val HERE_NOW_BASE_URL = "https://awake-zephyr-d9x5.here.now"
        const val HERE_NOW_UPDATE_URL = "https://awake-zephyr-d9x5.here.now/api/updates/rizzwise/latest.json"
        const val RAW_VERSION_JSON_URL = "https://raw.githubusercontent.com/Boltsky/Wise-Chat/main/version.json"
        const val GITHUB_COMMITS_API_URL = "https://api.github.com/repos/Boltsky/Wise-Chat/commits/main"
        const val GITHUB_REPO_URL = "https://github.com/Boltsky/Wise-Chat"
        const val DEFAULT_DOWNLOAD_URL = RELEASE_HUB_DOWNLOAD_URL
        const val GITHUB_DOWNLOAD_URL = "https://github.com/Boltsky/Wise-Chat/raw/main/apk/rizzwise-release.apk"
        private const val USER_AGENT = "RizzWise-Android-App"
    }
}
