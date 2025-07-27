package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class AutoCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK   // 设置填充为黑色
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 计算圆心和半径（确保不会超出 View 边界）
        val radius = (min(width, height) / 2f) * 0.8f  // 留点边距（比如 80% 大小）
        val cx = width / 2f
        val cy = height / 2f

        // 绘制圆
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
