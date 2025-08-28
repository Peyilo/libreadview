package org.peyilo.libreadview.utils

import android.graphics.PointF

class ScreenLineIntersections(
    private val width: Float,
    private val height: Float
) {
    private val eps = 1e-6f

    fun intersections(
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Pair<PointF, PointF> {
        val dx = x2 - x1
        val dy = y2 - y1
        val hits = ArrayList<PointF>(2)

        // 与 x=0
        if (kotlin.math.abs(dx) > eps) {
            val t = (0f - x1) / dx
            val y = y1 + t * dy
            if (y in 0f..height) hits.add(PointF(0f, y))
        }
        // 与 x=width
        if (kotlin.math.abs(dx) > eps) {
            val t = (width - x1) / dx
            val y = y1 + t * dy
            if (y in 0f..height) hits.add(PointF(width, y))
        }
        // 与 y=0
        if (kotlin.math.abs(dy) > eps) {
            val t = (0f - y1) / dy
            val x = x1 + t * dx
            if (x in 0f..width) hits.add(PointF(x, 0f))
        }
        // 与 y=height
        if (kotlin.math.abs(dy) > eps) {
            val t = (height - y1) / dy
            val x = x1 + t * dx
            if (x in 0f..width) hits.add(PointF(x, height))
        }

        val result = when (hits.size) {
            2 -> hits[0] to hits[1]
            else -> PointF(x1, y1) to PointF(x2, y2)
        }

        // 排序：y 小的在前
        return if (result.first.y <= result.second.y) {
            result
        } else {
            result.second to result.first
        }
    }

}
