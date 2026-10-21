package com.wisechat.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class OverlayDismissTargetView(context: Context) : View(context) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4A000000")
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#80FFFFFF")
        strokeWidth = 3f
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var isDismissHovered: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateHover(value)
            }
        }

    private var scaleFactor = 1.0f

    private fun animateHover(hovered: Boolean) {
        val targetScale = if (hovered) 1.25f else 1.0f
        val targetColor = if (hovered) Color.parseColor("#EF4444") else Color.parseColor("#5A000000")

        ValueAnimator.ofFloat(scaleFactor, targetScale).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scaleFactor = it.animatedValue as Float
                bgPaint.color = targetColor
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val width = (160 * density).toInt()
        val height = (90 * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f - 6f
        val baseRadius = min(width, height) * 0.35f
        val currentRadius = baseRadius * scaleFactor

        // Draw dismiss circle
        canvas.drawCircle(cx, cy, currentRadius, bgPaint)
        canvas.drawCircle(cx, cy, currentRadius, strokePaint)

        // Draw 'X' cross
        val crossSize = currentRadius * 0.45f
        canvas.drawLine(cx - crossSize, cy - crossSize, cx + crossSize, cy + crossSize, crossPaint)
        canvas.drawLine(cx - crossSize, cy + crossSize, cx + crossSize, cy - crossSize, crossPaint)

        // Draw hint text below
        val textY = cy + currentRadius + 24f
        val hint = if (isDismissHovered) "Release to Exit" else "Drop here to close"
        canvas.drawText(hint, cx, textY, textPaint)
    }
}
