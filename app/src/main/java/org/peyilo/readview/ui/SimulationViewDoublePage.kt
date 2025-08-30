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
import android.widget.Scroller
import androidx.core.graphics.toColorInt
import org.peyilo.libreadview.utils.reflectPointAboutLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

class SimulationViewDoublePage(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): View(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    init {
        setBackgroundColor(Color.GRAY)
    }

    private var isFlipping = false

    private val topLeftPoint = PointF()
    private val topMiddlePoint = PointF()
    private val topRightPoint = PointF()
    private val bottomLeftPoint = PointF()
    private val bottomMiddlePoint = PointF()
    private val bottomRightPoint = PointF()

    private val rightPageRegion = Path()
    private val leftPageRigion = Path()
    private val allPageRegion = Path()

    private val viewVerticalPadding = 200F
    private val viewHorizontalPadding = 400F
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

    // TODO: 不应该仅和dy有关系，如果没有触及装订线时，应该保持正常的radius
    private val cylinderRadius: Float get() {
        val radius = pageHeight * 0.075F
        val dy = abs(touchPos.y - downPos.y)
        val scale = 3
        return if (dy >= radius * scale) {
            radius
        } else {
            dy / scale
        }
    }

    private val touchPos = PointF()
    private val downPos = PointF()

    // 以下四个点用于touchPos.y == downPos.y时的水平滑动
    private val landscapeP1 = PointF()       // 左上角
    private val landscapeP2 = PointF()       // 右上角
    private val landscapeP3 = PointF()       // 左下角
    private val landscapeP4 = PointF()       // 右下角

    // 以下两个点用于只剩下一条sine曲线的情形
    private val sineP1 = PointF()
    private val sineP2 = PointF()
    
    private val cornerPos = PointF()
    private val cornerProjPos = PointF()
    
    private val endPos = PointF()
    private val cylinderAxisPos = PointF()
    private val cylinderEnglePos = PointF()
    private val cylinderAxisProjPos = PointF()
    private val cylinderEngleProjPos = PointF()

    private val cylinderAxisLineStartPos = PointF()
    private val cylinderAxisLineEndPos = PointF()
    private val cylinderEngleLineStartPos = PointF()
    private val cylinderEngleLineEndPos = PointF()

    private val cylinderAxisLineProjStartPos = PointF()
    private val cylinderAxisLineProjEndPos = PointF()
    private val cylinderEngleLineProjStartPos = PointF()
    private val cylinderEngleLineProjEndPos = PointF()

    private val sineStartPos1 = PointF()
    private val sineStartPos2 = PointF()
    private val sineMaxPos1 = PointF()
    private val sineMaxPos2 = PointF()

    private val pathA = Path()
    private val pathB = Path()
    private val pathC = Path()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        topLeftPoint.x = viewHorizontalPadding
        topLeftPoint.y = viewVerticalPadding
        topRightPoint.x = width.toFloat() - viewHorizontalPadding
        topRightPoint.y = viewVerticalPadding
        bottomLeftPoint.x = viewHorizontalPadding
        bottomLeftPoint.y = height.toFloat() - viewVerticalPadding
        bottomRightPoint.x = width.toFloat() - viewHorizontalPadding
        bottomRightPoint.y = height.toFloat() - viewVerticalPadding
        topMiddlePoint.x = (topRightPoint.x + topLeftPoint.x) / 2
        topMiddlePoint.y = viewVerticalPadding
        bottomMiddlePoint.x = (bottomLeftPoint.x + bottomRightPoint.x) / 2
        bottomMiddlePoint.y = height.toFloat() - viewVerticalPadding
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
        allPageRegion.apply {
            addPath(leftPageRigion)
            addPath(rightPageRegion)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(leftPageRigion, greenPaint)
        canvas.drawPath(rightPageRegion, yellowPaint)
        if (isFlipping) {
            val pointMode = computePoints()
            computePaths(pointMode)
            canvas.drawPath(pathC, purplePaint)
            canvas.drawPath(pathB, bluePaint)
            canvas.drawPath(pathA, yellowPaint)
            debug(canvas)
        }
    }

    companion object {
        private const val TAG = "SimulationViewDoublePag"
    }

    private val scroller = Scroller(context)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchPos.x = event.x
                touchPos.y = event.y
                if (!isFlipping) {
                    isFlipping = true
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                scroller.startScroll(
                    touchPos.x.toInt(), touchPos.y.toInt(),
                    - touchPos.x.toInt() + topLeftPoint.x.toInt(),
                    - touchPos.y.toInt() + downPos.y.toInt(), 2000)
                invalidate()
            }
        }
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            touchPos.x = scroller.currX.toFloat()
            touchPos.y = scroller.currY.toFloat()
            invalidate()
        }
    }

    private val debugPosPaint = Paint().apply {
        textSize = 15F
        color = Color.RED
    }

    private val debugLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1F
        color = Color.BLACK
    }

    private fun Canvas.drawPoint(text: String, x: Float, y: Float,) {
        drawCircle(x, y, 6F, debugLinePaint)
        val length = debugPosPaint.measureText(text)
        var dy = 0F
        if (y <= 0F) {
            dy = debugPosPaint.textSize
        } else if (y >= pageHeight) {
            dy = -debugPosPaint.textSize
        }
        drawText(text, x - length / 2, y + dy, debugPosPaint)
    }

    enum class CornerMode {
        TopRightCorner, BottomRightCorner, Landscape
    }

    private fun getCornerMode(): CornerMode = when {
        downPos.y < touchPos.y -> CornerMode.TopRightCorner
        downPos.y > touchPos.y -> CornerMode.BottomRightCorner
        else -> CornerMode.Landscape
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

    private fun computeLandscapeWidth(allWidth: Float, cylinderRadius: Float): Float {
        val cylinderHalfCircumference = (PI * cylinderRadius).toFloat()
        if (allWidth > cylinderHalfCircumference) {     // 大于1/2圆周长时,有一部分是平铺的
            return allWidth - cylinderHalfCircumference + cylinderRadius
        } else if (allWidth >= 0.5 * cylinderHalfCircumference) {   // 介于1/4圆周长到1/2圆周长之间时
            val theta = allWidth / cylinderRadius
            return cylinderRadius * (1 - sin(theta))
        } else {        // 小于1/4圆周长时
            return 0F
        }
    }

    /**
     * 求解直线P1P2和直线P3P4的交点坐标，并将交点坐标保存到result中
     */
    private fun computeCrossPoint(p1: PointF, p2: PointF, p3: PointF, p4: PointF, result: PointF) {
        // 二元函数通式： y=ax+b
        val a1 = (p2.y - p1.y) / (p2.x - p1.x)
        val b1 = (p1.x * p2.y - p2.x * p1.y) / (p1.x - p2.x)
        val a2 = (p4.y - p3.y) / (p4.x - p3.x)
        val b2 = (p3.x * p4.y - p4.x * p3.y) / (p3.x - p4.x)
        result.x = (b2 - b1) / (a1 - a2)
        result.y = a1 * result.x + b1
    }

    private fun computePoints(): Int {
        val cornerMode = getCornerMode()

        // process origin point
        var mouseDirX = downPos.x - touchPos.x
        var mouseDirY = downPos.y - touchPos.y
        var mouseLength = hypot(mouseDirX, mouseDirY)
        mouseDirX /= mouseLength
        mouseDirY /= mouseLength

        // process end point
        endPos.x = topRightPoint.x
        endPos.y = downPos.y + mouseDirY / mouseDirX * (endPos.x - downPos.x)

        // process axis point
        var L = hypot(endPos.x - downPos.x, endPos.y - downPos.y)
        cylinderAxisPos.x = touchPos.x + mouseDirX * L
        cylinderAxisPos.y = touchPos.y + mouseDirY * L

        if (cornerMode == CornerMode.Landscape) {
            // 在水平滑动状态时，只需要确定四个点的坐标即可确定A、B、C三个区域
            landscapeP1.y = topMiddlePoint.y
            landscapeP2.y = topMiddlePoint.y
            landscapeP3.y = bottomMiddlePoint.y
            landscapeP4.y = bottomMiddlePoint.y

            val allWidth = topRightPoint.x - cylinderAxisPos.x
            assert(allWidth >= 0)
            val landscapeWidth = computeLandscapeWidth(allWidth, cylinderRadius)
            landscapeP1.x = cylinderEnglePos.x - landscapeWidth
            landscapeP2.x = cylinderEnglePos.x
            landscapeP3.x = cylinderEnglePos.x - landscapeWidth
            landscapeP4.x = cylinderEnglePos.x
            return 0
        }

        // process axis line
        // 正交于mouseDir，(mouseDirY, -mouseDirX)
        if (cornerMode == CornerMode.TopRightCorner) {
            cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - topMiddlePoint.y)
            cylinderAxisLineStartPos.y = topMiddlePoint.y

            cylinderAxisLineEndPos.x = topRightPoint.x
            cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
        } else if (cornerMode == CornerMode.BottomRightCorner) {
            cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - bottomMiddlePoint.y)
            cylinderAxisLineStartPos.y = bottomMiddlePoint.y

            cylinderAxisLineEndPos.x = topRightPoint.x
            cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
        }

        // 限制页面左侧不能翻起来, 模拟真实书籍的装订
        if (cylinderAxisLineStartPos.x < topMiddlePoint.x) {
            if (cornerMode == CornerMode.TopRightCorner) {
                perpendicularFoot(topMiddlePoint.x, topMiddlePoint.y,
                    touchPos.x, touchPos.y,
                    downPos.x, downPos.y,
                    cylinderAxisPos
                )
                touchPos.x = cylinderAxisPos.x - mouseDirX * L
                touchPos.y = cylinderAxisPos.y - mouseDirY * L
            } else if (cornerMode == CornerMode.BottomRightCorner) {
                perpendicularFoot(bottomMiddlePoint.x, bottomMiddlePoint.y,
                    touchPos.x, touchPos.y,
                    downPos.x, downPos.y,
                    cylinderAxisPos
                )
                touchPos.x = cylinderAxisPos.x - mouseDirX * L
                touchPos.y = cylinderAxisPos.y - mouseDirY * L
            }

            // touchPoint更新后，需要重新计算与touchPoint有关系的坐标
            // process origin point
            mouseDirX = downPos.x - touchPos.x
            mouseDirY = downPos.y - touchPos.y
            mouseLength = hypot(mouseDirX, mouseDirY)
            mouseDirX /= mouseLength
            mouseDirY /= mouseLength

            // process end point
            endPos.x = topRightPoint.x
            endPos.y = downPos.y + mouseDirY / mouseDirX * (endPos.x - downPos.x)

            // process axis point
            L = hypot(endPos.x - downPos.x, endPos.y - downPos.y)
            cylinderAxisPos.x = touchPos.x + mouseDirX * L
            cylinderAxisPos.y = touchPos.y + mouseDirY * L

            // process axis line
            // 正交于mouseDir，(mouseDirY, -mouseDirX)
            if (cornerMode == CornerMode.TopRightCorner) {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - topMiddlePoint.y)
                cylinderAxisLineStartPos.y = topMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            } else if (cornerMode == CornerMode.BottomRightCorner) {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - bottomMiddlePoint.y)
                cylinderAxisLineStartPos.y = bottomMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            }
        }

        // process engle point
        cylinderEnglePos.x = cylinderAxisPos.x + mouseDirX * cylinderRadius
        cylinderEnglePos.y = cylinderAxisPos.y + mouseDirY * cylinderRadius

        // process engle project point
        cylinderEngleProjPos.x = (cylinderAxisPos.x + mouseDirX * cylinderRadius * 0.5 * PI).toFloat()
        cylinderEngleProjPos.y = (cylinderAxisPos.y + mouseDirY * cylinderRadius * 0.5 * PI).toFloat()

        // process axis project point
        cylinderAxisProjPos.x = (cylinderAxisPos.x + mouseDirX * cylinderRadius * PI).toFloat()
        cylinderAxisProjPos.y = (cylinderAxisPos.y + mouseDirY * cylinderRadius * PI).toFloat()


        if (cornerMode == CornerMode.TopRightCorner) {
            // process corner point
            cornerPos.x = topRightPoint.x
            cornerPos.y = topRightPoint.y

            // process engle line
            cylinderEngleLineStartPos.x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - topMiddlePoint.y)
            cylinderEngleLineStartPos.y = topMiddlePoint.y
            cylinderEngleLineEndPos.x =  topRightPoint.x
            cylinderEngleLineEndPos.y = cylinderEnglePos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineEndPos.x - cylinderEnglePos.x)

            // process engle project line
            cylinderEngleLineProjStartPos.x = cylinderEngleProjPos.x + mouseDirY / mouseDirX * (cylinderEngleProjPos.y - topMiddlePoint.y)
            cylinderEngleLineProjStartPos.y = topMiddlePoint.y
            cylinderEngleLineProjEndPos.x =  topRightPoint.x
            cylinderEngleLineProjEndPos.y = cylinderEngleProjPos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineProjEndPos.x - cylinderEngleProjPos.x)

            // process axis project line
            cylinderAxisLineProjStartPos.x = cylinderAxisProjPos.x + mouseDirY / mouseDirX * (cylinderAxisProjPos.y - topMiddlePoint.y)
            cylinderAxisLineProjStartPos.y = topMiddlePoint.y
            cylinderAxisLineProjEndPos.x =  topRightPoint.x
            cylinderAxisLineProjEndPos.y = cylinderAxisProjPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineProjEndPos.x - cylinderAxisProjPos.x)
        } else if (cornerMode == CornerMode.BottomRightCorner) {
            // process corner point
            cornerPos.x = bottomRightPoint.x
            cornerPos.y = bottomRightPoint.y

            // process engle line
            cylinderEngleLineStartPos.x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - bottomMiddlePoint.y)
            cylinderEngleLineStartPos.y = bottomMiddlePoint.y
            cylinderEngleLineEndPos.x =  topRightPoint.x
            cylinderEngleLineEndPos.y = cylinderEnglePos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineEndPos.x - cylinderEnglePos.x)

            // process engle project line
            cylinderEngleLineProjStartPos.x = cylinderEngleProjPos.x + mouseDirY / mouseDirX * (cylinderEngleProjPos.y - bottomMiddlePoint.y)
            cylinderEngleLineProjStartPos.y = bottomMiddlePoint.y
            cylinderEngleLineProjEndPos.x =  topRightPoint.x
            cylinderEngleLineProjEndPos.y = cylinderEngleProjPos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineProjEndPos.x - cylinderEngleProjPos.x)

            // process axis project line
            cylinderAxisLineProjStartPos.x = cylinderAxisProjPos.x + mouseDirY / mouseDirX * (cylinderAxisProjPos.y - bottomMiddlePoint.y)
            cylinderAxisLineProjStartPos.y = bottomMiddlePoint.y
            cylinderAxisLineProjEndPos.x =  topRightPoint.x
            cylinderAxisLineProjEndPos.y = cylinderAxisProjPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineProjEndPos.x - cylinderAxisProjPos.x)
        }

        // 处理镜像对称点: 关于cylinderEngleProjLine的镜像
        reflectPointAboutLine(cornerPos.x, cornerPos.y,
            cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
            cornerProjPos.x = first
            cornerProjPos.y = second
        }
        reflectPointAboutLine(cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y,
            cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
            sineStartPos1.x = first
            sineStartPos1.y = second
        }
        reflectPointAboutLine(cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y,
            cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
            sineStartPos2.x = first
            sineStartPos2.y = second
        }

        // 处理正弦波最高点
        sineMaxPos1.x = (cylinderAxisLineStartPos.x + sineStartPos1.x) / 2
        sineMaxPos1.y = (cylinderAxisLineStartPos.y + sineStartPos1.y) / 2
        sineMaxPos2.x = (cylinderAxisLineEndPos.x + sineStartPos2.x) / 2
        sineMaxPos2.y = (cylinderAxisLineEndPos.y + sineStartPos2.y) / 2
        sineMaxPos1.x = sineMaxPos1.x + mouseDirX * cylinderRadius
        sineMaxPos1.y = sineMaxPos1.y + mouseDirY * cylinderRadius
        sineMaxPos2.x = sineMaxPos2.x + mouseDirX * cylinderRadius
        sineMaxPos2.y = sineMaxPos2.y + mouseDirY * cylinderRadius

        // 处理只显示一条正弦曲线的情形
        // 当sineStartPos2处于屏幕之外时，只会显示sineStartPos1的正弦曲线
        // 此时需要额外增加两个点，用于确定A、B、C三个区域(之所以要这样做，是因为当一条sine曲线消失时，如果依旧按照两条sine曲线来计算区域，由于此时
        // 有些点的坐标其实已经接近无穷大了，导致计算不准确，从而出现渲染错误)
        if (abs(cornerPos.y - sineStartPos2.y) >= pageHeight) {
            if (cornerMode == CornerMode.TopRightCorner) {
                reflectPointAboutLine(bottomRightPoint.x, bottomRightPoint.y,
                    cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
                    cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
                    computeCrossPoint(bottomLeftPoint, bottomRightPoint,
                        cornerProjPos, PointF(first, second), sineP1)
                }
                val x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - bottomMiddlePoint.y)
                computeCrossPoint(bottomLeftPoint, bottomRightPoint,
                    cylinderEngleLineStartPos, PointF(x, bottomMiddlePoint.y), sineP2)
            } else if (cornerMode == CornerMode.BottomRightCorner) {
                reflectPointAboutLine(topRightPoint.x, topRightPoint.y,
                    cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
                    cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
                    computeCrossPoint(topLeftPoint, topRightPoint,
                        cornerProjPos, PointF(first, second), sineP1)
                }
                val x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - topMiddlePoint.y)
                computeCrossPoint(topLeftPoint, topRightPoint,
                    cylinderEngleLineStartPos, PointF(x, topMiddlePoint.y), sineP2)
            }
            return 2
        } else {
            return 1
        }
    }

    private fun debug(canvas: Canvas) {
        canvas.drawPoint("Down", downPos.x, downPos.y)
        canvas.drawPoint("Touch", touchPos.x, touchPos.y)
        canvas.drawLine(downPos.x, downPos.y, touchPos.x, touchPos.y, debugLinePaint)
        canvas.drawPoint("EndPos", endPos.x, endPos.y)
        canvas.drawLine(endPos.x, endPos.y, downPos.x, downPos.y, debugLinePaint)
        canvas.drawPoint("Axis", cylinderAxisPos.x, cylinderAxisPos.y)
        canvas.drawPoint("AxisStart", cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
        canvas.drawPoint("AxisEnd", cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y)
        canvas.drawLine(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y,
            cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y, debugLinePaint)
        canvas.drawPoint("Engle", cylinderEnglePos.x, cylinderEnglePos.y)
        canvas.drawPoint("EngleStart", cylinderEngleLineStartPos.x, cylinderEngleLineStartPos.y)
        canvas.drawPoint("EngleEnd", cylinderEngleLineEndPos.x, cylinderEngleLineEndPos.y)
        canvas.drawLine(cylinderEngleLineStartPos.x, cylinderEngleLineStartPos.y,
            cylinderEngleLineEndPos.x, cylinderEngleLineEndPos.y, debugLinePaint)
        canvas.drawPoint("", cylinderEngleProjPos.x, cylinderEngleProjPos.y)
        canvas.drawPoint("Start", cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y)
        canvas.drawPoint("End", cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y)
        canvas.drawLine(cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y, debugLinePaint)
        canvas.drawPoint("", cylinderAxisProjPos.x, cylinderAxisProjPos.y)
        canvas.drawPoint("Start", cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y)
        canvas.drawPoint("End", cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y)
        canvas.drawLine(cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y,
            cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y, debugLinePaint)
        canvas.drawPoint("Corner", cornerProjPos.x, cornerProjPos.y)
        canvas.drawLine(cornerProjPos.x, cornerProjPos.y, cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y, debugLinePaint)
        canvas.drawLine(cornerProjPos.x, cornerProjPos.y, cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y, debugLinePaint)
        canvas.drawLine(sineStartPos1.x, sineStartPos1.y, cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y, debugLinePaint)
        canvas.drawLine(sineStartPos2.x, sineStartPos2.y, cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y, debugLinePaint)
        canvas.drawPoint("sineStartPos1", sineStartPos1.x, sineStartPos1.y)
        canvas.drawPoint("sineStartPos2", sineStartPos2.x, sineStartPos2.y)
        canvas.drawLine(cornerProjPos.x, cornerProjPos.y, sineStartPos1.x, sineStartPos1.y, debugLinePaint)
        canvas.drawLine(cornerProjPos.x, cornerProjPos.y, sineStartPos2.x, sineStartPos2.y, debugLinePaint)
        val deltaX1 = hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)
        val deltaX2 = hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)
        drawHalfSineCurve(
            canvas, debugLinePaint,
            sineStartPos1.x, sineStartPos1.y,
            cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y,
            cylinderRadius, deltaX1, direction = if (getCornerMode() == CornerMode.TopRightCorner) 1 else -1
        )
        drawHalfSineCurve(
            canvas, debugLinePaint,
            sineStartPos2.x, sineStartPos2.y,
            cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y,
            cylinderRadius, deltaX2, direction = if (getCornerMode() == CornerMode.TopRightCorner) -1 else 1
        )
        canvas.drawPoint("", sineMaxPos1.x, sineMaxPos1.y)
        canvas.drawPoint("", sineMaxPos2.x, sineMaxPos2.y)

        canvas.drawPoint("landscapeP1", landscapeP1.x, landscapeP1.y)
        canvas.drawPoint("landscapeP2", landscapeP2.x, landscapeP2.y)
        canvas.drawPoint("landscapeP3", landscapeP3.x, landscapeP3.y)
        canvas.drawPoint("landscapeP4", landscapeP4.x, landscapeP4.y)
    }

    private fun computePaths(pointMode: Int) {
        when (pointMode) {
            0 -> {
                pathB.reset()
                pathB.moveTo(landscapeP2.x, landscapeP2.y)
                pathB.lineTo(topRightPoint.x, topRightPoint.y)
                pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                pathB.lineTo(landscapeP4.x, landscapeP4.y)
                pathB.close()
                pathB.op(rightPageRegion, Path.Op.INTERSECT)

                pathC.reset()
                pathC.moveTo(landscapeP1.x, landscapeP1.y)
                pathC.lineTo(landscapeP2.x, landscapeP2.y)
                pathC.lineTo(landscapeP4.x, landscapeP4.y)
                pathC.lineTo(landscapeP3.x, landscapeP3.y)
                pathC.close()
                pathC.op(allPageRegion, Path.Op.INTERSECT)
            }
            1 -> {
                val cornerMode = getCornerMode()
                pathB.reset()
                pathB.moveTo(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                if (cornerMode == CornerMode.TopRightCorner) {
                    pathB.lineTo(topRightPoint.x, topRightPoint.y)
                } else if (cornerMode == CornerMode.BottomRightCorner) {
                    pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                }
                pathB.lineTo(cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y)
                pathB.addQuarterSineFromHalfPeriod(
                    startX = cylinderAxisLineEndPos.x,
                    startY = cylinderAxisLineEndPos.y,
                    endX = sineStartPos2.x,
                    endY = sineStartPos2.y,
                    amplitude = cylinderRadius,
                    angularFrequency = (PI / hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)).toFloat(),
                    direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                )
                pathB.lineTo(sineMaxPos1.x, sineMaxPos1.y)
                pathB.addQuarterSineFromHalfPeriod(
                    startX = sineStartPos1.x,
                    startY = sineStartPos1.y,
                    endX = cylinderAxisLineStartPos.x,
                    endY = cylinderAxisLineStartPos.y,
                    amplitude = cylinderRadius,
                    angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
                    isFirstQuarter = false,
                    direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                )
                pathB.close()
                pathB.op(rightPageRegion, Path.Op.INTERSECT)

                pathC.reset()
                pathC.moveTo(sineMaxPos1.x, sineMaxPos1.y)
                pathC.lineTo(sineMaxPos2.x, sineMaxPos2.y)
                pathC.addQuarterSineFromHalfPeriod(
                    startX = cylinderAxisLineEndPos.x,
                    startY = cylinderAxisLineEndPos.y,
                    endX = sineStartPos2.x,
                    endY = sineStartPos2.y,
                    amplitude = cylinderRadius,
                    angularFrequency = (PI / hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)).toFloat(),
                    isFirstQuarter = false,
                    direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                )
                pathC.lineTo(cornerProjPos.x, cornerProjPos.y)
                pathC.lineTo(sineStartPos1.x, sineStartPos1.y)
                pathC.addQuarterSineFromHalfPeriod(
                    startX = sineStartPos1.x,
                    startY = sineStartPos1.y,
                    endX = cylinderAxisLineStartPos.x,
                    endY = cylinderAxisLineStartPos.y,
                    amplitude = cylinderRadius,
                    angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
                    direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                )
                pathC.close()
                pathC.op(allPageRegion, Path.Op.INTERSECT)
            }
            2 -> {
                val cornerMode = getCornerMode()
                val interval = hypot(cylinderAxisLineStartPos.x - sineStartPos1.x, cylinderAxisLineStartPos.y - sineStartPos1.y)
                val useDirectly = interval < 5F
                // 之所以额外添加这个判断，是因为当cylinderAxisLineStartPos和sineStartPos1非常接近时，
                // 如果依旧按照正弦曲线来绘制，会导致渲染错误，出现屏幕闪烁的现象
                if (useDirectly) {
                    pathB.reset()
                    pathB.moveTo(cylinderEngleLineStartPos.x, cylinderEngleLineStartPos.y)
                    if (cornerMode == CornerMode.TopRightCorner) {
                        pathB.lineTo(topRightPoint.x, topRightPoint.y)
                        pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                    } else if (cornerMode == CornerMode.BottomRightCorner) {
                        pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                        pathB.lineTo(topRightPoint.x, topRightPoint.y)
                    }
                    pathB.lineTo(sineP2.x, sineP2.y)
                    pathB.close()
                    pathB.op(rightPageRegion, Path.Op.INTERSECT)

                    pathC.reset()
                    pathC.moveTo(cylinderEngleLineStartPos.x, cylinderEngleLineStartPos.y)
                    pathC.lineTo(sineP2.x, sineP2.y)
                    pathC.lineTo(sineP1.x, sineP1.y)
                    pathC.lineTo(cornerProjPos.x, cornerProjPos.y)
                    pathC.lineTo(sineStartPos1.x, sineStartPos1.y)
                    pathC.close()
                    pathC.op(allPageRegion, Path.Op.INTERSECT)
                } else {
                    pathB.reset()
                    pathB.moveTo(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                    if (cornerMode == CornerMode.TopRightCorner) {
                        pathB.lineTo(topRightPoint.x, topRightPoint.y)
                        pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                    } else if (cornerMode == CornerMode.BottomRightCorner) {
                        pathB.lineTo(bottomRightPoint.x, bottomRightPoint.y)
                        pathB.lineTo(topRightPoint.x, topRightPoint.y)
                    }
                    pathB.lineTo(sineP2.x, sineP2.y)
                    pathB.lineTo(sineMaxPos1.x, sineMaxPos1.y)
                    pathB.addQuarterSineFromHalfPeriod(
                        startX = sineStartPos1.x,
                        startY = sineStartPos1.y,
                        endX = cylinderAxisLineStartPos.x,
                        endY = cylinderAxisLineStartPos.y,
                        amplitude = cylinderRadius,
                        angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
                        isFirstQuarter = false,
                        direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                    )
                    pathB.close()
                    pathB.op(rightPageRegion, Path.Op.INTERSECT)

                    pathC.reset()
                    pathC.moveTo(sineMaxPos1.x, sineMaxPos1.y)
                    pathC.lineTo(sineP2.x, sineP2.y)
                    pathC.lineTo(sineP1.x, sineP1.y)
                    pathC.lineTo(cornerProjPos.x, cornerProjPos.y)
                    pathC.lineTo(sineStartPos1.x, sineStartPos1.y)
                    pathC.addQuarterSineFromHalfPeriod(
                        startX = sineStartPos1.x,
                        startY = sineStartPos1.y,
                        endX = cylinderAxisLineStartPos.x,
                        endY = cylinderAxisLineStartPos.y,
                        amplitude = cylinderRadius,
                        angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
                        direction = if (cornerMode == CornerMode.TopRightCorner) 1 else -1
                    )
                    pathC.close()
                    pathC.op(allPageRegion, Path.Op.INTERSECT)
                }
            }
            else -> throw IllegalStateException("computePaths: pointMode=$pointMode is not supported.")
        }
        pathA.reset()
        pathA.moveTo(topMiddlePoint.x, topMiddlePoint.y)
        pathA.lineTo(topRightPoint.x, topRightPoint.y)
        pathA.lineTo(bottomRightPoint.x, bottomRightPoint.y)
        pathA.lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
        pathA.close()
        pathA.op(pathB, Path.Op.DIFFERENCE)
        pathA.op(pathC, Path.Op.DIFFERENCE)
    }


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

        for (i in 0..steps) {
            val xVal = i.toFloat() / steps * quarterLength + if (isFirstQuarter) 0F else quarterLength
            val sinVal = amplitude * sin(angularFrequency * xVal) * direction

            val x = startX + ux * xVal + vx * sinVal
            val y = startY + uy * xVal + vy * sinVal
            lineTo(x, y)
        }
    }


}


