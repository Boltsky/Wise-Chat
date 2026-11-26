package com.rizzwise.app.engine.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateDownloader {

    private const val TAG = "AppUpdateDownloader"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Downloads the APK directly into app-private cache (not public Downloads/)
     * and launches the system package installer in-app.
     */
    suspend fun downloadAndInstallInApp(
        context: Context,
        downloadUrl: String,
        backupDownloadUrl: String = "",
        versionName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> {
        val appContext = context.applicationContext
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val targetApk = File(updatesDir, "rizzwise-$versionName.apk")

        // If already downloaded & non-empty for this version, install immediately
        if (targetApk.exists() && targetApk.length() > 1_000_000L) {
            withContext(Dispatchers.Main) {
                onProgress(100)
                launchInAppInstaller(appContext, targetApk)
            }
            return Result.success(Unit)
        }

        // Clean up older cached APKs
        updatesDir.listFiles()?.forEach { file ->
            if (file.name != targetApk.name) {
                file.delete()
            }
        }

        val urlsToTry = listOf(downloadUrl, backupDownloadUrl).filter { it.isNotBlank() }.distinct()
        var lastException: Exception? = null

        for (url in urlsToTry) {
            val result = withContext(Dispatchers.IO) {
                downloadToFile(url, targetApk) { percent ->
                    onProgress(percent)
                }
            }
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    onProgress(100)
                    launchInAppInstaller(appContext, targetApk)
                }
                return Result.success(Unit)
            } else {
                lastException = result.exceptionOrNull() as? Exception
                targetApk.delete()
            }
        }

        return Result.failure(lastException ?: IllegalStateException("Failed to download update."))
    }

    private fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ): Result<Unit> {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code} downloading update"))
                }
                val body = response.body ?: return Result.failure(RuntimeException("Empty download body"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastReportedPercent = -1

                val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error from $url: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun launchInAppInstaller(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                Toast.makeText(
                    context,
                    "Allow RizzWise to install updates, then tap Update Now again.",
                    Toast.LENGTH_LONG
                ).show()
                val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch in-app package installer: ${e.message}", e)
            Toast.makeText(context, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open browser for URL: $url", e)
        }
    }
}
