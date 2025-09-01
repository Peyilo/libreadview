package org.peyilo.libreadview.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.manager.render.GLRenderer
import org.peyilo.libreadview.manager.util.PageBitmapCache
import org.peyilo.libreadview.util.LogHelper
import org.peyilo.libreadview.manager.util.ScreenLineIntersections
import org.peyilo.libreadview.manager.util.reflectPointAboutLine
import org.peyilo.libreadview.manager.util.screenshot
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class GLSLCurlPageManager: FlipOnReleaseLayoutManager.Horizontal(), AnimatedLayoutManager {

    private val pageBitmapCache = PageBitmapCache()
    private val gl by lazy { GLRenderer(pageContainer.context) }

    private val screenLineIntersections by lazy { ScreenLineIntersections(pageContainer.width.toFloat(), pageContainer.height.toFloat()) }

    private val debugPosPaint = Paint().apply {
        textSize = 36F
        color = Color.RED
    }

    private val debugLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3F
        color = Color.WHITE
    }

    override fun prepareAnim(initDire: PageDirection) {
        when (initDire) {
            PageDirection.NEXT -> {
                pageBitmapCache.topBitmap = pageContainer.getCurPage()!!.screenshot()
                pageBitmapCache.bottomBitmap = pageContainer.getNextPage()!!.screenshot()
            }
            PageDirection.PREV -> {
                pageBitmapCache.topBitmap = pageContainer.getPrevPage()!!.screenshot()
                pageBitmapCache.bottomBitmap = pageContainer.getCurPage()!!.screenshot()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        pageContainer.invalidate()
    }

    override fun startNextAnim() {

    }

    override fun startPrevAnim() {

    }

    override fun setAnimDuration(animDuration: Int) {

    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (pageBitmapCache.topBitmap != null && pageBitmapCache.bottomBitmap != null) {
            gl.render(
                canvas,
                topBitmap = pageBitmapCache.topBitmap!!,
                bottomBitmap = pageBitmapCache.bottomBitmap!!,
                mouseX = gesture.cur.x, mouseY = gesture.cur.y, mouseZ = gesture.down.x, mouseW = gesture.down.y
            )
            debug(canvas)
        }
    }

    private fun debug(canvas: Canvas) {
        canvas.drawPoint("Down", gesture.down.x, gesture.down.y)
        canvas.drawPoint("Cur", gesture.cur.x, gesture.cur.y)
        canvas.drawLine(gesture.down.x, gesture.down.y, gesture.cur.x, gesture.cur.y, debugLinePaint)

        // process origin point
        var mouseDirX = gesture.down.x - gesture.cur.x
        var mouseDirY = gesture.down.y - gesture.cur.y
        val len = hypot(mouseDirX, mouseDirY)
        mouseDirX /= len
        mouseDirY /= len
        val originX = 0F
        val originY = (gesture.cur.y - mouseDirY * gesture.cur.x / mouseDirX).coerceIn(0F, pageContainer.height.toFloat())
        canvas.drawPoint("Origin", originX, originY)
        canvas.drawLine(originX, originY, gesture.cur.x, gesture.cur.y, debugLinePaint)

        // process end point
        val endX = pageContainer.width.toFloat()
        val endY = gesture.down.y + mouseDirY / mouseDirX * (endX - gesture.down.x)
        canvas.drawPoint("End", endX, endY)
        canvas.drawLine(endX, endY, gesture.down.x, gesture.down.y, debugLinePaint)

        // process axis point
        val L1 = hypot(gesture.cur.x - originX, gesture.cur.y - originY)
        val L2 = hypot(endX - gesture.down.x, endY - gesture.down.y)
        val axisX = gesture.cur.x + mouseDirX * L2
        val axisY = gesture.cur.y + mouseDirY * L2
        canvas.drawPoint("Axis", axisX, axisY)

        // draw axis line
        // 正交于mouseDir，(mouseDirY, -mouseDirX)
        val axisLineStartX = axisX + mouseDirY / mouseDirX * axisY
        val axisLineStartY = 0F

        val axisLineEndX = axisX - mouseDirY / mouseDirX * (pageContainer.height - axisY)
        val axisLineEndY = pageContainer.height.toFloat()
        val axisLine = screenLineIntersections.intersections(
            axisLineStartX,
            axisLineStartY,
            axisLineEndX,
            axisLineEndY
        )
        canvas.drawLine(axisLine.first.x, axisLine.first.y, axisLine.second.x, axisLine.second.y, debugLinePaint)

        // draw engle point
        val radius = 0.05F * pageContainer.height
        val engleX = axisX + mouseDirX * radius
        val engleY = axisY + mouseDirY * radius
        canvas.drawPoint("Engle", engleX, engleY)

        // draw engle line
        val engleLineStartX = engleX + mouseDirY / mouseDirX * engleY
        val engleLineStartY = 0F
        val engleLineEndX =  engleX - mouseDirY / mouseDirX * (pageContainer.height - engleY)
        val engleLineEndY = pageContainer.height.toFloat()
        val engleLine = screenLineIntersections.intersections(
            engleLineStartX,
            engleLineStartY,
            engleLineEndX,
            engleLineEndY
        )
        canvas.drawLine(engleLine.first.x, engleLine.first.y, engleLine.second.x, engleLine.second.y, debugLinePaint)

        val engleProjX = axisX + mouseDirX * radius * 0.5 * PI
        val engleProjY = axisY + mouseDirY * radius * 0.5 * PI
        val engleProjLineStartX = (engleProjX + mouseDirY / mouseDirX * engleProjY).toFloat()
        val engleProjLineStartY = 0F
        val engleProjLineEndX = ( engleProjX - mouseDirY / mouseDirX * (pageContainer.height - engleProjY)).toFloat()
        val engleProjLineEndY = pageContainer.height.toFloat()
        val engleProjLine = screenLineIntersections.intersections(
            engleProjLineStartX,
            engleProjLineStartY,
            engleProjLineEndX,
            engleProjLineEndY
        )
        canvas.drawLine(engleProjLine.first.x, engleProjLine.first.y, engleProjLine.second.x, engleProjLine.second.y, debugLinePaint)

        val axisProjX = axisX + mouseDirX * radius * PI
        val axisProjY = axisY + mouseDirY * radius * PI
        val axisProjLineStartX =( axisProjX + mouseDirY / mouseDirX * axisProjY).toFloat()
        val axisProjLineStartY = 0F
        val axisProjLineEndX = (axisProjX - mouseDirY / mouseDirX * (pageContainer.height - axisProjY)).toFloat()
        val axisProjLineEndY = pageContainer.height.toFloat()
        val axisProjLine = screenLineIntersections.intersections(
            axisProjLineStartX,
            axisProjLineStartY,
            axisProjLineEndX,
            axisProjLineEndY
        )
        canvas.drawLine(axisProjLine.first.x, axisProjLine.first.y, axisProjLine.second.x, axisProjLine.second.y, debugLinePaint)

        if (gesture.down.y != gesture.cur.y) {
            val isTopRight = gesture.down.y < gesture.cur.y
            val cornerPos = if (!isTopRight) {
                // Corner pos: Bottom Right
                PointF(pageContainer.width.toFloat(), pageContainer.height.toFloat())
            } else {
                // Corner pos: Top Right
                PointF(pageContainer.width.toFloat(), 0F)
            }
            val reflectedCorner = reflectPointAboutLine(cornerPos.x, cornerPos.y,
                engleProjLineStartX,
                engleProjLineStartY,
                engleProjLineEndX, engleProjLineEndY)
            canvas.drawPoint("Corner", reflectedCorner.first, reflectedCorner.second)
            val reflectedAxisProjStart = reflectPointAboutLine(
                axisProjLine.first.x,
                axisProjLine.first.y,
                engleProjLineStartX,
                engleProjLineStartY,
                engleProjLineEndX,
                engleProjLineEndY
            )
            val reflectedAxisProjEnd = reflectPointAboutLine(
                axisProjLine.second.x,
                axisProjLine.second.y,
                engleProjLineStartX,
                engleProjLineStartY,
                engleProjLineEndX,
                engleProjLineEndY
            )
            canvas.drawPoint("ProjStart", reflectedAxisProjStart.first, reflectedAxisProjStart.second)
            canvas.drawPoint("ProjEnd", reflectedAxisProjEnd.first, reflectedAxisProjEnd.second)

            canvas.drawLine(reflectedAxisProjStart.first, reflectedAxisProjStart.second,
                reflectedCorner.first, reflectedCorner.second, debugLinePaint)
            canvas.drawLine(reflectedAxisProjEnd.first, reflectedAxisProjEnd.second,
                reflectedCorner.first, reflectedCorner.second, debugLinePaint)

            val deltaX1 = hypot(axisLine.first.x - reflectedAxisProjStart.first, axisLine.first.y - reflectedAxisProjStart.second)
            drawHalfSineCurve(
                canvas, debugLinePaint,
                axisLine.first.x, axisLine.first.y,
                reflectedAxisProjStart.first, reflectedAxisProjStart.second,
                radius, deltaX1, direction = -1
            )
            val deltaX2 = hypot(axisLine.second.x - reflectedAxisProjEnd.first, axisLine.second.y - reflectedAxisProjEnd.second)
            drawHalfSineCurve(
                canvas, debugLinePaint,
                axisLine.second.x, axisLine.second.y,
                reflectedAxisProjEnd.first, reflectedAxisProjEnd.second,
                radius, deltaX2, direction = 1
            )
        }
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

    private fun Canvas.drawPoint(text: String, x: Float, y: Float,) {
        drawCircle(x, y, 6F, debugPosPaint)
        drawText(text, x, y, debugPosPaint)
    }

    private fun getTranslateX(position: Int): Float {
        val containerPageCount = pageContainer.getContainerPageCount()
        return when {
            containerPageCount >= 3 -> when {
                position == 2 && !pageContainer.isFirstPage() -> -pageContainer.width.toFloat()
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                else -> 0F
            }
            containerPageCount == 2 -> when {
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                else -> 0F
            }
            containerPageCount == 1 -> 0F
            else -> throw IllegalStateException("onAddPage: The pageContainer.itemCount is 0.")
        }
    }

    override fun onAddPage(view: View, position: Int) {
        view.translationX = getTranslateX(position)
        LogHelper.d(
            TAG, "onAddPage: childCount = ${pageContainer.childCount}, " +
                    "containerPageCount = ${pageContainer.getContainerPageCount()}, $position -> ${view.translationX}")
    }

    companion object {
        private const val TAG = "GLSLCurlPageManager"
    }
}