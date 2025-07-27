package org.peyilo.libreadview.utils

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
