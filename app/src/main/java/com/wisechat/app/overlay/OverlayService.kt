package com.wisechat.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wisechat.app.MainActivity
import com.wisechat.app.R
import com.wisechat.app.WiseChatApp
import com.wisechat.app.capture.CaptureEvent
import com.wisechat.app.capture.ScreenCapturePermissionActivity
import com.wisechat.app.capture.ScreenCaptureService
import com.wisechat.app.data.preferences.ThemeMode
import com.wisechat.app.domain.model.*
import com.wisechat.app.engine.ocr.MlKitOcrEngine
import com.wisechat.app.engine.provider.GenerateRepliesParams
import com.wisechat.app.ui.theme.WiseChatTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.hypot

class OverlayService : Service() {

    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val ocrEngine = MlKitOcrEngine()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var bubbleView: OverlayBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var dismissTargetView: OverlayDismissTargetView? = null
    private var dismissTargetParams: WindowManager.LayoutParams? = null
    private var isDismissTargetAttached = false

    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isPanelOpen = false

    // State for overlay composable (Observable by Compose)
    private var isAnalyzing by mutableStateOf(false)
    private var isGenerating by mutableStateOf(false)
    private var currentContext by mutableStateOf<ConversationContext?>(null)
    private var suggestions by mutableStateOf<List<ReplySuggestion>>(emptyList())
    private var selectedStyle by mutableStateOf(ReplyStyle.CASUAL)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForegroundServiceWithNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }

        try {
            if (Settings.canDrawOverlays(this)) {
                initBubbleView()
                initDismissTargetView()
                listenToCaptureEvents()
            } else {
                Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize overlay window: ${e.message}", e)
            Toast.makeText(this, "Could not display overlay. Check device pop-up permissions.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initDismissTargetView() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val density = resources.displayMetrics.density

        dismissTargetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt()
        }

        dismissTargetView = OverlayDismissTargetView(this)
    }

    private fun showDismissTarget() {
        if (!isDismissTargetAttached && dismissTargetView != null && dismissTargetParams != null) {
            try {
                windowManager.addView(dismissTargetView, dismissTargetParams)
                isDismissTargetAttached = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show dismiss target", e)
            }
        }
    }

    private fun hideDismissTarget() {
        if (isDismissTargetAttached && dismissTargetView != null) {
            try {
                dismissTargetView?.isDismissHovered = false
                windowManager.removeView(dismissTargetView)
                isDismissTargetAttached = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide dismiss target", e)
            }
        }
    }

    private fun checkHoverDismissTarget(bubbleX: Float, bubbleY: Float): Boolean {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val targetCenterX = metrics.widthPixels / 2f
        val targetCenterY = metrics.heightPixels - (50 * density)

        val distance = hypot(bubbleX - targetCenterX, bubbleY - targetCenterY)
        val isHovered = distance < (95 * density)
        dismissTargetView?.isDismissHovered = isHovered
        return isHovered
    }

    private fun initBubbleView() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        val app = application as WiseChatApp
        bubbleView = OverlayBubbleView(
            context = this,
            windowManager = windowManager,
            layoutParams = bubbleParams!!,
            onBubbleClicked = {
                toggleOverlayPanel()
            },
            onShowDismissTarget = {
                showDismissTarget()
            },
            onHideDismissTarget = {
                hideDismissTarget()
            },
            onCheckHoverDismissTarget = { bx, by ->
                checkHoverDismissTarget(bx, by)
            },
            onDismiss = {
                hideDismissTarget()
                ScreenCaptureService.stopSession(this@OverlayService)
                serviceScope.launch {
                    app.preferencesManager.setOverlayEnabled(false)
                }
                stopSelf()
            }
        )

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubbleView to WindowManager", e)
            Toast.makeText(this, "Unable to draw overlay on this device.", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun getOrCreatePanelView(): View {
        if (panelView != null) return panelView!!

        val app = application as WiseChatApp
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(app)
            setViewTreeViewModelStoreOwner(app)
            setViewTreeSavedStateRegistryOwner(app)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }

        // Initialize lifecycle-aware Recomposer for Compose in WindowManager
        val coroutineContext = AndroidUiDispatcher.CurrentThread
        val recomposer = Recomposer(coroutineContext)
        composeView.compositionContext = recomposer
        serviceScope.launch(coroutineContext) {
            recomposer.runRecomposeAndApplyChanges()
        }

        composeView.setContent {
            val themeMode by app.preferencesManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val useDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            WiseChatTheme(darkTheme = useDarkTheme) {
                OverlayPanelContent(
                    isAnalyzing = isAnalyzing,
                    isGenerating = isGenerating,
                    conversationContext = currentContext,
                    suggestions = suggestions,
                    selectedStyle = selectedStyle,
                    errorMessage = errorMessage,
                    onAnalyzeScreenClicked = {
                        requestScreenCapture()
                    },
                    onRescanClicked = {
                        requestScreenCapture()
                    },
                    onStyleSelected = { style ->
                        selectedStyle = style
                        if (currentContext != null) {
                            generateReplies()
                        }
                    },
                    onSelectTargetMessage = { messageId ->
                        currentContext = currentContext?.withTarget(messageId)
                        generateReplies()
                    },
                    onTargetMessageChanged = { text ->
                        val targetId = currentContext?.resolvedTargetMessage?.id
                        if (targetId != null) {
                            currentContext = currentContext?.updateMessageText(targetId, text)
                            generateReplies()
                        }
                    },
                    onGenerateClicked = {
                        generateReplies()
                    },
                    onRegenerateClicked = {
                        generateReplies()
                    },
                    onOpenFullAppClicked = {
                        val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        hideOverlayPanel()
                    },
                    onMinimizeClicked = {
                        hideOverlayPanel()
                    },
                    onCloseClicked = {
                        stopSelf()
                    },
                    onCopySuggestion = { suggestion ->
                        serviceScope.launch {
                            try {
                                val target = currentContext?.resolvedTargetMessage?.text ?: "Screen Capture"
                                val provider = app.providerRepository.getActiveProvider().name
                                app.historyRepository.saveHistory(
                                    targetMessage = target,
                                    selectedReply = suggestion.text,
                                    style = suggestion.style.title,
                                    providerName = provider
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save copied reply to history: ${e.message}")
                            }
                        }
                    }
                )
            }
        }

        composeView.isFocusable = true
        composeView.isFocusableInTouchMode = true
        composeView.requestFocus()

        composeView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                hideOverlayPanel()
                true
            } else {
                false
            }
        }

        composeView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                hideOverlayPanel()
                true
            } else {
                false
            }
        }

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panelView = composeView
        return composeView
    }

    private fun toggleOverlayPanel() {
        if (isPanelOpen) {
            hideOverlayPanel()
        } else {
            showOverlayPanel()
        }
    }

    private fun showOverlayPanel() {
        if (!isPanelOpen) {
            try {
                val view = getOrCreatePanelView()
                windowManager.addView(view, panelParams)
                view.requestFocus()
                isPanelOpen = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay panel", e)
            }
        }
    }

    private fun hideOverlayPanel() {
        if (isPanelOpen && panelView != null) {
            try {
                windowManager.removeView(panelView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove panel view", e)
            }
            panelView = null
            isPanelOpen = false
        }
    }

    private fun requestScreenCapture() {
        bubbleView?.visibility = View.GONE
        hideOverlayPanel()

        // If Wise Chat's main activity is open in foreground, minimize it to reveal the messaging app underneath
        if (MainActivity.isResumed) {
            MainActivity.instance?.moveTaskToBack(true)
        }

        bubbleView?.visualState = BubbleVisualState.ANALYZING

        serviceScope.launch {
            delay(200) // Allow window transition/minimization to complete before capturing screen

            if (ScreenCaptureService.isSessionReady) {
                ScreenCaptureService.captureCurrentFrame(this@OverlayService)
            } else {
                ScreenCapturePermissionActivity.launch(this@OverlayService)
            }
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        isAnalyzing = true
        errorMessage = null
        bubbleView?.visualState = BubbleVisualState.ANALYZING
        showOverlayPanel()

        serviceScope.launch {
            val result = ocrEngine.processImage(bitmap)
            isAnalyzing = false

            result.onSuccess { context ->
                currentContext = context
                bubbleView?.visualState = BubbleVisualState.SUCCESS
                generateReplies()
            }.onFailure { ex ->
                errorMessage = ex.localizedMessage ?: "OCR failed to read conversation"
                bubbleView?.visualState = BubbleVisualState.ERROR
            }
        }
    }

    private fun listenToCaptureEvents() {
        serviceScope.launch {
            ScreenCaptureService.events.collectLatest { event ->
                bubbleView?.visibility = View.VISIBLE

                when (event) {
                    is CaptureEvent.Success -> {
                        processCapturedBitmap(event.bitmap)
                    }
                    is CaptureEvent.Error -> {
                        errorMessage = event.message
                        bubbleView?.visualState = BubbleVisualState.ERROR
                        showOverlayPanel()
                    }
                }
            }
        }
    }

    private fun generateReplies() {
        val context = currentContext ?: return
        isGenerating = true
        errorMessage = null
        bubbleView?.visualState = BubbleVisualState.ANALYZING

        serviceScope.launch {
            val app = application as WiseChatApp
            val params = GenerateRepliesParams(
                conversationContext = context,
                styleOptions = StyleCustomization(
                    selectedStyle = selectedStyle,
                    intensity = ToneIntensity.BALANCED,
                    length = ReplyLength.SHORT,
                    emojiUsage = EmojiUsage.OCCASIONAL,
                    language = "English"
                )
            )

            val result = app.providerCoordinator.generateReplies(params)
            isGenerating = false

            result.onSuccess { response ->
                suggestions = response.suggestions
                bubbleView?.visualState = BubbleVisualState.SUCCESS
            }.onFailure { ex ->
                errorMessage = ex.localizedMessage ?: "Failed to generate suggestions"
                bubbleView?.visualState = BubbleVisualState.ERROR
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wise Chat Assistant Active")
            .setContentText("Tap the floating bubble anytime to analyze messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wise Chat Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains the floating assistant above other applications"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ScreenCaptureService.stopSession(this)
        serviceScope.cancel()
        hideDismissTarget()
        try {
            if (bubbleView != null) windowManager.removeView(bubbleView)
            if (isPanelOpen && panelView != null) windowManager.removeView(panelView)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_STOP_OVERLAY = "com.wisechat.app.STOP_OVERLAY"
        private const val CHANNEL_ID = "wise_chat_overlay_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            try {
                val intent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting OverlayService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OverlayService::class.java).apply {
                    action = ACTION_STOP_OVERLAY
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping OverlayService", e)
            }
        }
    }
}
