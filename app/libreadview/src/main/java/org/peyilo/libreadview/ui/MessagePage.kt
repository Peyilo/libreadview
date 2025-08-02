package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet

/**
 * 加载目录时、加载章节、加载失败要显示的视图
 */
class MessagePage(
    context: Context, attrs: AttributeSet? = null
): DecoratedPage(context, attrs) {

    var text: String = ""

    private val textPaint = Paint().apply {
        textSize = 48F
        color = Color.BLACK
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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