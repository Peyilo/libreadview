package org.peyilo.libreadview.manager.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
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



fun findIntersections(path: Path, p1: PointF, p2: PointF, precision: Int = 1000): List<PointF> {
    val result = mutableListOf<PointF>()

    // 用 PathMeasure 遍历 Path
    val pm = PathMeasure(path, true)
    val length = pm.length
    val pos = FloatArray(2)

    // 采样点
    val points = ArrayList<PointF>()
    for (i in 0..precision) {
        pm.getPosTan(length * i / precision, pos, null)
        points.add(PointF(pos[0], pos[1]))
    }

    // 遍历相邻点，作为边段
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val cross = computeLineSegmentIntersectionWithLine(a, b, p1, p2)
        if (cross != null) {
            result.add(cross)
            if (result.size == 2) break // 最多两个交点，够了就退出
        }
    }

    return result
}

/**
 * 求线段 ab 与直线 p1p2 的交点, 如果没有交点则返回 null
 */
fun computeLineSegmentIntersectionWithLine(
    a: PointF, b: PointF,
    p1: PointF, p2: PointF
): PointF? {
    val a1 = p2.y - p1.y
    val b1 = p1.x - p2.x
    val c1 = a1 * p1.x + b1 * p1.y

    val a2 = b.y - a.y
    val b2 = a.x - b.x
    val c2 = a2 * a.x + b2 * a.y

    val det = a1 * b2 - a2 * b1
    if (det == 0f) return null // 平行或重合

    val x = (b2 * c1 - b1 * c2) / det
    val y = (a1 * c2 - a2 * c1) / det

    // 检查是否在边 ab 范围内
    if (x < minOf(a.x, b.x) - 0.01f || x > maxOf(a.x, b.x) + 0.01f) return null
    if (y < minOf(a.y, b.y) - 0.01f || y > maxOf(a.y, b.y) + 0.01f) return null

    return PointF(x, y)
}

/**
 * 根据点集绘制折线
 */
fun drawEngleLine(canvas: Canvas, engle: FloatArray, paint: Paint) {
    var lastX = 0F
    var lastY = 0F
    var isFirst = true
    for (i in engle.indices step 2) {
        if (isFirst) {
            lastX = engle[i]
            lastY = engle[i + 1]
            isFirst = false
            continue
        }
        val x = engle[i]
        val y = engle[i + 1]
        canvas.drawLine(lastX, lastY, x, y, paint)
        lastX = x
        lastY = y
    }
}
