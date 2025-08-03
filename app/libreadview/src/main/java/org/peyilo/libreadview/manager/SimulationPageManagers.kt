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
import android.util.Log
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.withClip
import org.peyilo.libreadview.PageContainer.PageDirection
import org.peyilo.libreadview.utils.Vec
import org.peyilo.libreadview.utils.copy
import org.peyilo.libreadview.utils.rakeRadio
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
class SimulationPageManagers {

    companion object {
        private const val TAG = "SimulationPageManagers"
    }

    /**
     * 仿真翻页实现1，常见于Android端各种小说阅读软件的仿真翻页实现
     * TODO：尽可能不在绘制中创建大量的PointF对象，由于对象全部保存在堆上，可能造成频繁地GC
     * TODO: 向上翻页时，动画实在过于简陋，尝试采用iOS iBook那种实现
     */
    open class Style1: FlipOnReleaseLayoutContainer.Horizontal(), AnimatedLayoutManager {

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

        private class PageBitmap {
            var topBitmap: Bitmap? = null
                set(value) {
                    field?.let {
                        try {           // 如果不为null，尝试释放bitmap
                            field!!.recycle()
                        } catch (e: Exception) {
                            Log.e(TAG, "topBitmap: ${e.stackTrace}")
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
                            Log.e(TAG, "topBitmap: ${e.stackTrace}")
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
            if (pageContainer.itemCount >= 2) {
                pageContainer.apply {
                    getPrevPage()?.translationX = -containerWidth.toFloat()
                    getNextPage()?.translationX = 0F
                }
            }
        }

        override fun onPrevCarouselLayout() {
            pageContainer.apply {
                // 被拖动的View需要主动移出画面内，不像其他PageManager会通过设置translationX达到动画的效果
                getPrevPage()?.translationX = -containerWidth.toFloat()
                getCurPage()?.translationX = 0F
            }
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

    class Style2: FlipOnReleaseLayoutContainer.Horizontal(), AnimatedLayoutManager {

        override fun prepareAnim(initDire: PageDirection) {
            TODO("Not yet implemented")
        }

        override fun startNextAnim() {
            TODO("Not yet implemented")
        }

        override fun startPrevAnim() {
            TODO("Not yet implemented")
        }

        override fun onNextCarouselLayout() {
            TODO("Not yet implemented")
        }

        override fun onPrevCarouselLayout() {
            TODO("Not yet implemented")
        }

        override fun setAnimDuration(animDuration: Int) {
            TODO("Not yet implemented")
        }
    }

}