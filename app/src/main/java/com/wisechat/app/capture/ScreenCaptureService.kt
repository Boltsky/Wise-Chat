package com.wisechat.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.wisechat.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class CaptureEvent {
    data class Success(val bitmap: Bitmap) : CaptureEvent()
    data class Error(val message: String) : CaptureEvent()
}

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captured = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_CAPTURE) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

            if (resultCode != 0 && data != null) {
                startForegroundWithNotification()
                // Small delay to ensure overlay view is hidden before capture
                handler.postDelayed({
                    startCapture(resultCode, data)
                }, 50)
            } else {
                notifyCaptureError("Invalid capture permission parameters.")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wise Chat Screen Analysis")
            .setContentText("Reading conversation...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        captured = false
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            notifyCaptureError("Failed to access screen capture: ${e.message}")
            cleanupAndStop()
            return
        }

        if (mediaProjection == null) {
            notifyCaptureError("Screen capture permission was not granted.")
            cleanupAndStop()
            return
        }

        // MANDATORY on Android 14+ (API 34+): Must register Callback BEFORE createVirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                cleanupAndStop()
            }
        }, handler)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "WiseChatDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        // Safety Watchdog: If no frame captured within 6 seconds, abort cleanly
        handler.postDelayed({
            if (!captured) {
                captured = true
                _events.tryEmit(CaptureEvent.Error("Screen capture timed out. Please try again or import a screenshot."))
                cleanupAndStop()
            }
        }, 6000)

        imageReader?.setOnImageAvailableListener({ reader ->
            if (captured) return@setOnImageAvailableListener
            val image = try {
                reader.acquireLatestImage() ?: reader.acquireNextImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            captured = true
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val rawBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)

                // Crop out row padding if any
                val finalBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                if (finalBitmap != rawBitmap) {
                    rawBitmap.recycle()
                }

                _events.tryEmit(CaptureEvent.Success(finalBitmap))
            } catch (e: Exception) {
                _events.tryEmit(CaptureEvent.Error("Failed to extract screen pixels: ${e.message}"))
            } finally {
                image.close()
                cleanupAndStop()
            }
        }, handler)
    }

    private fun cleanupAndStop() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wise Chat Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for authorized single-frame screen capture"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START_CAPTURE = "com.wisechat.app.START_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "wise_chat_capture_channel"
        private const val NOTIFICATION_ID = 2002

        private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 5)
        val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()

        fun notifyCaptureError(message: String) {
            _events.tryEmit(CaptureEvent.Error(message))
        }
    }
}
