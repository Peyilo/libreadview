package org.peyilo.libreadview.manager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.utils.LogHelper
import org.peyilo.libreadview.utils.ScreenLineIntersections
import org.peyilo.libreadview.utils.Vec
import org.peyilo.libreadview.utils.copy
import org.peyilo.libreadview.utils.rakeRadio
import org.peyilo.libreadview.utils.reflectPointAboutLine
import org.peyilo.libreadview.utils.screenshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 提供多种仿真翻页实现
 * TODO：仿iOS的仿真翻页实现
 */
class SimulationPageManagers private constructor() {

    companion object {
        private const val TAG = "SimulationPageManagers"
    }

    private class PageBitmap {
        var topBitmap: Bitmap? = null
            set(value) {
                field?.let {
                    try {           // 如果不为null，尝试释放bitmap
                        field!!.recycle()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "topBitmap: ${e.stackTrace}")
                    }
                }
                field = value
            }

        var bottomBitmap: Bitmap? = null
            set(value) {
                field?.let {
                    try {           // 如果不为null，尝试释放bitmap
                        field!!.recycle()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "topBitmap: ${e.stackTrace}")
                    }
                }
                field = value
            }

        fun clearBitmap() {
            topBitmap?.recycle()
            bottomBitmap?.recycle()
            topBitmap = null
            bottomBitmap = null
        }
    }

    /**
     * 仿真翻页实现1，常见于Android端各种小说阅读软件的仿真翻页实现
     * TODO：尽可能不在绘制中创建大量的PointF对象，由于对象全部保存在堆上，可能造成频繁地GC
     * TODO: 向上翻页时，动画实在过于简陋，尝试采用iOS iBook那种实现
     */
    open class Style1: FlipOnReleaseLayoutManager.Horizontal(), AnimatedLayoutManager {

        protected val containerWidth get() = pageContainer.width      // 容器的宽度
        protected val containerHeight get() = pageContainer.height    // 容器的高度

        protected val bezierStart1 = PointF()         // 第一条贝塞尔曲线的起始点、终点、控制点
        protected val bezierEnd1 = PointF()
        protected val bezierControl1 = PointF()

        protected val bezierStart2 = PointF()         // 第二条贝塞尔曲线的起始点、终点、控制点
        protected val bezierEnd2 = PointF()
        protected val bezierControl2 = PointF()

        // 大多数情况下，bezierControl2和bezierControl2Copy完全相同
        protected val bezierControl2Copy = PointF()
        protected var userCopy = false        // bezierControl2和bezierControl2Copy不同时，该值为true

        protected val bezierVertex1 = PointF()        // C区域直角三角形（为了更加简便，这里了近似为直角，真实视觉效果应该上比90°稍大）斜边与两条贝塞尔曲线相切的两个点
        protected val bezierVertex2 = PointF()

        protected val touchPoint = PointF()                 // 触摸点
        protected val cornerVertex = PointF()               // 页脚顶点
        protected val middlePoint = PointF()                // 触摸点、页脚定点连线的中点
        protected val m1 = PointF()                         // bezierStart1、bezierEnd1连线的中点
        protected val m2 = PointF()                         // bezierStart2、bezierEnd2连线的中点

        /**
         * 仿真翻页时有三个区域：A、B、C
         * A区域：当前页区域
         * B区域：下一页区域
         * C区域：当前页背面区域
         */
        protected val pathA = Path()
        protected val pathB = Path()
        protected val pathC = Path()

        /**
         * 绘制区域C要用到的: 叠加灰色调遮罩、矩阵变换
         */
        protected val backShadowPaint = Paint().apply {
            color = Color.argb(10, 0, 0, 0) // 半透明黑（也可以用灰）
        }
        private val regionCMatrixArray = floatArrayOf(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 1F)
        private val regionCMatrix = Matrix()

        /**
         * C区域：当前页的背面区域
         * 设置渲染仿真翻页扭曲部分区域的横向采样点数量：采样点越多，越精确，但是计算量也越高
         */
        var regionCMeshWidth = 40

        /**
         * C区域：当前页的背面区域
         * 设置渲染仿真翻页扭曲部分区域的纵向采样点数量：采样点越多，越精确，但是计算量也越高
         */
        var regionCMeshHeight = 40
        private val regionCMeshVertsCount = (regionCMeshWidth + 1) * (regionCMeshHeight + 1)
        private val regionCMeshVerts = FloatArray(regionCMeshVertsCount * 2)

        /**
         * A区域：当前页的正面区域
         * 设置渲染仿真翻页扭曲部分区域的横向采样点数量：采样点越多，越精确，但是计算量也越高
         */
        var regionAMeshWidth = 40

        /**
         * A区域：当前页的正面区域
         * 设置渲染仿真翻页扭曲部分区域的纵向采样点数量：采样点越多，越精确，但是计算量也越高
         */
        var regionAMeshHeight = 40
        private val regionAMeshVertsCount = (regionAMeshWidth + 1) * (regionAMeshHeight + 1)
        private val regionAMeshVerts = FloatArray(regionAMeshVertsCount * 2)

        /**
         * 注意控制Bitmap的回收，避免出现内存泄漏
         */
        private val pageBitmap = PageBitmap()

        private var animMode = AnimMode.None

        /**
         * 动画持续时间
         */
        private var animDuration = 400

        private val shadowB = Path()
        private val shadowC = Path()
        private val shadowA1 = Path()
        private val shadowA2 = Path()

        protected val shadowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        var shadowAWidth = 20F
        protected val shadowAStartColor = Color.argb(60, 0, 0, 0)
        protected val shadowAEndColor = Color.argb(0, 0, 0, 0)

        /**
         * 开启debug模式以后，将会显示仿真翻页绘制过程中各个关键点的位置以及连线
         */
        var enableDebugMode = false

        private val debugLinePaint by lazy {
            Paint().apply {
                color = Color.BLACK
                strokeWidth = 3F
            }
        }
        private val debugPosPaint by lazy {
            Paint().apply {
                color = Color.BLACK
                textSize = 32F
            }
        }


        override fun setAnimDuration(animDuration: Int) {
            this.animDuration = animDuration
        }

        /**
         * 计算各个点坐标，假设调用该函数之前touchPoint、cornerVertex已经初始化
         * 注意垂直的情况下，各个点需要特殊处理，否则会出现意外情况
         */
        private fun computePoints() {
            // 要保证touchPoint有效性，即touchPoint.x != cornerVertex.x，也就是dir1.rakeRadio() != Nan
            if (touchPoint.x == cornerVertex.x) {
                touchPoint.x = cornerVertex.x - 1
            }
            var dir1 = Vec.normalize(touchPoint - cornerVertex)
            // compute the first bezier curve point
            computeMiddlePoint(touchPoint, cornerVertex, middlePoint)
            bezierControl1.x = middlePoint.x - (cornerVertex.y - middlePoint.y) * dir1.rakeRadio()
            bezierControl1.y = cornerVertex.y
            bezierStart1.x = bezierControl1.x - (cornerVertex.x - bezierControl1.x) / 2
            bezierStart1.y = cornerVertex.y

            if (touchPoint.x > 0 && bezierStart1.x < 0) {
                // 限制页面左侧不能翻起来，模拟真实书籍的装订
                val w0 = containerWidth - bezierStart1.x
                val w1 = abs(cornerVertex.x - touchPoint.x)
                val w2 = containerWidth * w1 / w0
                touchPoint.x = abs(cornerVertex.x - w2)
                val h1 = abs(cornerVertex.y - touchPoint.y)
                val h2 = w2 * h1 / w1
                touchPoint.y = abs(cornerVertex.y - h2)

                // touchPoint更新后，需要重新计算与touchPoint有关系的坐标
                dir1 = Vec.normalize(touchPoint - cornerVertex)
                computeMiddlePoint(touchPoint, cornerVertex, middlePoint)
                bezierControl1.x = middlePoint.x - (cornerVertex.y - middlePoint.y) * dir1.rakeRadio()
                bezierStart1.x = bezierControl1.x - (cornerVertex.x - bezierControl1.x) / 2
                if (bezierStart1.x < 0 && -bezierStart1.x < 1e-2) {
                    bezierStart1.x = 0F
                }
            }
            computeMiddlePoint(bezierControl1, touchPoint, bezierEnd1)

            val dir2 = Vec.orthogonal(dir1.copy())
            // compute the second bezier curve point
            if (dir1.rakeRadio() != 0F) {
                bezierControl2.x = cornerVertex.x
                bezierControl2.y = middlePoint.y + (cornerVertex.x - middlePoint.x) * dir2.rakeRadio()
                computeMiddlePoint(bezierControl2, touchPoint, bezierEnd2)
                if (bezierEnd2.y < 0 || bezierEnd2.y > containerHeight) {
                    val p1 = PointF().apply {
                        x = 0F
                        y = containerHeight - bezierControl1.y
                    }
                    val p2 = PointF().apply {
                        x = containerWidth.toFloat()
                        y = containerHeight - bezierControl1.y
                    }
                    computeCrossPoint(p1, p2, touchPoint, bezierControl2, bezierControl2Copy)

                    var dx = containerHeight / dir2.rakeRadio()
                    if (cornerVertex.y != 0F) {
                        dx = -dx
                    }
                    bezierControl2.x = bezierControl1.x + dx
                    bezierControl2.y = containerHeight - bezierControl1.y
                    bezierStart2.x = bezierStart1.x + dx
                    bezierStart2.y = bezierControl2.y
                    bezierEnd2.x = bezierStart2.x
                    bezierEnd2.y = bezierStart2.y
                    userCopy = true
                } else {
                    bezierControl2Copy.x = bezierControl2.x
                    bezierControl2Copy.y = bezierControl2.y
                    bezierStart2.x = cornerVertex.x
                    bezierStart2.y = bezierControl2.y - (cornerVertex.y- bezierControl2.y) / 2
                    userCopy = false
                }
            } else {
                bezierControl2.x = bezierControl1.x
                bezierControl2.y = containerHeight.toFloat() - bezierControl1.y
                bezierStart2.x = bezierStart1.x
                bezierStart2.y = bezierControl2.y
                bezierEnd2.x = bezierStart2.x
                bezierEnd2.y = bezierStart2.y
                userCopy = true
                bezierControl2Copy.x = touchPoint.x
                bezierControl2Copy.y = bezierControl2.y
            }
            computeMiddlePoint(bezierStart1, bezierEnd1, m1)
            computeMiddlePoint(bezierStart2, bezierEnd2, m2)
            computeMiddlePoint(m1, bezierControl1, bezierVertex1)      // bezierVertex1为m1、bezierControl1连线的中点
            computeMiddlePoint(m2, bezierControl2, bezierVertex2)      // bezierVertex2为m2、bezierControl2连线的中点
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

        /**
         * 计算P1P2的中点坐标，并保存到result中
         */
        private fun computeMiddlePoint(p1: PointF, p2: PointF, result: PointF) {
            result.x = (p1.x + p2.x) / 2
            result.y = (p1.y + p2.y) / 2
        }

        private fun computePaths() {
            // 计算A区域的边界
            pathA.reset()
            pathA.moveTo(bezierStart1.x, bezierStart1.y)
            pathA.quadTo(bezierControl1.x, bezierControl1.y, bezierEnd1.x, bezierEnd1.y)
            pathA.lineTo(touchPoint.x, touchPoint.y)
            if (userCopy) {
                pathA.lineTo(bezierControl2Copy.x, bezierControl2Copy.y)
                pathA.lineTo(0F, containerHeight - cornerVertex.y)
                pathA.lineTo(0F, cornerVertex.y)
            } else {
                pathA.lineTo(bezierEnd2.x, bezierEnd2.y)
                pathA.quadTo(bezierControl2.x, bezierControl2.y, bezierStart2.x, bezierStart2.y)
                // 根据cornerVertex位置不同（左上角或者右上角）绘制区域A
                pathA.lineTo(containerWidth.toFloat(), containerHeight.toFloat() - cornerVertex.y)
                pathA.lineTo(0F, containerHeight.toFloat() - cornerVertex.y)
                pathA.lineTo(0F, cornerVertex.y)
            }
            pathA.close()
            // 计算C区域的边界
            pathC.reset()
            pathC.moveTo(bezierVertex1.x, bezierVertex1.y)      // 将曲线简化为直线，再减去与A区域相交的区域即可获得C区域
            if (userCopy) {
                pathC.lineTo(bezierVertex2.x, bezierVertex2.y)
                pathC.lineTo(bezierControl2Copy.x, bezierControl2Copy.y)
                pathC.lineTo(touchPoint.x, touchPoint.y)
            } else {
                pathC.lineTo(bezierEnd1.x, bezierEnd1.y)
                pathC.lineTo(touchPoint.x, touchPoint.y)
                pathC.lineTo(bezierEnd2.x, bezierEnd2.y)
                pathC.lineTo(bezierVertex2.x, bezierVertex2.y)
                pathC.lineTo(bezierVertex1.x, bezierVertex1.y)
            }
            pathC.close()
            pathC.op(pathA, Path.Op.DIFFERENCE)
            // 计算B区域的边界
            pathB.reset()
            pathB.moveTo(0F, 0F)
            pathB.lineTo(containerWidth.toFloat(), 0F)
            pathB.lineTo(containerWidth.toFloat(), containerHeight.toFloat())
            pathB.lineTo(0F, containerHeight.toFloat())
            pathB.close()               // 先取B+C区域，然后减去C区域即可获得B区域
            pathB.op(pathC, Path.Op.DIFFERENCE)
            pathB.op(pathA, Path.Op.DIFFERENCE)
        }

        override fun prepareAnim(initDire: PageDirection) {
            when (initDire) {
                PageDirection.NEXT -> {
                    pageBitmap.topBitmap = pageContainer.getCurPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getNextPage()!!.screenshot()
                }
                PageDirection.PREV -> {
                    pageBitmap.topBitmap = pageContainer.getPrevPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getCurPage()!!.screenshot()
                }
                else -> throw IllegalStateException()
            }
        }

        private enum class AnimMode {
            TopRightCorner, BottomRightCorner, NextLandscape, PrevLandscape, None
        }

        override fun decideInitDire(dx: Float, dy: Float): PageDirection {
            val initDire = super.decideInitDire(dx, dy)
            animMode = when (initDire) {
                PageDirection.NEXT -> {     // 向下一页翻页时，根据本轮手势的DOWN坐标决定翻页动画的三种不同模式：右上角翻页、右下角翻页、横向翻页
                    if (gesture.down.y < containerHeight * 0.4) {               // 右上角翻页
                        AnimMode.TopRightCorner
                    } else if (gesture.down.y > containerHeight * 0.8) {    // 右下角翻页
                        AnimMode.BottomRightCorner
                    } else {   // 横向翻页
                        AnimMode.NextLandscape
                    }
                }
                PageDirection.PREV -> {     // 向上一页翻页时，只有横向翻页一种模式
                    AnimMode.PrevLandscape
                }
                else -> AnimMode.None
            }
            if (animMode != AnimMode.None) {
                // 横向翻页通过touchPoint实现，因此也要设置cornerVertex
                if (gesture.down.y < containerHeight / 2) {
                    cornerVertex.x = containerWidth.toFloat()
                    cornerVertex.y = 0F
                } else {
                    cornerVertex.x = containerWidth.toFloat()
                    cornerVertex.y = containerHeight.toFloat()
                }
            }
            return initDire
        }

        /**
         * 限制TouchPoint的坐标不出界，并做一些近似处理
         */
        private fun setTouchPoint(x: Float, y: Float) {
            touchPoint.x = x
            when (animMode) {
                AnimMode.TopRightCorner, AnimMode.BottomRightCorner -> {
                    if (y > 0 && y < containerHeight) {
                        touchPoint.y = y
                    } else if (y <= 0) {            // 限制touchPoint.y不出界
                        touchPoint.y = 0F
                    } else {
                        touchPoint.y = containerHeight.toFloat()
                    }
                }
                AnimMode.NextLandscape, AnimMode.PrevLandscape -> {
                    if (cornerVertex.y == 0F) {
                        touchPoint.y = 0F
                    } else {
                        touchPoint.y = containerHeight.toFloat()
                    }
                }
                else -> Unit
            }
        }

        override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
            setTouchPoint(gesture.cur.x, gesture.cur.y)
            computePoints()
            computePaths()
            pageContainer.invalidate()
        }

        override fun startNextAnim() {
            val dx = - touchPoint.x - cornerVertex.x
            val dy = - touchPoint.y + cornerVertex.y
            isAnimRuning = true
            scroller.startScroll(
                touchPoint.x.toInt(), touchPoint.y.toInt(), dx.toInt(), dy.toInt(), animDuration
            )
            pageContainer.invalidate()
        }

        override fun startPrevAnim() {
            val dx = - touchPoint.x + cornerVertex.x
            val dy =  - touchPoint.y + cornerVertex.y
            isAnimRuning = true
            scroller.startScroll(
                touchPoint.x.toInt(), touchPoint.y.toInt(), dx.toInt(), dy.toInt(), animDuration
            )
            pageContainer.invalidate()
        }

        override fun startResetAnim(initDire: PageDirection) {
            when (initDire) {
                PageDirection.NEXT -> {
                    startPrevAnim()
                }
                PageDirection.PREV -> {
                    startNextAnim()
                }
                else -> throw IllegalStateException("initDire is PageDirection.None")
            }
            pageContainer.invalidate()
        }

        override fun onNextCarouselLayout() {
            // 由于仿真翻页的动画实现，并不是依靠translationX，因此在动画结束后，
            // 还需将被拖动的View需要主动移出画面内，不像其他PageManager会通过设置translationX达到动画的效果
            pageContainer.apply {
                getPrevPage()?.translationX = -containerWidth.toFloat()
            }
        }

        override fun onPrevCarouselLayout() {
            // 被拖动的View需要主动移出画面内，不像其他PageManager会通过设置translationX达到动画的效果
            pageContainer.apply {
                getCurPage()?.translationX = 0F
            }
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

        override fun flipToNextPage(limited: Boolean): Boolean {
            cornerVertex.x = containerWidth.toFloat()
            cornerVertex.y = containerHeight.toFloat()
            touchPoint.x = cornerVertex.x
            touchPoint.y = cornerVertex.y
            return super.flipToNextPage(limited)
        }

        override fun flipToPrevPage(limited: Boolean): Boolean {
            cornerVertex.x = containerWidth.toFloat()
            cornerVertex.y = containerHeight.toFloat()
            touchPoint.x = 0F
            touchPoint.y = cornerVertex.y
            return super.flipToPrevPage(limited)
        }

        override fun computeScroll() {
            if (scroller.computeScrollOffset()) {
                setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
                computePoints()
                computePaths()
                pageContainer.invalidate()
                // 滑动动画结束
                if (scroller.currX == scroller.finalX && scroller.currY == scroller.finalY) {
                    scroller.forceFinished(true)
                    isAnimRuning = false
                    pageBitmap.clearBitmap()
                }
            }
        }

        override fun abortAnim() {
            super.abortAnim()
            isAnimRuning = false
            scroller.forceFinished(true)
            pageBitmap.clearBitmap()
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (isDragging || isAnimRuning) {
                drawRegionA(canvas)
                drawRegionB(canvas)
                drawRegionC(canvas)
                drawShadow(canvas)
                if(enableDebugMode) debug(canvas)
            }
        }

        private fun debug(canvas: Canvas) {
            canvas.drawText("touchPoint", touchPoint.x - 60, touchPoint.y, debugPosPaint)
            canvas.drawText("middlePoint", middlePoint.x - 60, middlePoint.y, debugPosPaint)

            canvas.drawText("bezierStart1", bezierStart1.x - 60, bezierStart1.y, debugPosPaint)
            canvas.drawText("bezierControl1", bezierControl1.x - 60, bezierControl1.y, debugPosPaint)
            canvas.drawText("bezierEnd1", bezierEnd1.x - 60, bezierEnd1.y, debugPosPaint)

            canvas.drawText("bezierStart2", bezierStart2.x - 60, bezierStart2.y, debugPosPaint)
            canvas.drawText("bezierControl2", bezierControl2.x - 60, bezierControl2.y, debugPosPaint)
            canvas.drawText("bezierEnd2", bezierEnd2.x - 60, bezierEnd2.y, debugPosPaint)

            canvas.drawText("m1", m1.x, m1.y, debugPosPaint)
            canvas.drawText("m2", m2.x, m2.y, debugPosPaint)
            canvas.drawText("bezierVertex1", bezierVertex1.x - 60, bezierVertex1.y, debugPosPaint)
            canvas.drawText("bezierVertex2", bezierVertex2.x - 60, bezierVertex2.y, debugPosPaint)

            canvas.drawLine(cornerVertex.x, cornerVertex.y, touchPoint.x, touchPoint.y, debugLinePaint)
            canvas.drawLine(bezierControl1.x, bezierControl1.y, bezierControl2.x, bezierControl2.y, debugLinePaint)
            canvas.drawLine(bezierControl1.x, bezierControl1.y, touchPoint.x, touchPoint.y, debugLinePaint)
            canvas.drawLine(bezierControl2.x, bezierControl2.y, touchPoint.x, touchPoint.y, debugLinePaint)
            canvas.drawLine(bezierControl2Copy.x, bezierControl2Copy.y, touchPoint.x, touchPoint.y, debugLinePaint)
            canvas.drawLine(bezierStart1.x, bezierStart1.y, bezierStart2.x, bezierStart2.y, debugLinePaint)
            canvas.drawLine(bezierVertex1.x, bezierVertex1.y, bezierVertex2.x, bezierVertex2.y, debugLinePaint)
            canvas.drawLine(m1.x, m1.y, bezierControl1.x, bezierControl1.y, debugLinePaint)
            canvas.drawLine(m2.x, m2.y, bezierControl2.x, bezierControl2.y, debugLinePaint)
            canvas.drawLine(touchPoint.x, touchPoint.y, gesture.cur.x, gesture.cur.y, debugLinePaint)
        }

        private fun drawRegionA(canvas: Canvas) {
            canvas.withClip(pathA) {
                computeRegionAMeshVerts()
                canvas.drawBitmapMesh(pageBitmap.topBitmap!!, regionAMeshWidth, regionAMeshHeight, regionAMeshVerts, 0, null, 0, null)
            }
        }

        private fun drawRegionB(canvas: Canvas) {
            canvas.withClip(pathB) {
                drawBitmap(pageBitmap.bottomBitmap!!, 0F, 0F, null)
            }
        }

        private fun drawRegionC(canvas: Canvas) {
            canvas.withClip(pathC) {
                // 计算两个控制点之间的距离
                val dis = hypot(bezierControl2.x - bezierControl1.x, bezierControl2.y - bezierControl1.y)
                val sin = (bezierControl2.x - bezierControl1.x) / dis
                val cos = (bezierControl2.y - bezierControl1.y) / dis
                regionCMatrixArray[0] = -(1 - 2 * sin * sin)
                regionCMatrixArray[1] = 2 * sin * cos
                regionCMatrixArray[3] = 2 * sin * cos
                regionCMatrixArray[4] = 1 - 2 * sin * sin
                this@Style1.regionCMatrix.reset()
                this@Style1.regionCMatrix.setValues(regionCMatrixArray)
                // 将坐标原点平移到bezierControl1处
                this@Style1.regionCMatrix.preTranslate(-bezierControl1.x, -bezierControl1.y)
                this@Style1.regionCMatrix.postTranslate(bezierControl1.x, bezierControl1.y)
                computeRegionCMeshVerts(this@Style1.regionCMatrix)
                canvas.drawBitmapMesh(pageBitmap.topBitmap!!, regionCMeshWidth, regionCMeshHeight, regionCMeshVerts, 0, null, 0, null)
            }
        }

        /**
         * 计算RegionC drawBitmapMesh需要的点坐标，并对点坐标应用matrix的线性变换
         */
        private fun computeRegionCMeshVerts(matrix: Matrix?) {
            var index = 0
            for (y in 0..regionCMeshHeight) {
                val fy = containerHeight.toFloat() * y / regionCMeshHeight
                for (x in 0..regionCMeshWidth) {
                    val fx = containerWidth * x.toFloat() / regionCMeshWidth
                    regionCMeshVerts[index * 2] = fx
                    regionCMeshVerts[index * 2 + 1] = fy
                    index++
                }
            }

            val dirX = touchPoint.x - cornerVertex.x
            val dirY = touchPoint.y - cornerVertex.y
            val len = hypot(dirX, dirY)
            val ux = dirX / len       // 单位方向向量
            val uy = dirY / len
            val originX = cornerVertex.x
            val originY = cornerVertex.y
            val radius = abs((touchPoint.x - bezierVertex1.x) * ux + (touchPoint.y - bezierVertex1.y) * uy)  // 圆柱半径，值越大弯曲越柔和


            for (i in regionCMeshVerts.indices step 2) {
                val x = regionCMeshVerts[i]
                val y = regionCMeshVerts[i + 1]

                // 计算当前mesh vert相对于origin的坐标
                val dx = x - originX
                val dy = y - originY

                // s 是沿翻页轴方向的投影（用于卷曲角度）
                val s = dx * ux + dy * uy
                // t 是垂直于翻页轴的投影（保留该方向用于回变换）
                val t = -dx * uy + dy * ux

                // 得到的s和t构成了相对于一个新坐标系的坐标，该坐标系的其中一条轴由cornerVertex指向touchPoint，
                // 另一条则是垂直于cornerVertex指向touchPoint的连线
                // 将 s 映射到圆柱体表面
                val theta = s / radius
                // 当cornerVertex为右上角的顶点时，映射并不正确，推测是因为meshVerts中多个点位置可能存在互相覆盖的可能
                // theta < PI / 2区域是需要的区域，但是theta > PI / 2是不必要的区域，且有可能覆盖需要的区域
                // 因此，需要避免被覆盖
                // curveX表示的是沿着s方向的投影，由于采用的是圆柱仿真翻页模型，对于curveY直接不做任何更改，也就是直接使curveY=t
                val curveX = if (theta < PI / 2) {
                    radius * sin(theta)
                } else {
                    // 由于sin的周期性，大于PI/2时，得到的值可能和小于PI/2的值相同，导致遮挡
                    // 因此，对于在半径radius以内的区域进行弯曲，对于大于radius的区域则不采用任何弯曲特效（即平铺）
                    // 并且保证映射之后的page是连续的
                    s - PI.toFloat() / 2 * radius + radius
                }
                // 将变换后的点再映射回屏幕坐标系
                regionCMeshVerts[i] = originX + curveX * ux - t * uy
                regionCMeshVerts[i + 1] = originY + curveX * uy + t * ux
            }
            // drawBitmapMesh没有matrix参数，因此需要主动调用matrix，应用矩阵变换转换坐标
            val temp = FloatArray(2)
            for (i in regionCMeshVerts.indices step 2) {
                temp[0] = regionCMeshVerts[i]
                temp[1] = regionCMeshVerts[i + 1]
                matrix?.mapPoints(temp)
                regionCMeshVerts[i] = temp[0]
                regionCMeshVerts[i + 1] = temp[1]
            }
        }

        /**
         * 计算RegionA drawBitmapMesh需要的点坐标
         */
        private fun computeRegionAMeshVerts() {
            var index = 0
            for (y in 0..regionAMeshHeight) {
                val fy = containerHeight.toFloat() * y / regionAMeshHeight
                for (x in 0..regionAMeshWidth) {
                    val fx = containerWidth * x.toFloat() / regionAMeshWidth
                    regionAMeshVerts[index * 2] = fx
                    regionAMeshVerts[index * 2 + 1] = fy
                    index++
                }
            }

            val dirX = cornerVertex.x - touchPoint.x
            val dirY = cornerVertex.y - touchPoint.y        // touchPoint指向cornerVertex的向量
            val len = hypot(dirX, dirY)
            val ux = dirX / len                             // 单位方向向量
            val uy = dirY / len
            val originX = bezierEnd1.x                      // bezierEnd1作为原点
            val originY = bezierEnd1.y
            val radiusC = abs((touchPoint.x - bezierVertex1.x) * ux + (touchPoint.y - bezierVertex1.y) * uy)
            val radiusA = (len - PI.toFloat() / 2 * radiusC) / (PI.toFloat() / 2)
            val temp = abs((bezierEnd1.x - bezierVertex1.x) * ux + (bezierEnd1.y - bezierVertex1.y) * uy)
            val axisX = temp - radiusA      // 圆柱轴在新坐标系的x坐标

            for (i in regionAMeshVerts.indices step 2) {
                val x = regionAMeshVerts[i]
                val y = regionAMeshVerts[i + 1]

                // 计算当前mesh vert相对于origin的坐标
                val dx = x - originX
                val dy = y - originY

                // s 是沿翻页轴方向的投影（用于卷曲角度）
                val s = dx * ux + dy * uy
                // t 是垂直于翻页轴的投影（保留该方向用于回变换）
                val t = -dx * uy + dy * ux

                // 对于这部分区域，无需进行弯曲
                if (s < axisX) continue

                val theta = (s - axisX) / radiusA
                val curveX = if (theta < PI / 2) {
                    axisX + radiusA * sin(theta)
                } else {
                    // 由于sin的周期性，大于PI/2时，得到的值可能和小于PI/2的值相同，导致遮挡
                    // 因此，对于在半径radius以内的区域进行弯曲，对于大于radius的区域则不采用任何弯曲特效（即平铺）
                    // 并且保证映射之后的page是连续的
                    s - PI.toFloat() / 2 * radiusA + radiusA
                }
                // 将变换后的点再映射回屏幕坐标系
                regionAMeshVerts[i] = originX + curveX * ux - t * uy
                regionAMeshVerts[i + 1] = originY + curveX * uy + t * ux
            }
        }

        /**
         * 所有的shadow都实际上是矩形区域内的线性渐变
         */
        private fun drawShadow(canvas: Canvas) {
            drawPathAShadow(canvas)
            drawPathBShadow(canvas)
            drawPathCShadow(canvas)
        }

        protected fun drawPathBShadow(canvas: Canvas) {
            val width = 50F      // shadow width
            val v1 = bezierVertex1 - m1
            val v2 = Vec.normalize(v1.copy())           // v1的单位向量
            val v3 = v1 + v2 * width
            val v4 = bezierStart1 + v3
            val v5 = bezierStart2 + v3
            shadowB.reset()
            shadowB.moveTo(bezierStart1.x, bezierStart1.y)
            shadowB.lineTo(v4.x, v4.y)
            shadowB.lineTo(v5.x, v5.y)
            shadowB.lineTo(bezierStart2.x, bezierStart2.y)
            shadowB.close()
            shadowB.op(pathB, Path.Op.INTERSECT)
            // draw shadow
            // 创建渐变，从 bezierStart1 → v4（即阴影方向）
            val shader = LinearGradient(
                bezierStart1.x, bezierStart1.y,
                v4.x, v4.y,
                Color.argb(160, 0, 0, 0),    // 起始颜色：半透明黑
                Color.argb(0, 0, 0, 0),     // 结束颜色：完全透明
                Shader.TileMode.CLAMP
            )
            shadowPaint.shader = shader
            canvas.drawPath(shadowB, shadowPaint)
        }

        protected fun drawPathCShadow(canvas: Canvas) {
            canvas.drawPath(pathC, backShadowPaint)         // 给背面区域添加一个很淡的阴影

            val width = 60F
            val v1 = Vec.normalize(bezierVertex1 - m1)
            val v2 =  v1 * width
            val v3 = bezierVertex1 - v2
            val v4 = bezierVertex2 - v2
            shadowC.reset()
            shadowC.moveTo(bezierVertex1.x, bezierVertex1.y)
            shadowC.lineTo(bezierVertex2.x, bezierVertex2.y)
            shadowC.lineTo(v4.x, v4.y)
            shadowC.lineTo(v3.x, v3.y)
            shadowC.close()
            shadowC.op(pathC, Path.Op.INTERSECT)
            // draw shadow
            val shader = LinearGradient(
                bezierVertex1.x, bezierVertex1.y,
                v3.x, v3.y,
                Color.argb(40, 0, 0, 0),    // 起始颜色：半透明黑
                Color.argb(0, 0, 0, 0),     // 结束颜色：完全透明
                Shader.TileMode.CLAMP
            )
            shadowPaint.shader = shader
            canvas.drawPath(shadowC, shadowPaint)
        }

        protected fun drawPathAShadow(canvas: Canvas) {
            val v1 = Vec.normalize(touchPoint - bezierControl1)
            val v2 = Vec.normalize(touchPoint - bezierControl2)

            val v3 =  v2 * shadowAWidth
            val v4 = bezierControl1 + v3
            val v5 = touchPoint + v3 + (v1 * sqrt(2F) * shadowAWidth)
            shadowA1.reset()
            shadowA1.moveTo(bezierControl1.x, bezierControl1.y)
            shadowA1.lineTo(touchPoint.x, touchPoint.y)
            shadowA1.lineTo(v5.x, v5.y)
            shadowA1.lineTo(v4.x, v4.y)
            shadowA1.close()
            shadowA1.op(pathA, Path.Op.INTERSECT)
            // draw shadow
            val shader1 = LinearGradient(
                bezierControl1.x, bezierControl1.y,
                v4.x, v4.y,
                shadowAStartColor,    // 起始颜色：半透明黑
                shadowAEndColor,     // 结束颜色：完全透明
                Shader.TileMode.CLAMP
            )
            shadowPaint.shader = shader1
            canvas.drawPath(shadowA1, shadowPaint)

            val v6 =  v1 * shadowAWidth
            val v7 = bezierControl2Copy + v6
            shadowA2.reset()
            shadowA2.moveTo(bezierControl2Copy.x, bezierControl2Copy.y)
            shadowA2.lineTo(touchPoint.x, touchPoint.y)
            shadowA2.lineTo(v5.x, v5.y)
            shadowA2.lineTo(v7.x, v7.y)
            shadowA2.close()
            shadowA2.op(pathA, Path.Op.INTERSECT)
            // draw shadow
            val shader2 = LinearGradient(
                bezierControl2Copy.x, bezierControl2Copy.y,
                v7.x, v7.y,
                shadowAStartColor,    // 起始颜色：半透明黑
                shadowAEndColor,     // 结束颜色：完全透明
                Shader.TileMode.CLAMP
            )
            shadowPaint.shader = shader2
            canvas.drawPath(shadowA2, shadowPaint)
        }

        override fun onDestroy() {
            super.onDestroy()
            pageBitmap.clearBitmap()            // 销毁bitmap
        }
    }

    class Style2: FlipOnReleaseLayoutManager.Horizontal(), AnimatedLayoutManager {

        private val pageBitmap = PageBitmap()
        private val gl by lazy { GLRenderer(pageContainer.context) }

        private val screenLineIntersections by lazy { ScreenLineIntersections(pageContainer.width.toFloat(), pageContainer.height.toFloat()) }

        override fun prepareAnim(initDire: PageDirection) {
            when (initDire) {
                PageDirection.NEXT -> {
                    pageBitmap.topBitmap = pageContainer.getCurPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getNextPage()!!.screenshot()
                }
                PageDirection.PREV -> {
                    pageBitmap.topBitmap = pageContainer.getPrevPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getCurPage()!!.screenshot()
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

        private val debugPosPaint = Paint().apply {
            textSize = 36F
            color = Color.RED
        }

        private val debugLinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3F
            color = Color.WHITE
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (pageBitmap.topBitmap != null && pageBitmap.bottomBitmap != null) {
                gl.render(
                    canvas,
                    topBitmap = pageBitmap.topBitmap!!,
                    bottomBitmap = pageBitmap.bottomBitmap!!,
                    mouseX = gesture.cur.x, mouseY = gesture.cur.y, mouseZ = gesture.down.x, mouseW = gesture.down.y
                )
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
    }

    class Style3: FlipOnReleaseLayoutManager.Horizontal(), AnimatedLayoutManager {

        private val pageBitmap = PageBitmap()

        private val topLeftPoint = PointF()
        private val topMiddlePoint = PointF()
        private val topRightPoint = PointF()
        private val bottomLeftPoint = PointF()
        private val bottomMiddlePoint = PointF()
        private val bottomRightPoint = PointF()

        private val rightPageRegion = Path()
        private val leftPageRigion = Path()
        private val allPageRegion = Path()

        private val touchPos = PointF()
        private val downPos = PointF()

        // TODO: 不应该仅和dy有关系，如果没有触及装订线时，应该保持正常的radius
        private val cylinderRadius: Float get() {
            val radius = pageContainer.height * 0.075F
//            val dy = abs(touchPos.y - downPos.y)
//            val scale = 3
//            return if (dy >= radius * scale) {
//                radius
//            } else {
//                dy / scale
//            }
            return radius
        }

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

        // 位于手势点和corner点连线上的点
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

        private fun computePagePointsAndPaths() {
            topLeftPoint.x = -pageContainer.width.toFloat()
            topLeftPoint.y = 0F
            topRightPoint.x = pageContainer.width.toFloat()
            topRightPoint.y = 0F
            bottomLeftPoint.x = -pageContainer.width.toFloat()
            bottomLeftPoint.y = pageContainer.height.toFloat()
            bottomRightPoint.x = pageContainer.width.toFloat()
            bottomRightPoint.y = pageContainer.height.toFloat()
            topMiddlePoint.x = (topRightPoint.x + topLeftPoint.x) / 2
            topMiddlePoint.y = 0F
            bottomMiddlePoint.x = (bottomLeftPoint.x + bottomRightPoint.x) / 2
            bottomMiddlePoint.y = pageContainer.height.toFloat()
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

        override fun prepareAnim(initDire: PageDirection) {
            when (initDire) {
                PageDirection.NEXT -> {
                    pageBitmap.topBitmap = pageContainer.getCurPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getNextPage()!!.screenshot()
                }
                PageDirection.PREV -> {
                    pageBitmap.topBitmap = pageContainer.getPrevPage()!!.screenshot()
                    pageBitmap.bottomBitmap = pageContainer.getCurPage()!!.screenshot()
                }
                else -> throw IllegalStateException()
            }
            computePagePointsAndPaths()
        }

        override fun startNextAnim() {}

        override fun startPrevAnim() {}

        override fun startResetAnim(initDire: PageDirection) {
            super.startResetAnim(initDire)
        }

        override fun onDragging(
            initDire: PageDirection,
            dx: Float,
            dy: Float
        ) {
            super.onDragging(initDire, dx, dy)
            downPos.x = gesture.down.x
            downPos.y = gesture.down.y
            touchPos.x = gesture.cur.x
            touchPos.y = gesture.cur.y
            val pointMode = computePoints()
            computePaths(pointMode)
            pageContainer.invalidate()
        }

        enum class CornerMode {
            TopRightCorner, BottomRightCorner, Landscape
        }

        private fun getCornerMode(): CornerMode = when {
            downPos.y < touchPos.y -> CornerMode.TopRightCorner
            downPos.y > touchPos.y -> CornerMode.BottomRightCorner
            else -> CornerMode.Landscape
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
                // TODO: 这里有个bug，用到了没有更新的cylinderEnglePos
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
            if (abs(cornerPos.y - sineStartPos2.y) >= pageContainer.height) {
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
                    pathC.op(rightPageRegion, Path.Op.INTERSECT)
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
                    pathC.op(rightPageRegion, Path.Op.INTERSECT)
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
                        pathC.op(rightPageRegion, Path.Op.INTERSECT)
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
                        pathC.op(rightPageRegion, Path.Op.INTERSECT)
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
        
        override fun abortAnim() {
            super.abortAnim()
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

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (isAnimRuning || isDragging) {
                canvas.drawPath(pathC, purplePaint)
                canvas.drawPath(pathB, bluePaint)
                canvas.drawPath(pathA, yellowPaint)
//                debug(canvas)
            }
        }

        override fun computeScroll() {
            super.computeScroll()
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

        override fun setAnimDuration(animDuration: Int) {

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

            canvas.drawPoint("", sineP1.x, sineP1.y)
            canvas.drawPoint("", sineP2.x, sineP2.y)
            canvas.drawPoint("landscapeP1", landscapeP1.x, landscapeP1.y)
            canvas.drawPoint("landscapeP2", landscapeP2.x, landscapeP2.y)
            canvas.drawPoint("landscapeP3", landscapeP3.x, landscapeP3.y)
            canvas.drawPoint("landscapeP4", landscapeP4.x, landscapeP4.y)
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
            } else if (y >= pageContainer.height) {
                dy = -debugPosPaint.textSize
            }
            drawText(text, x - length / 2, y + dy, debugPosPaint)
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



    }
}