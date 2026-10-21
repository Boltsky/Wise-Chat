package com.wisechat.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenCapturePermissionActivity : Activity() {

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var hasRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            hasRequested = savedInstanceState.getBoolean(KEY_HAS_REQUESTED, false)
        }
        if (!hasRequested) {
            hasRequested = true
            try {
                val captureIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    projectionManager.createScreenCaptureIntent(
                        android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                    )
                } else {
                    projectionManager.createScreenCaptureIntent()
                }
                startActivityForResult(captureIntent, REQUEST_CODE_CAPTURE)
            } catch (e: Exception) {
                ScreenCaptureService.notifyCaptureError("Failed to initiate screen capture: ${e.message}")
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_REQUESTED, hasRequested)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE && resultCode == RESULT_OK && data != null) {
            // Keep this Activity alive until the frame is captured or times out,
            // otherwise Android OS immediately invalidates and terminates the MediaProjection session.
            activityScope.launch {
                ScreenCaptureService.events.collect {
                    finish()
                }
            }

            // Safety timeout: never leak this activity
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    finish()
                }
            }, 6000)

            try {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START_CAPTURE
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                ScreenCaptureService.notifyCaptureError("Failed to start screen capture: ${e.message}")
                finish()
            }
        } else {
            ScreenCaptureService.notifyCaptureError(
                "Screen capture permission was not granted. You can import a screenshot instead."
            )
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    companion object {
        private const val REQUEST_CODE_CAPTURE = 1001
        private const val KEY_HAS_REQUESTED = "key_has_requested"

        fun launch(context: Context) {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
