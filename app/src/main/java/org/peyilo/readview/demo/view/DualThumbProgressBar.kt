package org.peyilo.readview.demo.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * 双游标进度条（DualThumbProgressBar）
 *
 * 特性：
 * - 支持最大值 max
 * - 支持 primaryProgress（当前进度）和 secondaryProgress（上一次进度/参考进度）
 * - 左右两种颜色显示：已完成部分与未完成部分
 * - 两个可交互的游标：primaryThumb 和 secondaryThumb
 */
class DualThumbProgressBar(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // 系统触摸阈值
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ====== Paint ======
    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#C8C8C8".toColorInt() // 已完成部分颜色
    }
    private val remainingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#D8D8D8".toColorInt() // 未完成部分颜色
    }
    private val primaryThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#EDEDED".toColorInt() // 当前进度游标
    }
    private val secondaryThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#E5E5E5".toColorInt() // 参考进度游标
    }

    // ====== 进度相关属性 ======
    var max = 100
    private var primaryProgress = 0      // 当前进度

    private var secondaryProgress = 0    // 上一次进度 / 参考进度

    // ====== 尺寸（由 onMeasure 决定） ======
    var barHeight = (24 * resources.displayMetrics.density).toInt()
        set(value) {
            field = value
            requestLayout()
        }

    private val thumbRadius get() = barHeight / 2.3f
    private val barCornerRadius get() = barHeight / 2f

    // ====== 回调 ======
    /** 进度变化回调（拖动中实时触发） */
    var onProgressChanged: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    // ====== 复用对象 ======
    private val rectCompleted = RectF()
    private val rectRemaining = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 宽度：交给父布局决定
        val width = MeasureSpec.getSize(widthMeasureSpec)

        // 高度：我们自己决定，最多不超过父布局给的上限
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                // 即使是 EXACTLY，我们也不盲目用父布局的值，而是取我们想要的最小值
                barHeight + paddingTop + paddingBottom
            }
            MeasureSpec.AT_MOST -> {
                (barHeight + paddingTop + paddingBottom).coerceAtMost(
                    MeasureSpec.getSize(heightMeasureSpec)
                )
            }
            else -> {
                barHeight + paddingTop + paddingBottom
            }
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barStart = paddingLeft.toFloat()
        val barEnd = width - paddingRight.toFloat()
        val barTop = paddingTop.toFloat()
        val barBottom = height - paddingBottom.toFloat()

        // 当前进度对应的X
        val currX = progressToX(primaryProgress)

        // 背景：未完成部分
        rectRemaining.set(barStart, barTop, barEnd, barBottom)
        canvas.drawRoundRect(rectRemaining, barCornerRadius, barCornerRadius, remainingPaint)

        // 已完成部分
        rectCompleted.set(barStart, barTop, currX + barCornerRadius, barBottom)
        canvas.drawRoundRect(rectCompleted, barCornerRadius, barCornerRadius, completedPaint)

        // secondaryProgress 游标
        val secX = progressToX(secondaryProgress)
        canvas.drawCircle(secX, barTop + barHeight / 2f, thumbRadius, secondaryThumbPaint)

        // primaryProgress 游标
        canvas.drawCircle(currX, barTop + barHeight / 2f, thumbRadius, primaryThumbPaint)
    }

    private var isPrimaryThumb = true
    private var isDragging = false
    private val downPos = PointF()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
                isDragging = false

                // 判断是否点击 primaryThumb
                val currX = progressToX(primaryProgress)
                if (abs(event.x - currX) <= thumbRadius) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    isPrimaryThumb = true
                    return true
                }

                // 判断是否点击 secondaryThumb
                val secX = progressToX(secondaryProgress)
                if (abs(event.x - secX) <= thumbRadius) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    isPrimaryThumb = false
                    return true
                }

                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPrimaryThumb) {
                    updateProgress(event.x)
                }
                if (!isDragging && abs(event.x - downPos.x) > scaledTouchSlop) {
                    isDragging = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (!isPrimaryThumb && !isDragging) {
                    val oldValue = primaryProgress
                    val newValue = secondaryProgress
                    primaryProgress = secondaryProgress
                    invalidate()
                    if (oldValue != newValue) {
                        onProgressChanged?.invoke(primaryProgress, true)
                    }
                }
            }
        }
        return true
    }

    private fun updateProgress(x: Float) {
        val barStart = paddingLeft + thumbRadius
        val barEnd = width - paddingRight - thumbRadius
        val clampedX = x.coerceIn(barStart, barEnd)
        val ratio = (clampedX - barStart) / (barEnd - barStart)
        val newValue = (ratio * max).toInt()
        val oldValue = primaryProgress
        primaryProgress = newValue
        invalidate()
        if (oldValue != newValue) {
            onProgressChanged?.invoke(primaryProgress, true)
        }
    }

    private fun progressToX(progress: Int): Float {
        val barStart = paddingLeft + barCornerRadius
        val barEnd = width - paddingRight - barCornerRadius
        val ratio = progress.toFloat() / max
        return barStart + ratio * (barEnd - barStart)
    }

    /**
     * 设置已完成部分进度条颜色
     */
    fun setCompletedColor(color: Int) {
        completedPaint.color = color
        invalidate()
    }

    /**
     * 设置未完成部分进度条颜色
     */
    fun setRemainingColor(color: Int) {
        remainingPaint.color = color
        invalidate()
    }

    /**
     * 设置当前游标的颜色
     */
    fun setPrimaryThumbColor(color: Int) {
        primaryThumbPaint.color = color
        invalidate()
    }

    /**
     * 设置参考游标的颜色
     */
    fun setSecondaryThumbColor(color: Int) {
        secondaryThumbPaint.color = color
        invalidate()
    }

    fun getProgress() = primaryProgress

    /**
     * 设置进度
     * @param progress 新进度值
     * @param fromUser 是否由用户操作触发（用于回调区分）
     * @param needJoin 是否将 secondaryProgress 一起设置为相同值
     */
    fun setProgress(progress: Int, fromUser: Boolean = false, needJoin: Boolean = false) {
        val newValue = progress.coerceIn(0, max)
        val oldValue = primaryProgress
        primaryProgress = newValue
        if (needJoin) {
            secondaryProgress = newValue
        }
        invalidate()
        if (oldValue != newValue) {
            onProgressChanged?.invoke(primaryProgress, fromUser)
        }
    }

}