package com.wisechat.app.domain.model

data class AppUpdateInfo(
    val remoteVersionCode: Int,
    val remoteVersionName: String,
    val commitSha: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val githubUrl: String,
    val forceUpdate: Boolean = false,
    val publishedAt: String = "",
    val isUpdateAvailable: Boolean = false
)

enum class UpdateChannel(val title: String, val description: String) {
    STABLE("Stable Releases", "Only prompts when an official version or release is published"),
    ALL_COMMITS("Fast Track (All Commits)", "Prompts whenever a new update is committed to main")
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val latestInfo: AppUpdateInfo? = null) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
