package org.peyilo.readview.ui

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
import kotlin.math.abs

class SimulationViewDoublePage(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): View(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    private val topLeftPoint = PointF()
    private val topMiddlePoint = PointF()
    private val topRightPoint = PointF()
    private val bottomLeftPoint = PointF()
    private val bottomMiddlePoint = PointF()
    private val bottomRightPoint = PointF()

    private val rightPageRegion = Path()
    private val leftPageRigion = Path()
    private val viewPadding = 400F
    private val pageWidth get() =  topRightPoint.x - topMiddlePoint.x
    private val pageHeight get() = bottomRightPoint.y - topRightPoint.y

    private val greenPaint = Paint().apply {
        color = "#88FFA6".toColorInt()
        style = Paint.Style.FILL
    }
    private val yellowPaint = Paint().apply {
        color = "#FFF988".toColorInt()
        style = Paint.Style.FILL
    }
    private val bluePaint = Paint().apply {
        color = "#7DB5FF".toColorInt()
        style = Paint.Style.FILL
    }

    private val purplePaint = Paint().apply {
        color = "#D284FF".toColorInt()
        style = Paint.Style.FILL
    }

    private val bezierStart1 = PointF()         // 第一条贝塞尔曲线的起始点、终点、控制点
    private val bezierEnd1 = PointF()
    private val bezierControl1 = PointF()

    private val bezierStart2 = PointF()         // 第二条贝塞尔曲线的起始点、终点、控制点
    private val bezierEnd2 = PointF()
    private val bezierControl2 = PointF()

    private val bezierVertex1 = PointF()        // C区域直角三角形（近似为直角，实际上比90°稍大）斜边与两条贝塞尔曲线相切的两个点
    private val bezierVertex2 = PointF()

    private val touchPoint = PointF()                    // 触摸点
    private val selectedCornerPoint = PointF()
    private val fixedCornerPoint = PointF()            // 页脚顶点
    private val middlePoint = PointF()                // 触摸点、页脚定点连线的中点
    private val m1 = PointF()                         // bezierStart1、bezierEnd1连线的中点
    private val m2 = PointF()                         // bezierStart2、bezierEnd2连线的中点

    private val pathA = Path()
    private val pathC = Path()
    private val pathB = Path()

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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        topLeftPoint.x = viewPadding
        topLeftPoint.y = viewPadding
        topRightPoint.x = width.toFloat() - viewPadding
        topRightPoint.y = viewPadding
        bottomLeftPoint.x = viewPadding
        bottomLeftPoint.y = height.toFloat() - viewPadding
        bottomRightPoint.x = width.toFloat() - viewPadding
        bottomRightPoint.y = height.toFloat() - viewPadding
        topMiddlePoint.x = (topRightPoint.x + topLeftPoint.x) / 2
        topMiddlePoint.y = viewPadding
        bottomMiddlePoint.x = (bottomLeftPoint.x + bottomRightPoint.x) / 2
        bottomMiddlePoint.y = height.toFloat() - viewPadding
        rightPageRegion.apply {
            reset()
            moveTo(topMiddlePoint.x, topMiddlePoint.y)
            lineTo(topRightPoint.x, topRightPoint.y)
            lineTo(bottomRightPoint.x, bottomRightPoint.y)
            lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
            close()
        }
        leftPageRigion.apply {
            reset()
            moveTo(topMiddlePoint.x, topMiddlePoint.y)
            lineTo(topLeftPoint.x, topLeftPoint.y)
            lineTo(bottomLeftPoint.x, bottomLeftPoint.y)
            lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
            close()
        }
    }

    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3F
    }
    private val posPaint = Paint().apply {
        color = Color.BLACK
        textSize = 32F
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(leftPageRigion, greenPaint)
        canvas.drawPath(rightPageRegion, yellowPaint)

//        canvas.withClip(rightPageRegion) {
//            canvas.drawPath(pathA, yellowPaint)
//            canvas.drawPath(pathB, bluePaint)
//            canvas.drawPath(pathC, purplePaint)
//        }

        canvas.drawPath(pathA, yellowPaint)
        canvas.drawPath(pathB, bluePaint)
        canvas.drawPath(pathC, purplePaint)

        debug(canvas)
    }

    private fun debug(canvas: Canvas) {
        canvas.drawText("selectedCornerPoint", selectedCornerPoint.x - 60, selectedCornerPoint.y, posPaint)
        canvas.drawText("middlePoint", middlePoint.x - 60, middlePoint.y, posPaint)

        canvas.drawText("bezierStart1", bezierStart1.x - 60, bezierStart1.y, posPaint)
        canvas.drawText("bezierControl1", bezierControl1.x - 60, bezierControl1.y, posPaint)
        canvas.drawText("bezierEnd1", bezierEnd1.x - 60, bezierEnd1.y, posPaint)

        canvas.drawText("bezierStart2", bezierStart2.x - 60, bezierStart2.y, posPaint)
        canvas.drawText("bezierControl2", bezierControl2.x - 60, bezierControl2.y, posPaint)
        canvas.drawText("bezierEnd2", bezierEnd2.x - 60, bezierEnd2.y, posPaint)

        canvas.drawText("m1", m1.x, m1.y, posPaint)
        canvas.drawText("m2", m2.x, m2.y, posPaint)
        canvas.drawText("bezierVertex1", bezierVertex1.x - 60, bezierVertex1.y, posPaint)
        canvas.drawText("bezierVertex2", bezierVertex2.x - 60, bezierVertex2.y, posPaint)

        canvas.drawText("cornerVertex: (${fixedCornerPoint.x}, ${fixedCornerPoint.y})",
            40F, 80F, posPaint)
        canvas.drawText("touchPoint: (${selectedCornerPoint.x}, ${selectedCornerPoint.y})",
            40F, 120F, posPaint)
        canvas.drawText("middlePoint: (${middlePoint.x}, ${middlePoint.y})",
            40F, 160F, posPaint)

        canvas.drawText("bezierStart1: (${bezierStart1.x}, ${bezierStart1.y})",
            40F, 200F, posPaint)
        canvas.drawText("bezierControl1: (${bezierControl1.x}, ${bezierControl1.y})",
            40F, 240F, posPaint)
        canvas.drawText("bezierEnd1: (${bezierEnd1.x}, ${bezierEnd1.y})",
            40F, 280F, posPaint)

        canvas.drawText("bezierStart2: (${bezierStart2.x}, ${bezierStart2.y})",
            40F, 320F, posPaint)
        canvas.drawText("bezierControl2: (${bezierControl2.x}, ${bezierControl2.y})",
            40F, 360F, posPaint)
        canvas.drawText("bezierEnd2: (${bezierEnd2.x}, ${bezierEnd2.y})",
            40F, 400F, posPaint)

        canvas.drawText("m1: (${m1.x}, ${m1.y})",
            40F, 440F, posPaint)
        canvas.drawText("m2: (${m2.x}, ${m2.y})",
            40F, 480F, posPaint)
        canvas.drawText("bezierVertex1: (${bezierVertex1.x}, ${bezierVertex1.y})",
            40F, 520F, posPaint)
        canvas.drawText("bezierVertex2: (${bezierVertex2.x}, ${bezierVertex2.y})",
            40F, 560F, posPaint)

        canvas.drawLine(fixedCornerPoint.x, fixedCornerPoint.y, selectedCornerPoint.x, selectedCornerPoint.y, linePaint)
        canvas.drawLine(bezierControl1.x, bezierControl1.y, bezierControl2.x, bezierControl2.y, linePaint)
        canvas.drawLine(bezierControl1.x, bezierControl1.y, selectedCornerPoint.x, selectedCornerPoint.y, linePaint)
        canvas.drawLine(bezierControl2.x, bezierControl2.y, selectedCornerPoint.x, selectedCornerPoint.y, linePaint)
        canvas.drawLine(bezierStart1.x, bezierStart1.y, bezierStart2.x, bezierStart2.y, linePaint)
        canvas.drawLine(bezierVertex1.x, bezierVertex1.y, bezierVertex2.x, bezierVertex2.y, linePaint)
        canvas.drawLine(m1.x, m1.y, bezierControl1.x, bezierControl1.y, linePaint)
        canvas.drawLine(m2.x, m2.y, bezierControl2.x, bezierControl2.y, linePaint)
    }

    private fun decideCorner(y: Float) {
        if (y > height / 2) {     // 右下角顶点
            fixedCornerPoint.x = bottomRightPoint.x
            fixedCornerPoint.y = bottomRightPoint.y
        } else {                // 右上角顶点
            fixedCornerPoint.x = topRightPoint.x
            fixedCornerPoint.y = topRightPoint.y
        }
    }

    private fun inPageRegision(x: Float, y: Float): Boolean = (x > topLeftPoint.x && x < topRightPoint.x && y > topLeftPoint.y && y < bottomLeftPoint.y)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                decideCorner(event.y)
            }
            MotionEvent.ACTION_MOVE -> if (inPageRegision(event.x, event.y)) {
                touchPoint.x = event.x
                touchPoint.y = event.y
                calcPoints()
                calcPaths()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return true
    }

    private fun calcPaths() {
        // 计算A区域的边界
        pathA.reset()
        pathA.moveTo(bezierStart1.x, bezierStart1.y)
        pathA.quadTo(bezierControl1.x, bezierControl1.y, bezierEnd1.x, bezierEnd1.y)
        pathA.lineTo(selectedCornerPoint.x, selectedCornerPoint.y)
        pathA.lineTo(bezierEnd2.x, bezierEnd2.y)
        pathA.quadTo(bezierControl2.x, bezierControl2.y, bezierStart2.x, bezierStart2.y)
        pathA.lineTo(fixedCornerPoint.x, bottomRightPoint.y + viewPadding - fixedCornerPoint.y)   // 根据cornerVertex位置不同（左上角或者右上角）绘制区域A
        pathA.lineTo(bottomMiddlePoint.x, bottomRightPoint.y + viewPadding - fixedCornerPoint.y)
        pathA.lineTo(topMiddlePoint.x, fixedCornerPoint.y)
        pathA.close()
        // 计算C区域的边界
        pathC.reset()
        pathC.moveTo(bezierVertex1.x, bezierVertex1.y)      // 将曲线简化为直线，再减去与A区域相交的区域即可获得C区域
        pathC.lineTo(bezierEnd1.x, bezierEnd1.y)
        pathC.lineTo(selectedCornerPoint.x, selectedCornerPoint.y)
        pathC.lineTo(bezierEnd2.x, bezierEnd2.y)
        pathC.lineTo(bezierVertex2.x, bezierVertex2.y)
        pathC.close()
        pathC.op(pathA, Path.Op.DIFFERENCE)
        // 计算B区域的边界
        pathB.reset()
        pathB.set(rightPageRegion)
        // 先取整个视图区域，然后减去A和C区域即可获得B区域
        pathB.op(pathA, Path.Op.DIFFERENCE)
        pathB.op(pathC, Path.Op.DIFFERENCE)
    }

    // 计算各个点坐标，假设调用该函数之前touchPoint、cornerVertex已经初始化
    private fun calcPoints() {
        selectedCornerPoint.x = touchPoint.x
        selectedCornerPoint.y = touchPoint.y
        calcMiddlePoint(selectedCornerPoint, fixedCornerPoint, middlePoint)
        bezierControl1.y = fixedCornerPoint.y
        bezierControl2.x = fixedCornerPoint.x
        // 设touchPoint和cornerVertex连线为直线L1，过middlePoint作L1的垂直平分线L2
        // 与矩形屏幕边界相交于bezierControl1、bezierControl2
        var k1 = (selectedCornerPoint.y - fixedCornerPoint.y) / (selectedCornerPoint.x - fixedCornerPoint.x)  // 直线L1斜率
        var k2 = -1/k1      // 直线L2斜率
        bezierControl1.x = middlePoint.x + (fixedCornerPoint.y - middlePoint.y) / k2
        bezierControl2.y = middlePoint.y + (fixedCornerPoint.x - middlePoint.x) * k2
        // 设bezierStart1、bezierStart2连线为L3，则L3为touchPoint、middlePoint线段的垂直平分线
        bezierStart1.y = fixedCornerPoint.y
        bezierStart1.x = bezierControl1.x - (fixedCornerPoint.x - bezierControl1.x) / 2
        if (touchPoint.x > topMiddlePoint.x) {
            if (bezierStart1.x < topMiddlePoint.x) {
                val w0 = fixedCornerPoint.x - bezierStart1.x           // 限制bezierStart1.x不能小于0
                val w1 = abs(fixedCornerPoint.x - selectedCornerPoint.x)      // 如果小于0，需要对touchPoint进行特殊处理
                val w2 = pageWidth * w1 / w0
                selectedCornerPoint.x = abs(fixedCornerPoint.x - w2)
                val h1 = abs(fixedCornerPoint.y - selectedCornerPoint.y)
                val h2 = w2 * h1 / w1
                selectedCornerPoint.y = if (isTopCorner())  fixedCornerPoint.y + h2 else fixedCornerPoint.y - h2
                // touchPoint更新后，需要重新计算与touchPoint有关系的坐标
                calcMiddlePoint(selectedCornerPoint, fixedCornerPoint, middlePoint)
                k1 = (selectedCornerPoint.y - fixedCornerPoint.y) / (selectedCornerPoint.x - fixedCornerPoint.x)  // 直线L1斜率
                k2 = -1/k1      // 直线L2斜率
                bezierControl1.x = middlePoint.x + (fixedCornerPoint.y - middlePoint.y) / k2
                bezierControl2.y = middlePoint.y + (fixedCornerPoint.x - middlePoint.x) * k2
                bezierStart1.x = bezierControl1.x - (fixedCornerPoint.x - bezierControl1.x) / 2
            }
            bezierStart2.x = fixedCornerPoint.x
            bezierStart2.y = bezierControl2.y - (fixedCornerPoint.y- bezierControl2.y) / 2
            // 设bezierEnd1为bezierControl1和touchPoint连线与bezierStart1、bezierStart2连线的交点
            // 设bezierEnd1为bezierControl2和touchPoint连线与bezierStart1、bezierStart2连线的交点
            calcCrossPoint(selectedCornerPoint, bezierControl1, bezierStart1, bezierStart2, bezierEnd1)
            calcCrossPoint(selectedCornerPoint, bezierControl2, bezierStart1, bezierStart2, bezierEnd2)
            calcMiddlePoint(bezierStart1, bezierEnd1, m1)
            calcMiddlePoint(bezierStart2, bezierEnd2, m2)
            calcMiddlePoint(m1, bezierControl1, bezierVertex1)      // bezierVertex1为m1、bezierControl1连线的中点
            calcMiddlePoint(m2, bezierControl2, bezierVertex2)      // bezierVertex2为m2、bezierControl2连线的中点
        } else {
            if (bezierStart1.x > topMiddlePoint.x) {
                bezierStart2.x = fixedCornerPoint.x
                bezierStart2.y = bezierControl2.y - (fixedCornerPoint.y- bezierControl2.y) / 2
                // 设bezierEnd1为bezierControl1和touchPoint连线与bezierStart1、bezierStart2连线的交点
                // 设bezierEnd1为bezierControl2和touchPoint连线与bezierStart1、bezierStart2连线的交点
                calcCrossPoint(selectedCornerPoint, bezierControl1, bezierStart1, bezierStart2, bezierEnd1)
                calcCrossPoint(selectedCornerPoint, bezierControl2, bezierStart1, bezierStart2, bezierEnd2)
                calcMiddlePoint(bezierStart1, bezierEnd1, m1)
                calcMiddlePoint(bezierStart2, bezierEnd2, m2)
                calcMiddlePoint(m1, bezierControl1, bezierVertex1)      // bezierVertex1为m1、bezierControl1连线的中点
                calcMiddlePoint(m2, bezierControl2, bezierVertex2)      // bezierVertex2为m2、bezierControl2连线的中点
            } else {
                bezierStart2.x = fixedCornerPoint.x
                bezierStart2.y = bezierControl2.y - (fixedCornerPoint.y- bezierControl2.y) / 2
                // 设bezierEnd1为bezierControl1和touchPoint连线与bezierStart1、bezierStart2连线的交点
                // 设bezierEnd1为bezierControl2和touchPoint连线与bezierStart1、bezierStart2连线的交点
                calcCrossPoint(selectedCornerPoint, bezierControl1, bezierStart1, bezierStart2, bezierEnd1)
                calcCrossPoint(selectedCornerPoint, bezierControl2, bezierStart1, bezierStart2, bezierEnd2)
                calcMiddlePoint(bezierStart1, bezierEnd1, m1)
                calcMiddlePoint(bezierStart2, bezierEnd2, m2)
                calcMiddlePoint(m1, bezierControl1, bezierVertex1)      // bezierVertex1为m1、bezierControl1连线的中点
                calcMiddlePoint(m2, bezierControl2, bezierVertex2)      // bezierVertex2为m2、bezierControl2连线的中点


            }
        }
    }

    private fun isTopCorner() = fixedCornerPoint.y == topRightPoint.y

}