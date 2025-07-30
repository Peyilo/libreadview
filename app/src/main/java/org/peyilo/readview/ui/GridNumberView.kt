package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 在页面正中绘制一个数字，并且同时绘制网格
 */
class GridNumberView (
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var number: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    // 绘制细线网格
    var cellWidth = 40
    var cellHeight = 40

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    val gridPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rows = height / cellHeight
        val cols = width / cellWidth

        // 绘制纵向线
        for (i in 0..cols) {
            val x = i * cellWidth.toFloat()
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }

        // 绘制横向线
        for (i in 0..rows) {
            val y = i * cellHeight.toFloat()
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        val text = number.toString()
        val width = width.toFloat()
        val height = height.toFloat()

        // 计算合适的字体大小（以最小边为基准）
        val maxTextSize = height.coerceAtMost(width) * 0.6f
        paint.textSize = maxTextSize

        // 可选：根据大小动态设置字体粗细
        paint.typeface = Typeface.create(Typeface.DEFAULT, when {
            maxTextSize > 100 -> Typeface.BOLD
            maxTextSize > 60 -> Typeface.NORMAL
            else -> Typeface.NORMAL
        })

        // 计算文本真实高度（用于垂直居中）
        paint.getTextBounds(text, 0, text.length, textBounds)
        val textHeight = textBounds.height()

        // 居中绘制
        canvas.drawText(
            text,
            width / 2f,
            height / 2f + textHeight / 2f,
            paint
        )


    }

}