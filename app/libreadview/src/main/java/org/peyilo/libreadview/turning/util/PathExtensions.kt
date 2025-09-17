package org.peyilo.libreadview.turning.util

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 绘制 y = A * sin(w * x) 在 x ∈ [0, π/(2w)] 的 1/4 周期
 *
 * start 表示 x=0 点，end 表示 x=π/w 点（半周期）
 *
 * @param startX 起点X (x=0)
 * @param startY 起点Y
 * @param endX 半周期点X (x=π/w)
 * @param endY 半周期点Y
 * @param amplitude 振幅 A
 * @param angularFrequency 角频率 w（弧度/px）
 * @param steps 曲线分段，越大越平滑
 * @param isFirstQuarter 取前1/4周期还是后1/4周期
 * @param direction 正弦波在法向的方向：+1=正向，-1=反向
 */
fun Path.addQuarterSineFromHalfPeriod(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    amplitude: Float,
    angularFrequency: Float,
    steps: Int = 64,
    isFirstQuarter: Boolean = true,
    direction: Int = 1
) {
    // 向量表示 x 从 0 到 π/w 的方向
    val dx = endX - startX
    val dy = endY - startY
    val totalLength = hypot(dx.toDouble(), dy.toDouble()).toFloat()

    // 单位向量：主方向（x 轴）
    val ux = dx / totalLength
    val uy = dy / totalLength

    // 垂直方向（y 轴）
    val vx = -uy
    val vy = ux

    // x ∈ [0, π/(2w)]，距离为 quarterLength = π / (2w)
    val quarterLength = (PI / (2 * angularFrequency)).toFloat()

    // 采样点数量：由 quarterLength 决定，不超过 floor(quarterLength)
    val maxSamples = max(1, floor(quarterLength).toInt())
    val sampleCount = min(steps, maxSamples)

    for (i in 0..sampleCount) {
        val xVal = i.toFloat() / steps * quarterLength + if (isFirstQuarter) 0F else quarterLength
        val sinVal = amplitude * sin(angularFrequency * xVal) * direction

        val x = startX + ux * xVal + vx * sinVal
        val y = startY + uy * xVal + vy * sinVal
        lineTo(x, y)
    }
}

fun Path.splitPathByLine(p1: PointF, p2: PointF, result: Path, reverse: Boolean = false, samples: Int = 1000) {
    val pm = PathMeasure(this, true)
    val length = pm.length
    val pos = FloatArray(2)

    // Path 采样点
    val points = mutableListOf<PointF>()
    for (i in 0..samples) {
        pm.getPosTan(length * i / samples, pos, null)
        points.add(PointF(pos[0], pos[1]))
    }

    // 直线参数 ax + by + c = 0
    val a = p2.y - p1.y
    val b = p1.x - p2.x
    val c = -(a * p1.x + b * p1.y)

    fun side(x: Float, y: Float): Float = a * x + b * y + c

    result.reset()
    var first = true

    if (!reverse) {
        for (i in points.indices) {
            val pt = points[i]
            val s = side(pt.x, pt.y)
            if (s >= 0) {
                if (first) {
                    result.moveTo(pt.x, pt.y)
                    first = false
                } else {
                    result.lineTo(pt.x, pt.y)
                }
            }
        }
    } else {
        for (i in points.indices) {
            val pt = points[i]
            val s = side(pt.x, pt.y)
            if (s < 0) {
                if (result.isEmpty) {
                    result.moveTo(pt.x, pt.y)
                } else {
                    result.lineTo(pt.x, pt.y)
                }
            }
        }
    }
    result.close()
}

fun Path.buildPath(
    engle1: FloatArray,
    engle2: FloatArray,
    engle3: FloatArray,
    engle4: FloatArray
) {
    reset()
    moveTo(engle1[0], engle1[1])
    for (i in engle1.indices step 2) {
        lineTo(engle1[i], engle1[i + 1])
    }
    for (i in engle4.indices step 2) {
        lineTo(engle4[i], engle4[i + 1])
    }
    for (i in engle2.size - 2 downTo 0 step 2) {
        lineTo(engle2[i], engle2[i + 1])
    }
    for (i in engle3.size - 2 downTo 0 step 2) {
        lineTo(engle3[i], engle3[i + 1])
    }
    close()
}
