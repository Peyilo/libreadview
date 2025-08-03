package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import org.peyilo.libreadview.R

/**
 * 章节加载中视图
 */
class ChapLoadPage(
    context: Context, attrs: AttributeSet? = null
): DecoratedPage(context, attrs) {

    var title: String = ""

    @IntRange(from = 1) var chapIndex = 1

    private var drawable: Drawable? = null

    init {
        // 从资源文件中加载一个 Drawable
        drawable = ContextCompat.getDrawable(context, R.drawable.icon_chap_load_page)
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
    }

    private fun centerVerticalTop(height: Int): Int = (this@ChapLoadPage.height - height) / 2

    private fun centerHorizontalLeft(width: Int): Int = (this@ChapLoadPage.width - width) / 2

    private fun drawCenterText(canvas: Canvas, text: String, textSize: Float, y: Float = -1F) {
        textPaint.textSize = textSize
        val textWidth = textPaint.measureText(text)
        val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        val x = centerHorizontalLeft(textWidth.toInt())
        val y = if (y < 0F) centerVerticalTop(textHeight.toInt()) else y
        canvas.drawText(text, x.toFloat(), y.toFloat(), textPaint)
    }

    private fun drawDemo(canvas: Canvas) {
        drawable?.apply {
            val scale = 0.35F
            val scaledWidth = (intrinsicWidth * scale).toInt()
            val scaledHeight = (intrinsicHeight * scale).toInt()
            val left = centerHorizontalLeft(scaledWidth)
            val top = 180

            setBounds(left, top, left + scaledWidth, top + scaledHeight)
            draw(canvas)
        }

        textPaint.color = Color.BLACK
        drawCenterText(canvas, "妖精之诗", 42F, 320F)
        drawCenterText(canvas, "第一卷 众情相牵之花", 64F, 450F)
        drawCenterText(canvas, "冰融水镜照花影，花映人心情相牵。", 48F, 600F)

        textPaint.color = Color.GRAY
        drawCenterText(canvas, "—— 夏莳 ——", 32F, height - 150F)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDemo(canvas)

        textPaint.color = Color.BLACK
        drawCenterText(canvas, title, 48F, 1300F)
    }

}