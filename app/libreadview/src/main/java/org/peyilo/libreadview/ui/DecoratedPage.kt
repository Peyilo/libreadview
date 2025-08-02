package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

open class DecoratedPage(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    init {
        setBackgroundColor(Color.WHITE)
    }

    private val linePaint = Paint().apply {
        strokeWidth = 3F
        color = "#D7D7D7".toColorInt()
    }

    private val lineMargin = 50F
    private val lineWidth = 15F

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
    }

}