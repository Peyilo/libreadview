package org.peyilo.libreadview.manager.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.widget.Scroller
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import org.peyilo.libreadview.manager.util.buildPath
import org.peyilo.libreadview.manager.util.computeCrossPoint
import org.peyilo.libreadview.manager.util.perpendicularFoot
import org.peyilo.libreadview.manager.util.reflectPointAboutLine
import org.peyilo.libreadview.util.LogHelper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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
    }

    /**
     * 开启debug模式以后，将会显示仿真翻页绘制过程中各个关键点的位置以及连线
     */
    var enableDebugMode = true

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

    private val matrixShadow = Matrix()

    private val shadowCrossPos = PointF()

    private val cylinderRadius: Float get() {
        val bias = abs(cylinderAxisPos.x - touchPos.x)
        val len = hypot(downAlignRightPos.x - cylinderAxisPos.x, downAlignRightPos.y - cylinderAxisPos.y)
        val raidus = (len / PI).toFloat()
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
            strokeWidth = 3F
            color = Color.BLACK
        }
    }

    private val paintBack = makeBackPagePaint()

    private val regionCMatrixArray = floatArrayOf(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 1F)
    private val regionCMatrix = Matrix()

    var meshWidth = 30
    var meshHeight = 50

    private val meshVertsCount = (meshWidth + 1) * (meshHeight + 1)
    private val regionCMeshVerts = FloatArray(meshVertsCount * 2)
    private val regionAMeshVerts = FloatArray(meshVertsCount * 2)

    private val engleA1 = FloatArray((meshWidth + 1) * 2)
    private val engleA2 = FloatArray((meshWidth + 1) * 2)
    private val engleA3 = FloatArray((meshHeight + 1) * 2)
    private val engleA4 = FloatArray((meshHeight + 1) * 2)

    private val engleC1 = FloatArray((meshWidth + 1) * 2)
    private val engleC2 = FloatArray((meshWidth + 1) * 2)
    private val engleC3 = FloatArray((meshHeight + 1) * 2)
    private val engleC4 = FloatArray((meshHeight + 1) * 2)

    private val meshFrontPagePath = Path()
    private val meshBackPagePath = Path()

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

    private fun computePoints() {
        val cornerMode = getCornerMode()
        downAlignRightPos.x = topRightPoint.x + 20F
        downAlignRightPos.y = downPos.y

        // process origin point
        // TODO: downAlignRightPos和touchPos可能会完全重合, 导致计算出现错误
        var mouseDirX = downAlignRightPos.x - touchPos.x
        var mouseDirY = downAlignRightPos.y - touchPos.y
        val mouseLength = hypot(mouseDirX, mouseDirY)
        mouseDirX /= mouseLength
        mouseDirY /= mouseLength

        // process axis point
        cylinderAxisPos.x = touchPos.x
        cylinderAxisPos.y = touchPos.y

        // process axis line
        // 正交于mouseDir，(mouseDirY, -mouseDirX)
        when (cornerMode) {
            CornerMode.TopRightCorner -> {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - topMiddlePoint.y)
                cylinderAxisLineStartPos.y = topMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            }
            CornerMode.BottomRightCorner -> {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x + mouseDirY / mouseDirX * (cylinderAxisPos.y - bottomMiddlePoint.y)
                cylinderAxisLineStartPos.y = bottomMiddlePoint.y

                cylinderAxisLineEndPos.x = topRightPoint.x
                cylinderAxisLineEndPos.y = cylinderAxisPos.y + (-mouseDirX) / mouseDirY * (cylinderAxisLineEndPos.x - cylinderAxisPos.x)
            }
            else -> {
                cylinderAxisLineStartPos.x = cylinderAxisPos.x      // landscape状态下，cylinderAxisLineStartPos在top位置
                cylinderAxisLineStartPos.y = topMiddlePoint.y
                cylinderAxisLineEndPos.x = cylinderAxisPos.x
                cylinderAxisLineEndPos.y = bottomMiddlePoint.y
            }
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

        when (cornerMode) {
            CornerMode.TopRightCorner -> {
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
            }
            CornerMode.BottomRightCorner -> {
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
            else -> {
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
        }

        // 处理镜像对称点: 关于cylinderEngleProjLine的镜像
        reflectPointAboutLine(cornerPos.x, cornerPos.y,
            cylinderEngleLineProjStartPos.x, cylinderEngleLineProjStartPos.y,
            cylinderEngleProjPos.x, cylinderEngleProjPos.y).apply {
            cornerProjPos.x = first
            cornerProjPos.y = second
        }
    }

    private fun computePaths() {
        // TODO: 优化性能
        buildBackPagePath()
        buildFrontPagePath()
        pathAC.reset()
        pathAC.addPath(meshFrontPagePath)

        // compute pathC
        pathC.reset()
        pathC.addPath(meshBackPagePath)

        // compute pathA
        computeTime("computePaths.PathA") {
            pathA.reset()
            pathA.addPath(meshFrontPagePath)
            // 这一步非常耗时
//            pathA.op(pathC, Path.Op.DIFFERENCE)
        }

        // compute pathB
        computeTime("computePaths.pathB") {
            pathB.reset()
            pathB.addPath(rightPageRegion)
            pathB.op(pathAC, Path.Op.DIFFERENCE)
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
     * computePoints: 27us
     * computeRegionAMeshVerts: 143us
     * computeRegionCMeshVerts: 173us
     * computePaths: 13353us
     */
    override fun updateTouchPosition(curX: Float, curY: Float) {
        touchPos.x = curX
        touchPos.y = curY
        computeTime("updateTouchPosition.computePoints") {
            computePoints()
        }
        computeTime("updateTouchPosition.computeRegionAMeshVerts") {
            computeRegionAMeshVerts()
        }
        computeTime("updateTouchPosition.computeRegionCMeshVerts") {
            computeRegionCMeshVerts()
        }
        computeTime("updateTouchPosition.computePaths") {
            computePaths()
        }
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


    override fun render(canvas: Canvas) {
        computeTime("render") {
            // draw region B
            canvas.withClip(pathB) {
                drawBitmap(bottomBitmap, 0F, 0F, null)
                drawBottomPageShadow(canvas)
            }

            // draw region A
            canvas.withClip(pathA) {
                drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                    regionAMeshVerts, 0, null, 0, null)
            }

            // draw region C
            canvas.withClip(pathC) {
                drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                    regionCMeshVerts, 0, null, 0, paintBack)

            }

            // draw shadow
            drawShadowA(canvas)
            drawCylinderShadow(canvas)

            if(enableDebugMode) debug(canvas)
        }
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
        canvas.drawPath(meshFrontPagePath, debugLinePaint)
        canvas.drawPath(meshBackPagePath, debugLinePaint)

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

    private fun initMeshVerts(verts: FloatArray) {
        var index = 0
        for (y in 0..meshHeight) {
            val fy = pageHeight * y / meshHeight
            for (x in 0..meshWidth) {
                val fx = pageWidth * x.toFloat() / meshWidth
                verts[index * 2] = fx
                verts[index * 2 + 1] = fy
                index++
            }
        }
    }

    /**
     * 计算RegionA drawBitmapMesh需要的点坐标
     */
    private fun computeRegionAMeshVerts() {
        initMeshVerts(regionAMeshVerts)

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
                cylinderRadius
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

        initMeshVerts(regionCMeshVerts)

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
                cylinderRadius
            }
            // 将变换后的点再映射回屏幕坐标系
            regionCMeshVerts[i] = originX + curveX * ux - t * uy
            regionCMeshVerts[i + 1] = originY + curveX * uy + t * ux
        }
        // drawBitmapMesh没有matrix参数，因此需要主动调用matrix，应用矩阵变换转换坐标
        regionCMatrix.mapPoints(regionCMeshVerts)
    }

    private fun buildFrontPagePath() {
        for (i in engleA1.indices step 2) {
            engleA1[i] = regionAMeshVerts[i]
            engleA1[i + 1] = regionAMeshVerts[i + 1]
        }

        for (i in engleA2.indices step 2) {
            engleA2[i] = regionAMeshVerts[i + (meshWidth + 1) * meshHeight * 2]
            engleA2[i + 1] = regionAMeshVerts[i + 1 + (meshWidth + 1) * meshHeight * 2]
        }

        for (i in engleA3.indices step 2) {
            engleA3[i] = regionAMeshVerts[i * (meshWidth + 1)]
            engleA3[i + 1] = regionAMeshVerts[i * (meshWidth + 1) + 1]
        }

        for (i in engleA4.indices step 2) {
            engleA4[i] = regionAMeshVerts[i * (meshWidth + 1) + meshWidth * 2]
            engleA4[i + 1] = regionAMeshVerts[i * (meshWidth + 1) + meshWidth * 2 + 1]
        }
        meshFrontPagePath.buildPath(engleA1, engleA2, engleA3, engleA4)
    }

    private fun buildBackPagePath() {
        // 计算扭曲后的page边缘Path
        for (i in engleC1.indices step 2) {
            engleC1[i] = regionCMeshVerts[i]
            engleC1[i + 1] = regionCMeshVerts[i + 1]
        }
        for (i in engleC2.indices step 2) {
            engleC2[i] = regionCMeshVerts[i + (meshWidth + 1) * meshHeight * 2]
            engleC2[i + 1] = regionCMeshVerts[i + 1 + (meshWidth + 1) * meshHeight * 2]
        }
        for (i in engleC3.indices step 2) {
            engleC3[i] = regionCMeshVerts[i * (meshWidth + 1)]
            engleC3[i + 1] = regionCMeshVerts[i * (meshWidth + 1) + 1]
        }
        for (i in engleC4.indices step 2) {
            engleC4[i] = regionCMeshVerts[i * (meshWidth + 1) + meshWidth * 2]
            engleC4[i + 1] = regionCMeshVerts[i * (meshWidth + 1) + meshWidth * 2 + 1]
        }
        meshBackPagePath.buildPath(engleC1, engleC2, engleC3, engleC4)
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

    private fun drawCylinderShadow(canvas: Canvas) {
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

    private fun drawBottomPageShadow(canvas: Canvas) {
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
        canvas.drawBitmap(shadowB!!, matrixShadow, null)
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
