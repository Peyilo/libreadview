package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 加载目录时、加载章节、加载失败要显示的视图
 */
class PlaceholderPage(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var text: String = ""

    private val textPaint = Paint().apply {
        textSize = 48F
        color = Color.BLACK
    }

    private val linePaint = Paint().apply {
        strokeWidth = 1F
        color = Color.GRAY
    }

    private val lineMargin = 30F
    private val lineWidth = 10F

    /**
     * 绘制边框
     */
    private fun drawBorder(canvas: Canvas) {
        val x1 = lineMargin
        val y1 = lineMargin - lineWidth
        val x2 = width - lineMargin
        val y2 = lineMargin - lineWidth
        val x3 = lineMargin
        val y3 = height - lineMargin + lineWidth
        val x4 = width - lineMargin
        val y4 = height - lineMargin + lineWidth

        val x5 = lineMargin - lineWidth
        val y5 = lineMargin
        val x6 = width - lineMargin + lineWidth
        val y6 = lineMargin
        val x7 = lineMargin - lineWidth
        val y7 = height - lineMargin
        val x8 = width - lineMargin + lineWidth
        val y8 = height - lineMargin

        canvas.drawLine(x1, y1, x2, y2, linePaint)
        canvas.drawLine(x3, y3, x4, y4, linePaint)
        canvas.drawLine(x1, y1, x3, y3, linePaint)
        canvas.drawLine(x2, y2, x4, y4, linePaint)

        canvas.drawLine(x5, y5, x6, y6, linePaint)
        canvas.drawLine(x7, y7, x8, y8, linePaint)
        canvas.drawLine(x5, y5, x7, y7, linePaint)
        canvas.drawLine(x6, y6, x8, y8, linePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBorder(canvas)

        if (text.isNotEmpty()) {
            val width = width.toFloat()
            val height = height.toFloat()
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top

            val x = width / 2 - textWidth / 2
            val y = height / 2 - textHeight / 2

            canvas.drawText(text, x, y, textPaint)
        }
    }

}