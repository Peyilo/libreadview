package org.peyilo.readview.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
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

    private val selectedCornerPos = PointF()
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
            calcPointers()
            calcPaths()
            canvas.drawPath(pathC, purplePaint)
            canvas.drawPath(pathB, bluePaint)
            canvas.drawPath(pathA, yellowPaint)
            if (getMode() == Mode.Landscape) {
                Log.d(TAG, "onDraw: Landscape")
            }
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
        drawText(text, x - length / 2, y + debugPosPaint.textSize / 4, debugPosPaint)
    }

    enum class Mode {
        TopRightCorner, BottomRightCorner, Landscape
    }

    private fun getMode(): Mode = when {
        downPos.y < touchPos.y -> Mode.TopRightCorner
        downPos.y > touchPos.y -> Mode.BottomRightCorner
        else -> Mode.Landscape
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

    private fun calcPointers() {
        val mode = getMode()

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

        // process axis line
        // 正交于mouseDir，(mouseDirY, -mouseDirX)
        if (mode == Mode.TopRightCorner) {
            cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - topMiddlePoint.y)
            cylinderAxisLineStartPos.y = topMiddlePoint.y

            cylinderAxisLineEndPos.x = topRightPoint.x
            cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
        } else if (mode == Mode.BottomRightCorner) {
            cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - bottomMiddlePoint.y)
            cylinderAxisLineStartPos.y = bottomMiddlePoint.y

            cylinderAxisLineEndPos.x = topRightPoint.x
            cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
        }

        // 限制页面左侧不能翻起来, 模拟真实书籍的装订
        if (cylinderAxisLineStartPos.x < topMiddlePoint.x && mode != Mode.Landscape) {
            if (mode == Mode.TopRightCorner) {
                perpendicularFoot(topMiddlePoint.x, topMiddlePoint.y,
                    touchPos.x, touchPos.y,
                    downPos.x, downPos.y,
                    cylinderAxisPos
                    )
                touchPos.x = cylinderAxisPos.x - mouseDirX * L
                touchPos.y = cylinderAxisPos.y - mouseDirY * L
            } else if (mode == Mode.BottomRightCorner) {
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

            // draw axis line
            // 正交于mouseDir，(mouseDirY, -mouseDirX)
            if (mode == Mode.TopRightCorner) {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - topMiddlePoint.y)
                cylinderAxisLineStartPos.y = topMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            } else if (mode == Mode.BottomRightCorner) {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - bottomMiddlePoint.y)
                cylinderAxisLineStartPos.y = bottomMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            }

        }

        // draw engle point
        cylinderEnglePos.x = cylinderAxisPos.x + mouseDirX * cylinderRadius
        cylinderEnglePos.y = cylinderAxisPos.y + mouseDirY * cylinderRadius

        // draw engle line
        if (mode == Mode.TopRightCorner) {
            cylinderEngleLineStartPos.x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - topMiddlePoint.y)
            cylinderEngleLineStartPos.y = topMiddlePoint.y
            cylinderEngleLineEndPos.x =  topRightPoint.x
            cylinderEngleLineEndPos.y = cylinderEnglePos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineEndPos.x - cylinderEnglePos.x)
        } else if (mode == Mode.BottomRightCorner) {
            cylinderEngleLineStartPos.x = cylinderEnglePos.x + mouseDirY / mouseDirX * (cylinderEnglePos.y - bottomMiddlePoint.y)
            cylinderEngleLineStartPos.y = bottomMiddlePoint.y
            cylinderEngleLineEndPos.x =  topRightPoint.x
            cylinderEngleLineEndPos.y = cylinderEnglePos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineEndPos.x - cylinderEnglePos.x)
        }


        cylinderEngleProjPos.x = (cylinderAxisPos.x + mouseDirX * cylinderRadius * 0.5 * PI).toFloat()
        cylinderEngleProjPos.y = (cylinderAxisPos.y + mouseDirY * cylinderRadius * 0.5 * PI).toFloat()
        if (mode == Mode.TopRightCorner) {
            cylinderEngleLineProjStartPos.x = cylinderEngleProjPos.x + mouseDirY / mouseDirX * (cylinderEngleProjPos.y - topMiddlePoint.y)
            cylinderEngleLineProjStartPos.y = topMiddlePoint.y
            cylinderEngleLineProjEndPos.x =  topRightPoint.x
            cylinderEngleLineProjEndPos.y = cylinderEngleProjPos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineProjEndPos.x - cylinderEngleProjPos.x)
        } else if (mode == Mode.BottomRightCorner) {
            cylinderEngleLineProjStartPos.x = cylinderEngleProjPos.x + mouseDirY / mouseDirX * (cylinderEngleProjPos.y - bottomMiddlePoint.y)
            cylinderEngleLineProjStartPos.y = bottomMiddlePoint.y
            cylinderEngleLineProjEndPos.x =  topRightPoint.x
            cylinderEngleLineProjEndPos.y = cylinderEngleProjPos.y + (-mouseDirX) / mouseDirY * (cylinderEngleLineProjEndPos.x - cylinderEngleProjPos.x)
        }


        cylinderAxisProjPos.x = (cylinderAxisPos.x + mouseDirX * cylinderRadius * PI).toFloat()
        cylinderAxisProjPos.y = (cylinderAxisPos.y + mouseDirY * cylinderRadius * PI).toFloat()
        if (mode == Mode.TopRightCorner) {
            cylinderAxisLineProjStartPos.x = cylinderAxisProjPos.x + mouseDirY / mouseDirX * (cylinderAxisProjPos.y - topMiddlePoint.y)
            cylinderAxisLineProjStartPos.y = topMiddlePoint.y
            cylinderAxisLineProjEndPos.x =  topRightPoint.x
            cylinderAxisLineProjEndPos.y = cylinderAxisProjPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineProjEndPos.x - cylinderAxisProjPos.x)
        } else if (mode == Mode.BottomRightCorner) {
            cylinderAxisLineProjStartPos.x = cylinderAxisProjPos.x + mouseDirY / mouseDirX * (cylinderAxisProjPos.y - bottomMiddlePoint.y)
            cylinderAxisLineProjStartPos.y = bottomMiddlePoint.y
            cylinderAxisLineProjEndPos.x =  topRightPoint.x
            cylinderAxisLineProjEndPos.y = cylinderAxisProjPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineProjEndPos.x - cylinderAxisProjPos.x)
        }

        // 只有当翻页角不在水平线上时，才进行曲线计算
        if (mode != Mode.Landscape) {
            val cornerPosX = if (mode == Mode.TopRightCorner) topRightPoint.x else bottomRightPoint.x
            val cornerPosY = if (mode == Mode.TopRightCorner) topRightPoint.y else bottomRightPoint.y
            reflectPointAboutLine(cornerPosX, cornerPosY,
                cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
                cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y).apply {
                selectedCornerPos.x = first
                selectedCornerPos.y = second
            }

            reflectPointAboutLine(cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y,
                cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
                cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y).apply {
                sineStartPos1.x = first
                sineStartPos1.y = second
            }
            reflectPointAboutLine(cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y,
                cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
                cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y).apply {
                sineStartPos2.x = first
                sineStartPos2.y = second
            }

            sineMaxPos1.x = (cylinderAxisLineStartPos.x + sineStartPos1.x) / 2
            sineMaxPos1.y = (cylinderAxisLineStartPos.y + sineStartPos1.y) / 2
            sineMaxPos2.x = (cylinderAxisLineEndPos.x + sineStartPos2.x) / 2
            sineMaxPos2.y = (cylinderAxisLineEndPos.y + sineStartPos2.y) / 2
            sineMaxPos1.x = sineMaxPos1.x + mouseDirX * cylinderRadius
            sineMaxPos1.y = sineMaxPos1.y + mouseDirY * cylinderRadius
            sineMaxPos2.x = sineMaxPos2.x + mouseDirX * cylinderRadius
            sineMaxPos2.y = sineMaxPos2.y + mouseDirY * cylinderRadius
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
        canvas.drawPoint("Corner", selectedCornerPos.x, selectedCornerPos.y)
        canvas.drawLine(selectedCornerPos.x, selectedCornerPos.y, cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y, debugLinePaint)
        canvas.drawLine(selectedCornerPos.x, selectedCornerPos.y, cylinderEngleLineProjEndPos.x, cylinderEngleLineProjEndPos.y, debugLinePaint)
        canvas.drawLine(sineStartPos1.x, sineStartPos1.y, cylinderAxisLineProjStartPos.x, cylinderAxisLineProjStartPos.y, debugLinePaint)
        canvas.drawLine(sineStartPos2.x, sineStartPos2.y, cylinderAxisLineProjEndPos.x, cylinderAxisLineProjEndPos.y, debugLinePaint)
        canvas.drawPoint("sineStartPos1", sineStartPos1.x, sineStartPos1.y)
        canvas.drawPoint("sineStartPos2", sineStartPos2.x, sineStartPos2.y)
        canvas.drawLine(selectedCornerPos.x, selectedCornerPos.y, sineStartPos1.x, sineStartPos1.y, debugLinePaint)
        canvas.drawLine(selectedCornerPos.x, selectedCornerPos.y, sineStartPos2.x, sineStartPos2.y, debugLinePaint)
        val deltaX1 = hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)
        val deltaX2 = hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)
        drawHalfSineCurve(
            canvas, debugLinePaint,
            sineStartPos1.x, sineStartPos1.y,
            cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y,
            cylinderRadius, deltaX1, direction = if (getMode() == Mode.TopRightCorner) 1 else -1
        )
        drawHalfSineCurve(
            canvas, debugLinePaint,
            sineStartPos2.x, sineStartPos2.y,
            cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y,
            cylinderRadius, deltaX2, direction = if (getMode() == Mode.TopRightCorner) -1 else 1
        )
        canvas.drawPoint("", sineMaxPos1.x, sineMaxPos1.y)
        canvas.drawPoint("", sineMaxPos2.x, sineMaxPos2.y)
    }

    private fun calcPaths() {
        val mode = getMode()
        pathC.reset()
        pathC.moveTo(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
        if (mode == Mode.TopRightCorner) {
            pathC.lineTo(topRightPoint.x, topRightPoint.y)
        } else if (mode == Mode.BottomRightCorner) {
            pathC.lineTo(bottomRightPoint.x, bottomRightPoint.y)
        }
        pathC.lineTo(cylinderAxisLineEndPos.x, cylinderAxisLineEndPos.y)
        pathC.addQuarterSineFromHalfPeriod(
            startX = cylinderAxisLineEndPos.x,
            startY = cylinderAxisLineEndPos.y,
            endX = sineStartPos2.x,
            endY = sineStartPos2.y,
            amplitude = cylinderRadius,
            angularFrequency = (PI / hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)).toFloat(),
            direction = if (mode == Mode.TopRightCorner) 1 else -1
        )
        pathC.lineTo(sineMaxPos1.x, sineMaxPos1.y)
        pathC.addQuarterSineFromHalfPeriod(
            startX = sineStartPos1.x,
            startY = sineStartPos1.y,
            endX = cylinderAxisLineStartPos.x,
            endY = cylinderAxisLineStartPos.y,
            amplitude = cylinderRadius,
            angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
            isFirstQuarter = false,
            direction = if (mode == Mode.TopRightCorner) 1 else -1
        )
        pathC.close()
        pathC.op(allPageRegion, Path.Op.INTERSECT)

        pathB.reset()
        pathB.moveTo(sineMaxPos1.x, sineMaxPos1.y)
        pathB.lineTo(sineMaxPos2.x, sineMaxPos2.y)
        pathB.addQuarterSineFromHalfPeriod(
            startX = cylinderAxisLineEndPos.x,
            startY = cylinderAxisLineEndPos.y,
            endX = sineStartPos2.x,
            endY = sineStartPos2.y,
            amplitude = cylinderRadius,
            angularFrequency = (PI / hypot(sineStartPos2.x - cylinderAxisLineEndPos.x, sineStartPos2.y - cylinderAxisLineEndPos.y)).toFloat(),
            isFirstQuarter = false,
            direction = if (mode == Mode.TopRightCorner) 1 else -1
        )
        pathB.lineTo(selectedCornerPos.x, selectedCornerPos.y)
        pathB.lineTo(sineStartPos1.x, sineStartPos1.y)
        pathB.addQuarterSineFromHalfPeriod(
            startX = sineStartPos1.x,
            startY = sineStartPos1.y,
            endX = cylinderAxisLineStartPos.x,
            endY = cylinderAxisLineStartPos.y,
            amplitude = cylinderRadius,
            angularFrequency = (PI / hypot(sineStartPos1.x - cylinderAxisLineStartPos.x, sineStartPos1.y - cylinderAxisLineStartPos.y)).toFloat(),
            direction = if (mode == Mode.TopRightCorner) 1 else -1
        )
        pathB.close()
        pathB.op(allPageRegion, Path.Op.INTERSECT)

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


