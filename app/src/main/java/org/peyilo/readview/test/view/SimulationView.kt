package org.peyilo.readview.test.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * 仿真翻页时有三个区域：A、B、C
 * A区域：当前页区域
 * B区域：下一页区域
 * C区域：当前页背面区域
 */
class SimulationView(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
) : View(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    private val bezierStart1 = PointF()         // 第一条贝塞尔曲线的起始点、终点、控制点
    private val bezierEnd1 = PointF()
    private val bezierControl1 = PointF()

    private val bezierStart2 = PointF()         // 第二条贝塞尔曲线的起始点、终点、控制点
    private val bezierEnd2 = PointF()
    private val bezierControl2 = PointF()

    private val bezierVertex1 = PointF()        // C区域直角三角形（近似为直角，实际上比90°稍大）斜边与两条贝塞尔曲线相切的两个点
    private val bezierVertex2 = PointF()

    private val touchPoint = PointF()                 // 触摸点
    private val cornerVertex = PointF()               // 页脚顶点
    private val middlePoint = PointF()                // 触摸点、页脚定点连线的中点
    private val m1 = PointF()                         // bezierStart1、bezierEnd1连线的中点
    private val m2 = PointF()                         // bezierStart2、bezierEnd2连线的中点


    // 计算各个点坐标，假设调用该函数之前touchPoint、cornerVertex已经初始化
    private fun calcPoints() {
        calcMiddlePoint(touchPoint, cornerVertex, middlePoint)
        bezierControl1.y = cornerVertex.y
        bezierControl2.x = cornerVertex.x
        // 设touchPoint和cornerVertex连线为直线L1，过middlePoint作L1的垂直平分线L2
        // 与矩形屏幕边界相交于bezierControl1、bezierControl2
        val k1 = (touchPoint.y - cornerVertex.y) / (touchPoint.x - cornerVertex.x)  // 直线L1斜率
        val k2 = -1/k1      // 直线L2斜率
        bezierControl1.x = middlePoint.x + (cornerVertex.y - middlePoint.y) / k2
        bezierControl2.y = middlePoint.y + (cornerVertex.x - middlePoint.x) * k2
        // 设bezierStart1、bezierStart2连线为L3，则L3为touchPoint、middlePoint线段的垂直平分线
        bezierStart1.y = cornerVertex.y
        bezierStart1.x = bezierControl1.x - (cornerVertex.x - bezierControl1.x) / 2
        bezierStart2.x = cornerVertex.x
        bezierStart2.y = bezierControl2.y - (cornerVertex.y- bezierControl2.y) / 2
        // 设bezierEnd1为bezierControl1和touchPoint连线与bezierStart1、bezierStart2连线的交点
        // 设bezierEnd1为bezierControl2和touchPoint连线与bezierStart1、bezierStart2连线的交点
        calcCrossPoint(touchPoint, bezierControl1, bezierStart1, bezierStart2, bezierEnd1)
        calcCrossPoint(touchPoint, bezierControl2, bezierStart1, bezierStart2, bezierEnd2)
        calcMiddlePoint(bezierStart1, bezierEnd1, m1)
        calcMiddlePoint(bezierStart2, bezierEnd2, m2)
        calcMiddlePoint(m1, bezierControl1, bezierVertex1)      // bezierVertex1为m1、bezierControl1连线的中点
        calcMiddlePoint(m2, bezierControl2, bezierVertex2)      // bezierVertex2为m2、bezierControl2连线的中点
    }

    /**
     * 求解直线P1P2和直线P3P4的交点坐标，并将交点坐标保存到result中
     */
    private fun calcCrossPoint(P1: PointF, P2: PointF, P3: PointF, P4: PointF, result: PointF) {
        // 二元函数通式： y=ax+b
        val a1 = (P2.y - P1.y) / (P2.x - P1.x)
        val b1 = (P1.x * P2.y - P2.x * P1.y) / (P1.x - P2.x)
        val a2 = (P4.y - P3.y) / (P4.x - P3.x)
        val b2 = (P3.x * P4.y - P4.x * P3.y) / (P3.x - P4.x)
        result.x = (b2 - b1) / (a1 - a2)
        result.y = a1 * result.x + b1
    }

    // 计算P1P2的中点坐标，并保存到result中
    private fun calcMiddlePoint(P1: PointF, P2: PointF, result: PointF) {
        result.x = (P1.x + P2.x) / 2
        result.y = (P1.y + P2.y) / 2
    }

    private val pathA = Path()
    private val pathC = Path()
    private val pathB = Path()

    private fun calcPaths() {
        // 计算A区域的边界
        pathA.reset()
        pathA.moveTo(bezierStart1.x, bezierStart1.y)
        pathA.quadTo(bezierControl1.x, bezierControl1.y, bezierEnd1.x, bezierEnd1.y)
        pathA.lineTo(touchPoint.x, touchPoint.y)
        pathA.lineTo(bezierEnd2.x, bezierEnd2.y)
        pathA.quadTo(bezierControl2.x, bezierControl2.y, bezierStart2.x, bezierStart2.y)
        pathA.lineTo(width.toFloat(), height.toFloat() - cornerVertex.y)   // 根据cornerVertex位置不同（左上角或者右上角）绘制区域A
        pathA.lineTo(0F, height.toFloat() - cornerVertex.y)
        pathA.lineTo(0F, cornerVertex.y)
        pathA.close()
        // 计算C区域的边界
        pathC.reset()
        pathC.moveTo(bezierVertex1.x, bezierVertex1.y)      // 将曲线简化为直线，再减去与A区域相交的区域即可获得C区域
        pathC.lineTo(bezierEnd1.x, bezierEnd1.y)
        pathC.lineTo(touchPoint.x, touchPoint.y)
        pathC.lineTo(bezierEnd2.x, bezierEnd2.y)
        pathC.lineTo(bezierVertex2.x, bezierVertex2.y)
        pathC.close()
        pathC.op(pathA, Path.Op.DIFFERENCE)
        // 计算B区域的边界
        pathB.reset()
        pathB.moveTo(0F, 0F)
        pathB.lineTo(width.toFloat(), 0F)
        pathB.lineTo(width.toFloat(), height.toFloat())
        pathB.lineTo(0F, height.toFloat())
        pathB.close()               // 先取整个视图区域，然后减去A和C区域即可获得B区域
        pathB.op(pathA, Path.Op.DIFFERENCE)
        pathB.op(pathC, Path.Op.DIFFERENCE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cornerVertex.set(width.toFloat(), 0F)
        touchPoint.set(200F, height - 1800F)
        calcPoints()
        calcPaths()
    }

    private val regionAPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val regionCPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val regionBPaint = Paint().apply {
        color = "#3F9FFF".toColorInt()
        style = Paint.Style.FILL
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(pathA, regionAPaint)
        canvas.drawPath(pathC, regionCPaint)
        canvas.drawPath(pathB, regionBPaint)
    }

    private fun decideCorner(y: Float) {
        if (y > height / 2) {     // 右下角顶点
            cornerVertex.x = width.toFloat()
            cornerVertex.y = height.toFloat()
        } else {                // 右上角顶点
            cornerVertex.x = width.toFloat()
            cornerVertex.y = 0F
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchPoint.x = event.x
        touchPoint.y = event.y
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                decideCorner(event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                calcPoints()
                calcPaths()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return true
    }
}