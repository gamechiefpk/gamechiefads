package com.gcl.gamechiefads.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.gcl.gamechiefads.ui.R

/**
 * Loading placeholder: a rounded block with a light band sweeping across it. Pure code, no library.
 * Starts when attached and visible, stops when detached or hidden (no leaked animator).
 */
class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var shader: LinearGradient? = null
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var radiusPx: Float

    init {
        val density = resources.displayMetrics.density
        radiusPx = context.resources.getDimension(R.dimen.gads_corner_radius)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.gads_ShimmerView)
            try {
                radiusPx = a.getDimension(R.styleable.gads_ShimmerView_gads_cornerRadius, radiusPx)
            } finally {
                a.recycle()
            }
        }
        radiusPx = radiusPx.coerceAtLeast(0f * density)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val base = context.getColor(R.color.gads_shimmer_base)
        val light = context.getColor(R.color.gads_shimmer_highlight)
        shader = LinearGradient(0f, 0f, w.toFloat().coerceAtLeast(1f), 0f, intArrayOf(base, light, base), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        // band travels from fully left of the view to fully right of it
        matrix.setTranslate(-w + progress * 2f * w, 0f)
        shader?.setLocalMatrix(matrix)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateAnimation()
    }

    private fun updateAnimation() {
        if (isAttachedToWindow && isShown) start() else stop()
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }
}
