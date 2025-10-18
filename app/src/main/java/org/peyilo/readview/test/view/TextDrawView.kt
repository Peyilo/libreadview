package org.peyilo.readview.test.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.peyilo.libreadview.util.DisplayUtil

class TextDrawView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DisplayUtil.spToPx(context, 40f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = DisplayUtil.dpToPx(context, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val text = "你"
        val left = 100f
        val baseY = 200f

        val fm = textPaint.fontMetrics

        // 绘制文字
        canvas.drawText(text, left, baseY, textPaint)

        // 各线位置
        val baseline = baseY
        val ascent = baseline + fm.ascent
        val descent = baseline + fm.descent
        val top = baseline + fm.top
        val bottom = baseline + fm.bottom

        val textWidth = textPaint.measureText(text)

        // 辅助函数
        fun drawLine(y: Float, color: Int, label: String) {
            linePaint.color = color
            canvas.drawLine(left, y, left + textWidth + 100, y, linePaint)
            textPaint.color = color
            textPaint.textSize = DisplayUtil.spToPx(context, 12f)
            canvas.drawText(label, left + textWidth + 20, y, textPaint)
            textPaint.textSize = DisplayUtil.spToPx(context, 40f)
        }

        // 绘制基准线和各指标线
        drawLine(baseline, 0xFFFF0000.toInt(), "baseline")
        drawLine(ascent, 0xFF00AAFF.toInt(), "ascent")
        drawLine(descent, 0xFF00FF00.toInt(), "descent")
        drawLine(top, 0xFFAA00FF.toInt(), "top")
        drawLine(bottom, 0xFFFFFF00.toInt(), "bottom")
    }
}