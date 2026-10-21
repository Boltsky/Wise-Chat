package com.wisechat.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executor

class WiseChatAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "WiseChatAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - passive service strictly for instant screenshot helper
    }

    override fun onInterrupt() {
        Log.i(TAG, "WiseChatAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "WiseChatAccessibilityService destroyed")
    }

    /**
     * Captures a single-frame screenshot directly without any system sharing dialogs.
     * Available on Android 11+ (API 30+).
     */
    fun captureScreenshot(callback: (Bitmap?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mainExecutor = mainExecutor ?: Executor { runnable -> runnable.run() }

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            // Convert hardware bitmap to software ARGB_8888 bitmap so ML Kit OCR can access pixels
                            val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()

                            if (softwareBitmap != null) {
                                callback(softwareBitmap, null)
                            } else {
                                callback(null, "Unable to extract software bitmap from screenshot buffer.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process screenshot buffer", e)
                            callback(null, "Screenshot processing error: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val message = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                "Screenshot requested too quickly. Please wait a moment."
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                                "Wise Chat Accessibility permission was revoked."
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR ->
                                "Internal system error during screenshot capture."
                            else ->
                                "Instant screenshot failed (error code $errorCode)."
                        }
                        Log.w(TAG, "Screenshot capture failed: $message")
                        callback(null, message)
                    }
                }
            )
        } else {
            callback(null, "Instant capture requires Android 11 or higher.")
        }
    }

    companion object {
        private const val TAG = "WiseChatAccessibility"

        var instance: WiseChatAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val expectedComponent = ComponentName(context, WiseChatAccessibilityService::class.java).flattenToString()
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
            } catch (_: Exception) {
                false
            }
        }
    }
}
