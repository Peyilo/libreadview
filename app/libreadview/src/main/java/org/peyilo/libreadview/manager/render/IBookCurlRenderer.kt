package org.peyilo.libreadview.manager.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.widget.Scroller
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import org.peyilo.libreadview.manager.util.addQuarterSineFromHalfPeriod
import org.peyilo.libreadview.manager.util.computeCrossPoint
import org.peyilo.libreadview.manager.util.drawHalfSineCurve
import org.peyilo.libreadview.manager.util.perpendicularFoot
import org.peyilo.libreadview.manager.util.reflectPointAboutLine
import org.peyilo.libreadview.util.LogHelper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.log
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 仿iOS风格的curl翻页
 * TODO：处理装订线
 * TODO: 当page被拖动时，page翻页动画不应该直接跳转到当前位置，而应该有过渡动画，待实现
 */
class IBookCurlRenderer: CurlRenderer {

    companion object {
        private const val TAG = "IBookCurlRenderer"

        private const val POINT_MODE_LANDSCAPE = 0
        private const val POINT_MODE_DOUBLE_SINE = 1
        private const val POINT_MODE_SINGLE_SINE = 2

    }

    /**
     * 开启debug模式以后，将会显示仿真翻页绘制过程中各个关键点的位置以及连线
     */
    var enableDebugMode = false

    private var _topBitmap: Bitmap? = null
    private var _bottomBitmap: Bitmap? = null

    private val topBitmap: Bitmap get() = _topBitmap!!
    private val bottomBitmap: Bitmap get() = _bottomBitmap!!

    private val topLeftPoint = PointF()
    private val topMiddlePoint = PointF()
    private val topRightPoint = PointF()
    private val bottomLeftPoint = PointF()
    private val bottomMiddlePoint = PointF()
    private val bottomRightPoint = PointF()

    private val rightPageRegion = Path()
    private val leftPageRigion = Path()
    private val allPageRegion = Path()

    private val pageWidth get() =  topRightPoint.x - topMiddlePoint.x
    private val pageHeight get() = bottomRightPoint.y - topRightPoint.y

    private val touchPos = PointF()
    private val downPos = PointF()

    private val downAlignRightPos = PointF()        // downPos.y与rightPageRegion右侧对齐时的点

    private var shadowB: Bitmap? = null

    private var shadowCurl: Bitmap? = null

    val matrixShadow = Matrix()

    private val shadowCrossPos = PointF()

