package org.peyilo.libreadview.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 实现了覆盖翻页中,page的右侧边缘的阴影绘制，并且提供了多个阴影参数设置的API
 */
abstract class CoverShadowLayoutManager: FlipOnReleaseLayoutManager.Horizontal() {

    private val shadowPaint: Paint = Paint()

    /**
     * 阴影宽度
     */
    var shadowWidth: Int = 35

    /**
     * 设置阴影渐变的起始透明度: 0-255，first为起始透明度，second为结束透明度
     */
    var gradientColors: Pair<Int, Int> = Pair(90, 0)

    // 自定义渐变函数：输入0-1，输出0-1
    private var gradientFunction: (Float) -> Float = { t ->
        1 - t
    }

    /**
     * 返回一个View. 阴影将会绘制在该View的右侧（根据translationX确定View的位置）
     */
    protected abstract fun getCoverShadowView(): View?

    /**
     * 处理阴影绘制
     */
    override fun dispatchDraw(canvas: Canvas) {
        val coverShadowView = getCoverShadowView()
        if ((isAnimRuning || isDragging) && coverShadowView != null) {
            pageContainer.apply {
                val shadowStartX = width + coverShadowView.translationX.toInt()
                if (shadowStartX !in 0 until width) return
                for (i in 0 until shadowWidth) {
                    val t = i / shadowWidth.toFloat()                          // 0f ~ 1f
                    val alphaRatio = gradientFunction(t)
                    val alpha = (alphaRatio * gradientColors.first + gradientColors.second).toInt().coerceIn(0, 255)    // 可调最大透明度
                    shadowPaint.color = Color.argb(alpha, 0, 0, 0)            // 只调 alpha，黑色阴影
                    canvas.drawRect( (shadowStartX + i).toFloat(), 0f,
                        (shadowStartX + i + 1).toFloat(), height.toFloat(),
                        shadowPaint
                    )
                }
            }
        }
    }

    fun setGradientFunction(function: (Float) -> Float) {
        gradientFunction = function
    }

    /**
     * 设置阴影的衰减速度：单位圆衰减，先快后慢
     */
    fun useCircleGradientFunction() {
        setGradientFunction { t->
            1 - sqrt(2 * t - t * t)
        }
    }

    /**
     * 设置阴影的衰减速度：线性衰减
     */
    fun useLinearGradientFunction() {
        setGradientFunction { t->
            1 - t
        }
    }

    /**
     * 设置阴影的衰减速度：余弦衰减，先慢后快
     */
    fun useCosineGradientFunction() {
        setGradientFunction { t->
            cos(t * Math.PI / 2).toFloat()
        }
    }

}