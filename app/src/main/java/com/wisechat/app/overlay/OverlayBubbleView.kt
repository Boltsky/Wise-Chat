package com.wisechat.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.wisechat.app.R
import kotlin.math.abs

enum class BubbleVisualState {
    IDLE,
    ANALYZING,
    SUCCESS,
    ERROR
}

class OverlayBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onBubbleClicked: () -> Unit
) : View(context) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5")
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5CF6")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val iconBitmap: Bitmap? by lazy {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        drawable?.let {
            val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            it.setBounds(0, 0, 80, 80)
            it.draw(canvas)
            bmp
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat().coerceAtLeast(20f)

    var visualState: BubbleVisualState = BubbleVisualState.IDLE
        set(value) {
            field = value
            when (value) {
                BubbleVisualState.ANALYZING -> startPulseAnimation()
                BubbleVisualState.SUCCESS -> {
                    ringPaint.color = Color.parseColor("#10B981")
                    stopPulseAnimation()
                }
                BubbleVisualState.ERROR -> {
                    ringPaint.color = Color.parseColor("#EF4444")
                    stopPulseAnimation()
                }
                BubbleVisualState.IDLE -> {
                    ringPaint.color = Color.parseColor("#8B5CF6")
                    stopPulseAnimation()
                }
            }
            invalidate()
        }

    private var pulseRadiusOffset = 0f
    private var pulseAnimator: ValueAnimator? = null

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 10f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulseRadiusOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseRadiusOffset = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Bubble touch target: 60dp standard size
        val size = (60 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width / 2f) - 12f

        // Draw shadow
        canvas.drawCircle(cx, cy + 4f, radius, shadowPaint)

        // Draw main circular bubble
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Draw animated status ring
        canvas.drawCircle(cx, cy, radius + pulseRadiusOffset, ringPaint)

        // Draw Wise Chat icon centered
        iconBitmap?.let {
            val left = cx - (it.width / 2f)
            val top = cy - (it.height / 2f)
            canvas.drawBitmap(it, left, top, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartX
                val dy = event.rawY - touchStartY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    layoutParams.x = (initialX + dx).toInt()
                    layoutParams.y = (initialY + dy).toInt()
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val totalDx = abs(event.rawX - touchStartX)
                val totalDy = abs(event.rawY - touchStartY)
                if (!isDragging || (totalDx < touchSlop && totalDy < touchSlop)) {
                    onBubbleClicked()
                } else {
                    snapToEdge()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (layoutParams.x + width / 2 > screenWidth / 2) {
            screenWidth - width - 16
        } else {
            16
        }

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                layoutParams.x = it.animatedValue as Int
                try {
                    windowManager.updateViewLayout(this@OverlayBubbleView, layoutParams)
                } catch (_: Exception) {}
            }
            start()
        }
    }
}
