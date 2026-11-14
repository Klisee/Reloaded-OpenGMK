package com.klisee.reloaded

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.klisee.reloaded.gmk.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

internal class LoadingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val logo: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.reloaded_logo)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.google_sans_flex) ?: typeface
        color = ResourcesCompat.getColor(resources, R.color.loading_text, context.theme)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            20f,
            resources.displayMetrics,
        )
        letterSpacing = 0.01f
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 255, 255, 255)
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(238, 244, 249, 255)
    }
    private val logoRect = RectF()
    private val trackRect = RectF()
    private val indicatorRect = RectF()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = LOADING_LABEL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(5, 5, 6),
                Color.rgb(18, 19, 21),
                Color.rgb(29, 30, 33),
                Color.rgb(9, 9, 10),
            ),
            floatArrayOf(0f, 0.34f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        val radius = maxOf(width, height).toFloat() * 0.78f
        glowPaint.shader = RadialGradient(
            width * 0.16f,
            height * 0.04f,
            radius,
            intArrayOf(Color.argb(42, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowSecondaryPaint.shader = RadialGradient(
            width * 0.98f,
            height * 0.22f,
            maxOf(width, height).toFloat() * 0.52f,
            intArrayOf(Color.argb(22, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)
        canvas.drawPaint(glowPaint)
        canvas.drawPaint(glowSecondaryPaint)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val logoSize = min(dp(160f), min(width, height) * 0.32f)
        val logoGap = dp(18f)
        val barGap = dp(18f)
        val barHeight = dp(3f).coerceAtLeast(2f)
        val blockHeight = logoSize + logoGap + textHeight + barGap + barHeight
        val top = (height - blockHeight) / 2f - height * 0.02f
        val centerX = width / 2f
        val wave = ((sin(phase * TWO_PI - PI.toFloat() / 2f) + 1f) / 2f)

        logoRect.set(
            centerX - logoSize / 2f,
            top,
            centerX + logoSize / 2f,
            top + logoSize,
        )
        logo?.let {
            logoPaint.alpha = (242 + 13 * wave).toInt()
            canvas.drawBitmap(it, null, logoRect, logoPaint)
        }

        val baseline = logoRect.bottom + logoGap - metrics.ascent
        canvas.drawText(LOADING_LABEL, centerX, baseline, textPaint)

        val barWidth = min(dp(184f), width * 0.30f)
        val barTop = baseline + metrics.descent + barGap
        trackRect.set(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, barTop + barHeight)
        canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint)

        val indicatorWidth = barWidth * 0.34f
        val indicatorLeft = trackRect.left + (barWidth - indicatorWidth) * wave
        indicatorRect.set(
            indicatorLeft,
            barTop,
            indicatorLeft + indicatorWidth,
            barTop + barHeight,
        )
        canvas.drawRoundRect(indicatorRect, barHeight / 2f, barHeight / 2f, indicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        const val LOADING_LABEL = "Cargando..."
        private const val TWO_PI = (PI * 2.0).toFloat()
    }
}