    private val cylinderRadius: Float get() {
        val bias = abs(cylinderAxisPos.x - touchPos.x)
        val L = hypot(downAlignRightPos.x - cylinderAxisPos.x, downAlignRightPos.y - cylinderAxisPos.y)
        val raidus = (L / PI).toFloat()
        val res = if (bias == 0F) {
            raidus
        } else {
            val scale = abs(touchPos.y - downAlignRightPos.y) / pageHeight
            val minRaidus = scale.coerceIn(0F, 0.5F) * raidus
            val dis = (1 - hypot(cylinderAxisPos.x - touchPos.x, cylinderAxisPos.y - touchPos.y) / (0.6F * pageWidth)).coerceIn(0F, 1F)
            max(minRaidus, dis * raidus)
        }
        return res
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

    private val pathAC = Path()

    private val debugPosPaint by lazy {
        Paint().apply {
            textSize = 15F
            color = Color.RED
        }
    }

    private val debugLinePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1F
            color = Color.BLACK
        }
    }

    private val regionCMatrixArray = floatArrayOf(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 1F)
    private val regionCMatrix = Matrix()

    var meshWidth = 30
    var meshHeight = 50

    private val meshVertsCount = (meshWidth + 1) * (meshHeight + 1)
    private val regionCMeshVerts = FloatArray(meshVertsCount * 2)
    private val regionAMeshVerts = FloatArray(meshVertsCount * 2)

    override fun setPageSize(width: Float, height: Float) {
        topLeftPoint.x = -width
        topLeftPoint.y = 0F
        topRightPoint.x = width
        topRightPoint.y = 0F
        bottomLeftPoint.x = -width
        bottomLeftPoint.y = height
        bottomRightPoint.x = width
        bottomRightPoint.y = height
        topMiddlePoint.x = (topRightPoint.x + topLeftPoint.x) / 2
        topMiddlePoint.y = topRightPoint.y
        bottomMiddlePoint.x = (bottomLeftPoint.x + bottomRightPoint.x) / 2
        bottomMiddlePoint.y = bottomRightPoint.y
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

    override fun initControllPosition(x: Float, y: Float) {
        downPos.x = x
        downPos.y = y
        touchPos.x = downPos.x
        touchPos.y = downPos.y
    }

    private enum class CornerMode {
        TopRightCorner, BottomRightCorner, Landscape
    }

    private fun getCornerMode(): CornerMode = when {
        downPos.y < touchPos.y -> CornerMode.TopRightCorner
        downPos.y > touchPos.y -> CornerMode.BottomRightCorner
        else -> CornerMode.Landscape
    }

    private fun computePoints(): Int {
        val cornerMode = getCornerMode()
        downAlignRightPos.x = topRightPoint.x
        downAlignRightPos.y = downPos.y

        // process origin point
        // TODO: downAlignRightPos和touchPos可能会完全重合, 导致计算出现错误
        var mouseDirX = downAlignRightPos.x - touchPos.x
        var mouseDirY = downAlignRightPos.y - touchPos.y
        var mouseLength = hypot(mouseDirX, mouseDirY)
        mouseDirX /= mouseLength
        mouseDirY /= mouseLength

        // process axis point
        cylinderAxisPos.x = touchPos.x
        cylinderAxisPos.y = touchPos.y

        val isLandscape = cornerMode == CornerMode.Landscape
        if (isLandscape) {
            // 在水平滑动状态时，只需要确定四个点的坐标即可确定A、B、C三个区域
            landscapeP1.y = topMiddlePoint.y
            landscapeP2.y = topMiddlePoint.y
            landscapeP3.y = bottomMiddlePoint.y
            landscapeP4.y = bottomMiddlePoint.y

            landscapeP1.x = cylinderAxisPos.x
            landscapeP2.x = cylinderAxisPos.x + cylinderRadius
            landscapeP3.x = cylinderAxisPos.x
            landscapeP4.x = cylinderAxisPos.x + cylinderRadius
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
        } else {
            cylinderAxisLineStartPos.x = cylinderAxisPos.x      // landscape状态下，cylinderAxisLineStartPos在top位置
            cylinderAxisLineStartPos.y = topMiddlePoint.y
            cylinderAxisLineEndPos.x = cylinderAxisPos.x
            cylinderAxisLineEndPos.y = bottomMiddlePoint.y
        }
        computeShadowPoints()

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
        } else {
            // process corner point
            cornerPos.x = topRightPoint.x
            cornerPos.y = topRightPoint.y

            // process engle line
            cylinderEngleLineStartPos.x = cylinderEnglePos.x
            cylinderEngleLineStartPos.y = topMiddlePoint.y
            cylinderEngleLineEndPos.x =  cylinderEnglePos.x
            cylinderEngleLineEndPos.y = bottomMiddlePoint.y

            // process engle project line
            cylinderEngleLineProjStartPos.x = cylinderEngleProjPos.x
            cylinderEngleLineProjStartPos.y = topMiddlePoint.y
            cylinderEngleLineProjEndPos.x =   cylinderEngleProjPos.x
            cylinderEngleLineProjEndPos.y = bottomMiddlePoint.y

            // process axis project line
            cylinderAxisLineProjStartPos.x = cylinderAxisProjPos.x
            cylinderAxisLineProjStartPos.y = topMiddlePoint.y
            cylinderAxisLineProjEndPos.x =  cylinderAxisProjPos.x
            cylinderAxisLineProjEndPos.y = bottomMiddlePoint.y
        }

        // 处理镜像对称点: 关于cylinderEngleProjLine的镜像
        reflectPointAboutLine(cornerPos.x, cornerPos.y,
            cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
            cornerProjPos.x = first
            cornerProjPos.y = second
        }

        if (isLandscape) return POINT_MODE_LANDSCAPE

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
            return POINT_MODE_SINGLE_SINE
        } else {
            return POINT_MODE_DOUBLE_SINE
        }
    }

    /**
     * 注意，当pathC计算完毕以后，不要进行pathC.op(allPageRegion, Path.Op.INTERSECT)操作，用pathC.op(rightPageRegion, Path.Op.INTERSECT)代替，
     * 前者会造成严重的性能问题：随着调用次数增加，执行时间大幅度增加
     */
    private fun computePaths(pointMode: Int) {
        when (pointMode) {
            POINT_MODE_LANDSCAPE -> {
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
            POINT_MODE_DOUBLE_SINE -> {
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
            POINT_MODE_SINGLE_SINE -> {
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

        pathAC.apply {
            reset()
            addPath(pathA)
            addPath(pathC)
        }
    }

    private fun computeTime(msg: String, block: () -> Unit) {
        val start = System.nanoTime()
        block()
        val end = System.nanoTime()
        LogHelper.d(TAG, "$msg: ${(end - start) / 1000}us")
    }

    /**
     * 性能分析：
     * computePoints: 93us
     * computePaths: 200us
     * computeRegionAMeshVerts: 3967us
     * computeRegionCMeshVerts: 1282us
     */
    override fun updateTouchPosition(curX: Float, curY: Float) {
        touchPos.x = curX
        touchPos.y = curY
        val pointMode = computePoints()
        computePaths(pointMode)
        // TODO: 关于verts的计算，可以考虑使用c编写的native方法代替
        computeRegionAMeshVerts()
        computeRegionCMeshVerts()
    }

    // 通过ColorMatrixColorFilter实现纸张背面区域的颜色变浅效果
    private fun makeBackPagePaint(): Paint {
        // 矩阵思路：
        // 如果是白色的背景黑色的字体，那么背面就是依旧保持白色背景，但是黑色的字体要变成浅黑色
        // 1. 保持亮色（白色）接近不变
        // 2. 黑色被抬高，变成灰色
        val cm = ColorMatrix().apply {
            // 把对比度调低，亮部保持，暗部抬高
            set(floatArrayOf(
                0.5f, 0f,   0f,   0f, 128f,   // R
                0f,   0.5f, 0f,   0f, 128f,   // G
                0f,   0f,   0.5f, 0f, 128f,   // B
                0f,   0f,   0f,   1f, 0f      // A
            ))
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
    }

    val paintBack = makeBackPagePaint()

    override fun render(canvas: Canvas) {
        // draw region A
        canvas.withClip(pathA) {
            canvas.drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                regionAMeshVerts, 0, null, 0, null)
        }

        // draw region B
        canvas.withClip(pathB) {
            drawBitmap(bottomBitmap, 0F, 0F, null)
        }

        // draw region C
        canvas.withClip(pathC) {
            canvas.drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                regionCMeshVerts, 0, null, 0, paintBack)
        }

        // draw shadow
        drawShadow(canvas)

        if(enableDebugMode) debug(canvas)
    }

    override fun setPages(top: Bitmap, bottom: Bitmap) {
        _topBitmap = top
        _bottomBitmap = bottom
    }

    override fun release() {
        _topBitmap = null
        _bottomBitmap = null
    }

    private fun debug(canvas: Canvas) {
        canvas.drawPoint("Down", downPos.x, downPos.y)
        canvas.drawPoint("Touch", touchPos.x, touchPos.y)
        canvas.drawLine(downPos.x, downPos.y, touchPos.x, touchPos.y, debugLinePaint)
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
        canvas.drawPoint("cylinderEngleProjPos", cylinderEngleProjPos.x, cylinderEngleProjPos.y)
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

        // 将坐标转换为整数后显示
        canvas.drawPoint("P1(${landscapeP1.x.toInt()}, ${landscapeP1.y.toInt()})", landscapeP1.x, landscapeP1.y)
        canvas.drawPoint("P2(${landscapeP2.x.toInt()}, ${landscapeP2.y.toInt()})", landscapeP2.x, landscapeP2.y)
        canvas.drawPoint("P3(${landscapeP3.x.toInt()}, ${landscapeP3.y.toInt()})", landscapeP3.x, landscapeP3.y)
        canvas.drawPoint("P4(${landscapeP4.x.toInt()}, ${landscapeP4.y.toInt()})", landscapeP4.x, landscapeP4.y)

        canvas.drawPoint("downAlignRightPos", downAlignRightPos.x, downAlignRightPos.y)
        canvas.drawLine(topMiddlePoint.x, topMiddlePoint.y, downAlignRightPos.x, downAlignRightPos.y, debugLinePaint)
        canvas.drawLine(cylinderAxisPos.x, cylinderAxisPos.y, downAlignRightPos.x, downAlignRightPos.y, debugLinePaint)
        canvas.drawLine(topMiddlePoint.x, topMiddlePoint.y, cylinderAxisPos.x, cylinderAxisPos.y, debugLinePaint)
    }

    private fun Canvas.drawPoint(text: String, x: Float, y: Float) {
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

    /**
     * 计算RegionA drawBitmapMesh需要的点坐标
     */
    private fun computeRegionAMeshVerts() {
        var index = 0
        for (y in 0..meshHeight) {
            val fy = pageHeight * y / meshHeight
            for (x in 0..meshWidth) {
                val fx = pageWidth * x.toFloat() / meshWidth
                regionAMeshVerts[index * 2] = fx
                regionAMeshVerts[index * 2 + 1] = fy
                index++
            }
        }

        val dirX = downAlignRightPos.x - cylinderAxisPos.x
        val dirY = downAlignRightPos.y - cylinderAxisPos.y
        val len = hypot(dirX, dirY)
        val ux = dirX / len                                  // 单位方向向量
        val uy = dirY / len
        val originX = cylinderAxisPos.x                      // cylinderAxisPos作为原点
        val originY = cylinderAxisPos.y

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
            if (s < 0F) continue

            val theta = s / cylinderRadius
            val curveX = if (theta < PI / 2) {
                cylinderRadius * sin(theta)
            } else {
                // 由于sin的周期性，大于PI/2时，得到的值可能和小于PI/2的值相同，导致遮挡
                // 因此，对于在半径radius以内的区域进行弯曲，对于大于radius的区域则不采用任何弯曲特效（即平铺）
                // 并且保证映射之后的page是连续的
                s - PI.toFloat() / 2 * cylinderRadius + cylinderRadius
            }
            // 将变换后的点再映射回屏幕坐标系
            regionAMeshVerts[i] = originX + curveX * ux - t * uy
            regionAMeshVerts[i + 1] = originY + curveX * uy + t * ux
        }
    }

    /**
     * 计算RegionC drawBitmapMesh需要的点坐标，并对点坐标应用matrix的线性变换
     */
    private fun computeRegionCMeshVerts() {
        // 计算两个控制点之间的距离
        // 以cylinderEngleProjPos、cylinderEngleLineProjStartPos的连线作为镜像轴
        // 计算旋转矩阵
        val dis = hypot(cylinderEngleProjPos.x - cylinderEngleLineProjStartPos.x,
            cylinderEngleProjPos.y - cylinderEngleLineProjStartPos.y)
        val sin = (cylinderEngleProjPos.x - cylinderEngleLineProjStartPos.x) / dis
        val cos = (cylinderEngleProjPos.y - cylinderEngleLineProjStartPos.y) / dis
        regionCMatrixArray[0] = -(1 - 2 * sin * sin)
        regionCMatrixArray[1] = 2 * sin * cos
        regionCMatrixArray[3] = 2 * sin * cos
        regionCMatrixArray[4] = 1 - 2 * sin * sin
        regionCMatrix.reset()
        regionCMatrix.setValues(regionCMatrixArray)
        // 将坐标原点平移到bezierControl1处
        val dx = -cylinderEngleProjPos.x
        val dy = -cylinderEngleProjPos.y
        regionCMatrix.preTranslate(dx, dy)
        regionCMatrix.postTranslate(-dx, -dy)

        var index = 0
        for (y in 0..meshHeight) {
            val fy = pageHeight * y / meshHeight
            for (x in 0..meshWidth) {
                val fx = pageWidth * x.toFloat() / meshWidth
                regionCMeshVerts[index * 2] = fx
                regionCMeshVerts[index * 2 + 1] = fy
                index++
            }
        }

        val dirX = cylinderAxisPos.x - downAlignRightPos.x
        val dirY = cylinderAxisPos.y - downAlignRightPos.y
        val len = hypot(dirX, dirY)
        val ux = dirX / len                                  // 单位方向向量
        val uy = dirY / len
        val originX = cylinderAxisProjPos.x                      // cylinderAxisProjPos作为原点
        val originY = cylinderAxisProjPos.y

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

            // 对于这部分区域，无需进行弯曲
            if (s < 0F) continue

            // 得到的s和t构成了相对于一个新坐标系的坐标，该坐标系的其中一条轴由cornerVertex指向touchPoint，
            // 另一条则是垂直于cornerVertex指向touchPoint的连线
            // 将 s 映射到圆柱体表面
            val theta = s / cylinderRadius
            // 当cornerVertex为右上角的顶点时，映射并不正确，推测是因为meshVerts中多个点位置可能存在互相覆盖的可能
            // theta < PI / 2区域是需要的区域，但是theta > PI / 2是不必要的区域，且有可能覆盖需要的区域
            // 因此，需要避免被覆盖
            // curveX表示的是沿着s方向的投影，由于采用的是圆柱仿真翻页模型，对于curveY直接不做任何更改，也就是直接使curveY=t
            val curveX = if (theta < PI / 2) {
                cylinderRadius * sin(theta)
            } else {
                // 由于sin的周期性，大于PI/2时，得到的值可能和小于PI/2的值相同，导致遮挡
                // 因此，对于在半径radius以内的区域进行弯曲，对于大于radius的区域则不采用任何弯曲特效（即平铺）
                // 并且保证映射之后的page是连续的
                s - PI.toFloat() / 2 * cylinderRadius + cylinderRadius
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
            regionCMatrix.mapPoints(temp)
            regionCMeshVerts[i] = temp[0]
            regionCMeshVerts[i + 1] = temp[1]
        }
    }

    /**
     * 绘制阴影
     */
    private fun drawShadow(canvas: Canvas) {
        drawShadowA(canvas)
        drawShadowB(canvas)
        drawShadowC(canvas)
    }

    private fun computeShadowPoints() {
        val cornerMode = getCornerMode()
        if (cornerMode == CornerMode.Landscape) {
            shadowCrossPos.x = cylinderAxisLineEndPos.x
            shadowCrossPos.y = cylinderAxisLineEndPos.y
            return
        }
        val res = computeCrossPoint(cylinderAxisPos, cylinderAxisLineStartPos,
            topRightPoint, bottomRightPoint, shadowCrossPos
        )
        if (cornerMode == CornerMode.TopRightCorner) {
            if (!res || shadowCrossPos.y > bottomRightPoint.y) {
                perpendicularFoot(bottomRightPoint.x, bottomRightPoint.y, cylinderAxisPos.x,
                    cylinderAxisPos.y, cylinderAxisLineStartPos.x,
                    cylinderAxisLineStartPos.y, shadowCrossPos)
            }
        } else {
            if (!res || shadowCrossPos.y < topRightPoint.y) {
                perpendicularFoot(topRightPoint.x, topRightPoint.y, cylinderAxisPos.x,
                    cylinderAxisPos.y, cylinderAxisLineStartPos.x,
                    cylinderAxisLineStartPos.y, shadowCrossPos)
            }
        }
    }

    private fun drawShadowA(canvas: Canvas) {
        // TODO
    }

    private fun drawShadowC(canvas: Canvas) {
        if (cylinderRadius == 0F) {
            return
        }
        if (shadowCurl == null) {
            shadowCurl = makeShadowLut(kOfT = { t ->
                (1 - t).coerceIn(0f, 1f).toDouble().pow(0.2).toFloat()
            }, color = 0x808080)
        }

        val dx = cylinderAxisPos.x - cylinderAxisLineStartPos.x
        val dy = cylinderAxisPos.y - cylinderAxisLineStartPos.y

        // 计算角度（让 Bitmap 的高度方向对齐 AB）
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() - 90f

        val wWidth = cylinderRadius / shadowCurl!!.width
        val cornerMode = getCornerMode()
        val wHeight = hypot(shadowCrossPos.x - cylinderAxisLineStartPos.x,
            shadowCrossPos.y - cylinderAxisLineStartPos.y)
        when (cornerMode) {
            CornerMode.BottomRightCorner -> {
                // TopRightCorner和BottomRightCorner的阴影方向相反
                angle += 180F
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postRotate(angle)
                    postTranslate(shadowCrossPos.x, shadowCrossPos.y)
                }
            }
            CornerMode.TopRightCorner -> {
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postRotate(angle)
                    postTranslate(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                }
            }
            else -> {
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postTranslate(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                }
            }
        }
        canvas.withClip(pathAC) {
            canvas.drawBitmap(shadowCurl!!, matrixShadow, null)
        }
    }

    private fun drawShadowB(canvas: Canvas) {
        // 需要想办法优化
        if (shadowB == null) {
            shadowB = makeShadowLut()
        }
        val dx = cylinderAxisPos.x - cylinderAxisLineStartPos.x
        val dy = cylinderAxisPos.y - cylinderAxisLineStartPos.y

        // 计算角度（让 Bitmap 的高度方向对齐 AB）
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() - 90f

        val wWidth = 1F * canvas.width / shadowB!!.width
        val cornerMode = getCornerMode()
        val wHeight = hypot(shadowCrossPos.x - cylinderAxisLineStartPos.x,
            shadowCrossPos.y - cylinderAxisLineStartPos.y)
        when (cornerMode) {
            CornerMode.BottomRightCorner -> {
                // TopRightCorner和BottomRightCorner的阴影方向相反
                angle += 180F
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postRotate(angle)
                    postTranslate(shadowCrossPos.x, shadowCrossPos.y)
                }
            }
            CornerMode.TopRightCorner -> {
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postRotate(angle)
                    postTranslate(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                }
            }
            else -> {
                matrixShadow.apply {
                    reset()
                    postScale(wWidth, wHeight)
                    postTranslate(cylinderAxisLineStartPos.x, cylinderAxisLineStartPos.y)
                }
            }
        }

        canvas.withClip(pathB) {
            canvas.drawBitmap(shadowB!!, matrixShadow, null)
        }
    }

    private fun makeShadowLut(
        width: Int = 256,
        kOfT: (Float) -> Float = { t ->
            t.coerceIn(0f, 1f).toDouble().pow(0.2).toFloat()
        },
        color: Int = 0x000000
    ): Bitmap {
        val bmp = createBitmap(width, 1)
        val pixels = IntArray(width)

        for (x in 0 until width) {
            val t = x / (width - 1f) // 归一化到 [0,1]
            val k = kOfT(t).coerceIn(0f, 1f)
            val alpha = ((1f - k) * 255f).toInt().coerceIn(0, 255)
            pixels[x] = (alpha shl 24) or color // 黑色 + alpha
        }

        bmp.setPixels(pixels, 0, width, 0, 0, width, 1)
        return bmp
    }

    override fun flipToNextPage(scroller: Scroller, animDuration: Int) {
        val dx = - touchPos.x.toInt() + topLeftPoint.x.toInt()
        val dy = - touchPos.y.toInt() + downPos.y.toInt()
        scroller.startScroll(touchPos.x.toInt(), touchPos.y.toInt(),
            dx, dy, animDuration)
    }

    override fun flipToPrevPage(scroller: Scroller, animDuration: Int) {
        val dx = - touchPos.x.toInt() + topRightPoint.x.toInt()
        val dy = - touchPos.y.toInt() + downPos.y.toInt()
        scroller.startScroll(touchPos.x.toInt(), touchPos.y.toInt(),
            dx, dy, animDuration)
    }

    override fun destory() {
        super.destory()
        release()
        shadowCurl?.recycle()
        shadowB?.recycle()
        shadowCurl = null
        shadowB = null
    }
}