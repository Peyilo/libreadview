package org.peyilo.libreadview.turning.util

import android.graphics.PointF
import androidx.core.graphics.minus
import kotlin.math.abs
import kotlin.math.sqrt

class Vec private constructor() {
    companion object {

        /**
         * 标准化，也就是：求单位向量，注意：会覆盖原来的向量
         */
        fun normalize(vec: PointF): PointF = vec.apply {
            val length = length(vec)
            x /= length
            y /= length
        }

        /**
         * 取绝对值，注意：会覆盖原来的向量
         */
        fun abs(vec: PointF): PointF = vec.apply {
            x = abs(x)
            y = abs(y)
        }

        /**
         * 求向量vec的长度
         */
        fun length(vec: PointF): Float = sqrt(vec.x * vec.x + vec.y * vec.y)

        /**
         * 求两个点之间的距离
         */
        fun distance(vec1: PointF, vec2: PointF) = length(vec1 - vec2)

        /**
         * 求正交向量，注意：会覆盖原来的向量
         */
        fun orthogonal(vec: PointF): PointF {
            apply {
                val temp = vec.y
                vec.y = vec.x
                vec.x = -temp
            }
            return vec
        }
    }
}

/**
 * 复制
 */
fun PointF.copy() = PointF().apply {
    x = this@copy.x
    y = this@copy.y
}

fun PointF.rakeRadio() = y / x

/**
 * 把点 (px, py) 关于由 (ax, ay)-(bx, by) 定义的直线镜像，返回对称点 (x', y')
 */
fun reflectPointAboutLine(
    px: Float, py: Float,
    ax: Float, ay: Float,
    bx: Float, by: Float
): Pair<Float, Float> {
    val vx = bx - ax
    val vy = by - ay
    val denom = vx*vx + vy*vy
    if (denom == 0f) {
        // 对称轴的两点重合，无法计算
//        require(denom > 0f) { "对称轴的两点不能重合" }
        return 0F to 0F
    }

    // 投影参数 t
    val wx = px - ax
    val wy = py - ay
    val t = (wx*vx + wy*vy) / denom

    // 投影点 H
    val hx = ax + t * vx
    val hy = ay + t * vy

    // 对称点 P' = 2H - P
    val rx = 2f * hx - px
    val ry = 2f * hy - py

    return Pair(rx, ry)
}