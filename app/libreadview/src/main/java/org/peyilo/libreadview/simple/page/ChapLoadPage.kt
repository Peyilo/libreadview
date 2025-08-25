package org.peyilo.libreadview.simple.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.IntRange

/**
 * 章节加载中视图
 */
class ChapLoadPage(
    context: Context, attrs: AttributeSet? = null
): DecoratedPage(context, attrs) {

    var title: String = ""

    @IntRange(from = 1) var chapIndex = 1

    private val textPaint = Paint().apply {
        color = Color.BLACK
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        textPaint.color = Color.BLACK
        val textSize = 48F
        textPaint.textSize = textSize
        val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        val y1 = centerVerticalTop(textHeight.toInt())
        val y2 = y1 + 2 * textSize
        drawCenterText(canvas, textPaint, title, textSize, y1)
        drawCenterText(canvas, textPaint, "加载中", textSize, y2)
    }

}