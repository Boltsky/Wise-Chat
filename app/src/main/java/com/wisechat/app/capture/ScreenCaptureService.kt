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
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
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

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentDensity = 0

    @Volatile
    private var pendingFrameRequest = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != 0 && data != null) {
                    startForegroundWithNotification()
                    handler.postDelayed({
                        initSession(resultCode, data)
                    }, 50)
                } else {
                    notifyCaptureError("Invalid capture permission parameters.")
                    cleanupAndStop()
                }
            }
            ACTION_CAPTURE_FRAME -> {
                handler.post {
                    requestFrame()
                }
            }
            ACTION_STOP_CAPTURE -> {
                cleanupAndStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wise Chat Assistant")
            .setContentText("Screen capture session active")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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

    private fun initSession(resultCode: Int, data: Intent) {
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

        // Register callback mandatory on Android 14+ before createVirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.i(TAG, "MediaProjection stopped by system callback")
                cleanupAndStop()
            }
        }, handler)

        setupVirtualDisplay()
        isSessionReady = true

        // Capture initial frame for the pending request
        requestFrame()
    }

    private fun setupVirtualDisplay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        currentWidth = metrics.widthPixels
        currentHeight = metrics.heightPixels
        currentDensity = metrics.densityDpi

        imageReader?.close()
        imageReader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                if (pendingFrameRequest) {
                    val image = try {
                        reader.acquireLatestImage() ?: reader.acquireNextImage()
                    } catch (_: Exception) {
                        null
                    }
                    if (image != null) {
                        pendingFrameRequest = false
                        extractAndEmitBitmap(image)
                    }
                } else {
                    // Drain buffer queue so it never overflows
                    try {
                        reader.acquireLatestImage()?.close()
                    } catch (_: Exception) {}
                }
            }, handler)
        }

        virtualDisplay?.release()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "WiseChatDisplay",
            currentWidth,
            currentHeight,
            currentDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun checkDisplayMetricsChange() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        if (metrics.widthPixels != currentWidth || metrics.heightPixels != currentHeight) {
            currentWidth = metrics.widthPixels
            currentHeight = metrics.heightPixels
            currentDensity = metrics.densityDpi

            imageReader?.close()
            imageReader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    if (pendingFrameRequest) {
                        val image = try {
                            reader.acquireLatestImage() ?: reader.acquireNextImage()
                        } catch (_: Exception) {
                            null
                        }
                        if (image != null) {
                            pendingFrameRequest = false
                            extractAndEmitBitmap(image)
                        }
                    } else {
                        try {
                            reader.acquireLatestImage()?.close()
                        } catch (_: Exception) {}
                    }
                }, handler)
            }
            virtualDisplay?.resize(currentWidth, currentHeight, currentDensity)
            virtualDisplay?.surface = imageReader?.surface
        }
    }

    private fun requestFrame() {
        if (mediaProjection == null || imageReader == null) {
            notifyCaptureError("Screen capture session is not active.")
            return
        }

        checkDisplayMetricsChange()

        val reader = imageReader ?: return
        val immediateImage = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        }

        if (immediateImage != null) {
            pendingFrameRequest = false
            extractAndEmitBitmap(immediateImage)
        } else {
            // Wait for next frame
            pendingFrameRequest = true
            // Timeout watchdog
            handler.postDelayed({
                if (pendingFrameRequest) {
                    pendingFrameRequest = false
                    notifyCaptureError("Screen capture timed out waiting for frame.")
                }
            }, 3000)
        }
    }

    private fun extractAndEmitBitmap(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * currentWidth

            val rawBitmap = Bitmap.createBitmap(
                currentWidth + rowPadding / pixelStride,
                currentHeight,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)

            val finalBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, currentWidth, currentHeight)
            if (finalBitmap != rawBitmap) {
                rawBitmap.recycle()
            }

            _events.tryEmit(CaptureEvent.Success(finalBitmap))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract screen pixels", e)
            _events.tryEmit(CaptureEvent.Error("Failed to extract screen pixels: ${e.message}"))
        } finally {
            image.close()
        }
    }

    private fun cleanupAndStop() {
        isSessionReady = false
        pendingFrameRequest = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up projection session", e)
        }
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wise Chat Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for authorized screen capture session"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val ACTION_START_CAPTURE = "com.wisechat.app.START_CAPTURE"
        const val ACTION_CAPTURE_FRAME = "com.wisechat.app.CAPTURE_FRAME"
        const val ACTION_STOP_CAPTURE = "com.wisechat.app.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "wise_chat_capture_channel"
        private const val NOTIFICATION_ID = 2002

        var isSessionReady: Boolean = false
            private set

        private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 5)
        val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()

        fun captureCurrentFrame(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_CAPTURE_FRAME
            }
            context.startService(intent)
        }

        fun stopSession(context: Context) {
            if (isSessionReady) {
                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ACTION_STOP_CAPTURE
                }
                context.startService(intent)
            }
        }

        fun notifyCaptureError(message: String) {
            _events.tryEmit(CaptureEvent.Error(message))
        }
    }
}
