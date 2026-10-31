package com.wisechat.app.engine.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object AppUpdateDownloader {

    private const val TAG = "AppUpdateDownloader"
    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onDownloadStarted: (Long) -> Unit = {}
    ) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "wise-chat-$versionName.apk"

            // Ensure previous download file with same name is deleted if exists
            try {
                val externalDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(externalDownloadsDir, fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            } catch (_: Exception) {}

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Wise Chat Update ($versionName)")
                setDescription("Downloading latest Wise Chat package...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            onDownloadStarted(downloadId)

            Toast.makeText(context, "Downloading Wise Chat update...", Toast.LENGTH_SHORT).show()

            registerDownloadReceiver(context.applicationContext, downloadId, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate download via DownloadManager: ${e.message}", e)
            Toast.makeText(context, "Could not start in-app download. Opening browser...", Toast.LENGTH_LONG).show()
            openInBrowser(context, downloadUrl)
        }
    }

    private fun registerDownloadReceiver(appContext: Context, downloadId: Long, fileName: String) {
        unregisterReceiver(appContext)

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    unregisterReceiver(appContext)
                    triggerPackageInstaller(context, downloadId, fileName)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(downloadReceiver, filter)
        }
    }

    fun triggerPackageInstaller(context: Context, downloadId: Long, fileName: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val contentUri: Uri? = downloadManager.getUriForDownloadedFile(downloadId)

            val uriToInstall: Uri = if (contentUri != null) {
                contentUri
            } else {
                // Fallback to FileProvider
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val apkFile = File(downloadsDir, fileName)
                if (apkFile.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Log.e(TAG, "Downloaded file not found at $apkFile")
                    Toast.makeText(context, "Download complete. Check Downloads folder to install.", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriToInstall, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            Toast.makeText(context, "Tap the notification or check Downloads to install.", Toast.LENGTH_LONG).show()
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

    private fun unregisterReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            downloadReceiver = null
        }
    }
}
