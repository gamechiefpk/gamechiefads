package com.gcl.gamechiefads.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator

/** Small premium-looking spinner: a faint ring with a fading arc running around it. Code only, stops when detached. */
internal class ArcSpinner(context: Context, private val color: Int, strokeDp: Float = 4f) : View(context) {
    private val stroke = strokeDp * resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = this@ArcSpinner.color
        alpha = 38
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var angle = 0f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val half = stroke / 2f
        rect.set(half, half, w - half, h - half)
        arc.shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(color and 0x00FFFFFF, color),
            floatArrayOf(0f, 0.75f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawOval(rect, track)
        canvas.save()
        canvas.rotate(angle, width / 2f, height / 2f)
        canvas.drawArc(rect, 0f, 270f, false, arc)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1100
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { angle = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
