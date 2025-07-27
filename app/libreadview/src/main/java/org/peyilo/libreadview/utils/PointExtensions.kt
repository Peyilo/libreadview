package org.peyilo.libreadview.utils

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.pow

/**
 * 两个点之间的距离
 */
fun PointF.apartFrom(pointF: PointF): Float {
    return (this.x - pointF.x).pow(2) + (this.y - pointF.y).pow(2)
}

/**
 * 以当前点为原点，并且将当前点作为起点向endPoint做一条直线，获取该直线与x轴的夹角
 */
fun PointF.angle(endPoint: PointF): Int {
    val distanceX = endPoint.x - this.x
    val distanceY = endPoint.y - this.y
    return Math.toDegrees(atan2(distanceY.toDouble(), distanceX.toDouble())).toInt()
}