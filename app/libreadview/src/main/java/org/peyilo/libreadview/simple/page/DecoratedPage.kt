package org.peyilo.libreadview.simple.page

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

    private val paint = Paint().apply {
        strokeWidth = 3F
    }

    private val signColor = Color.GRAY
    private val lineColor = "#D7D7D7".toColorInt()

    private val lineMargin = 50F
    private val lineWidth = 15F

    protected fun centerVerticalTop(height: Int): Float = (this.height - height) / 2F

    protected fun centerHorizontalLeft(width: Int): Float = (this.width - width) / 2F

    /**
     * 在中间绘制一个
     */
    protected fun drawCenterText(canvas: Canvas, paint: Paint, text: String, textSize: Float, y: Float = -1F) {
        paint.textSize = textSize
        val textWidth = paint.measureText(text)
        val textHeight = paint.fontMetrics.bottom - paint.fontMetrics.top
        val x = centerHorizontalLeft(textWidth.toInt())
        val y = if (y < 0F) centerVerticalTop(textHeight.toInt()) else y
        canvas.drawText(text, x, y, paint)
    }

    /**
     * 绘制边框
     */
    private fun drawBorder(canvas: Canvas) {
        paint.color = lineColor
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

        canvas.drawLine(x1, y1, x2, y2, paint)
        canvas.drawLine(x3, y3, x4, y4, paint)
        canvas.drawLine(x1, y1, x3, y3, paint)
        canvas.drawLine(x2, y2, x4, y4, paint)

        canvas.drawLine(x5, y5, x6, y6, paint)
        canvas.drawLine(x7, y7, x8, y8, paint)
        canvas.drawLine(x5, y5, x7, y7, paint)
        canvas.drawLine(x6, y6, x8, y8, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBorder(canvas)

        paint.color = signColor
        drawCenterText(canvas, paint, "—— 夏莳 ——", 32F, height - 150F)
    }

}