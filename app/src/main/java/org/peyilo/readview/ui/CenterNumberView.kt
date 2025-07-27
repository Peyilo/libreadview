package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class CenterNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var number: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    private val textBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

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
