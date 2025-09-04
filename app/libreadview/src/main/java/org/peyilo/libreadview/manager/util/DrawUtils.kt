package org.peyilo.libreadview.manager.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 在 Canvas 上绘制从 (x0,y0) 到 (x1,y1) 的半周期正弦曲线
 *
 * @param canvas    画布
 * @param paint     画笔
 * @param x0,y0     起点
 * @param x1,y1     终点
 * @param R         振幅
 * @param deltaX    横向跨度（通常设为起点和终点的距离）
 * @param direction 正弦波在法向的方向：+1=正向，-1=反向
 * @param samples   采样点数（越大越平滑）
 */
fun drawHalfSineCurve(
    canvas: Canvas,
    paint: Paint,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    R: Float,
    deltaX: Float,
    direction: Int = 1,
    samples: Int = 64
) {
    val dx = x1 - x0
    val dy = y1 - y0
    val len = hypot(dx, dy)
    if (len == 0f) return

    // 单位向量
    val ux = dx / len
    val uy = dy / len
    // 法向量
    val nx = -uy
    val ny = ux

    var prevX = x0
    var prevY = y0

    for (i in 1..samples) {
        val t = i.toFloat() / samples
        val localX = deltaX * t
        val localY = R * sin(Math.PI * t).toFloat() * direction

        val gx = x0 + localX * ux + localY * nx
        val gy = y0 + localX * uy + localY * ny

        canvas.drawLine(prevX, prevY, gx, gy, paint)

        prevX = gx
        prevY = gy
    }
}

/**
 * 计算点 A 到线段 BC 的垂足坐标
 *
 * @param ax 点 A 的 X
 * @param ay 点 A 的 Y
 * @param bx 点 B 的 X
 * @param by 点 B 的 Y
 * @param cx 点 C 的 X
 * @param cy 点 C 的 Y
 * @return 垂足坐标 PointF
 */
fun perpendicularFoot(
    ax: Float, ay: Float,
    bx: Float, by: Float,
    cx: Float, cy: Float,
    res: PointF
)  {
    val vx = cx - bx
    val vy = cy - by
    val wx = ax - bx
    val wy = ay - by

    val vDotV = vx * vx + vy * vy
    if (vDotV == 0f) {
        // B 与 C 重合，无法定义直线，返回 B
        res.x = bx
        res.y = by
        return
    }

    val t = (wx * vx + wy * vy) / vDotV

    val px = bx + t * vx
    val py = by + t * vy

    res.x = px
    res.y = py
}

/**
 * 已知：A、B 两点（AB 为斜边），BC 的长度 L。
 * 求：满足 ∠C = 90° 且 |BC| = L 的点 C。
 *
 * 原理：C 同时满足 |BC| = L 且 |AC| = sqrt(|AB|^2 - L^2)，
 *      等价于“以 B 为圆心半径 L 的圆”与“以 A 为圆心半径 sqrt(|AB|^2 - L^2) 的圆”的交点。
 *
 * @param A 斜边端点 A
 * @param B 斜边端点 B
 * @param L 边 BC 的长度（>0 且 < |AB|）
 * @param direction 取哪一个交点：+1 / -1，沿 AB 的单位法向量 ±n 的方向
 * @throws IllegalArgumentException 当几何条件不可能（如 L<=0 或 L>=|AB|，或数值误差导致无交点）
 */
fun rightTriangleC(
    A: PointF,
    B: PointF,
    L: Float,
    direction: Int = +1,
    result: PointF
) {
    val eps = 1e-6
    val ax = A.x.toDouble(); val ay = A.y.toDouble()
    val bx = B.x.toDouble(); val by = B.y.toDouble()
    val Ld = L.toDouble()

    // 向量 AB 与长度
    val ux = bx - ax
    val uy = by - ay
    val d = hypot(ux, uy)
    require(d > eps) { "A 与 B 不能重合" }
    require(Ld > eps && Ld < d - eps) { "必须满足 0 < L < |AB|" }

    // 两圆半径：r0 = |AC|, r1 = |BC| = L
    val r1 = Ld
    val r0 = sqrt(d*d - r1*r1)   // 由直角三角形：AC^2 + BC^2 = AB^2

    // AB 方向单位向量 u 与法向量 n
    val uxN = ux / d
    val uyN = uy / d
    val nx = -uyN   // 将 u 逆时针旋转 90°
    val ny =  uxN

    // 圆交公式：从 A 出发，沿 u 到达基点 P2 的距离 a；再沿 ±n 偏移 h
    val a = (r0*r0 - r1*r1 + d*d) / (2.0 * d)
    val h2 = r0*r0 - a*a
    require(h2 >= -1e-8) { "无交点（数值或参数不合法）" }
    val h = sqrt(max(0.0, h2))   // 夹逼避免 -0

    // 基点 P2（在 AB 线上）
    val p2x = ax + a * uxN
    val p2y = ay + a * uyN

    // 选择方向（+1 或 -1）
    val s = if (direction >= 0) +1.0 else -1.0

    // 交点：P2 ± h * n
    val cx = p2x + s * h * nx
    val cy = p2y + s * h * ny
    result.x = cx.toFloat()
    result.y = cy.toFloat()
}

/**
 * 求解直线P1P2和直线P3P4的交点坐标，并将交点坐标保存到result中
 */
fun computeCrossPoint(p1: PointF, p2: PointF, p3: PointF, p4: PointF, result: PointF): Boolean {
    val a1 = p2.y - p1.y
    val b1 = p1.x - p2.x
    val c1 = a1 * p1.x + b1 * p1.y

    val a2 = p4.y - p3.y
    val b2 = p3.x - p4.x
    val c2 = a2 * p3.x + b2 * p3.y

    val det = a1 * b2 - a2 * b1
    if (det == 0f) {
        // 平行或重合，没交点
        return false
    }

    result.x = (b2 * c1 - b1 * c2) / det
    result.y = (a1 * c2 - a2 * c1) / det
    return true
}


/**
 * 计算P1P2的中点坐标，并保存到result中
 */
fun computeMiddlePoint(p1: PointF, p2: PointF, result: PointF) {
    result.x = (p1.x + p2.x) / 2
    result.y = (p1.y + p2.y) / 2
}

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