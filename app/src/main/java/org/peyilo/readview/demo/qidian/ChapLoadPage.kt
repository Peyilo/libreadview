package org.peyilo.readview.demo.qidian

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import org.peyilo.libreadview.ui.DecoratedPage
import org.peyilo.readview.R

/**
 * 章节加载中视图
 */
class ChapLoadPage(
    context: Context, attrs: AttributeSet? = null
): DecoratedPage(context, attrs) {

    var title: String = ""

    @IntRange(from = 1) var chapIndex = 1

    private val drawable: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.icon_chap_load_page)!! }

    private val textPaint = Paint().apply {
        color = Color.BLACK
    }

    private fun drawDemo(canvas: Canvas) {
        drawable?.apply {
            val scale = 0.35F
            val scaledWidth = (intrinsicWidth * scale).toInt()
            val scaledHeight = (intrinsicHeight * scale).toInt()
            val left = centerHorizontalLeft(scaledWidth).toInt()
            val top = 180

            setBounds(left, top, left + scaledWidth, top + scaledHeight)
            draw(canvas)
        }

        textPaint.color = Color.BLACK
        drawCenterText(canvas, textPaint, "妖精之诗", 42F, 320F)
        drawCenterText(canvas, textPaint, "第一卷 众情相牵之花", 64F, 450F)
        drawCenterText(canvas, textPaint, "冰融水镜照花影，花映人心情相牵。", 48F, 600F)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDemo(canvas)

        textPaint.color = Color.BLACK
        drawCenterText(canvas, textPaint, title, 48F, 1300F)
    }

}