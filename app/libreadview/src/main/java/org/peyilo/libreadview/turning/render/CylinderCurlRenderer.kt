package org.peyilo.libreadview.turning.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import androidx.core.graphics.withClip

abstract class CylinderCurlRenderer: CurlRenderer {

    /**
     * 开启debug模式以后，将会显示仿真翻页绘制过程中各个关键点的位置以及连线
     */
    var enableDebugMode = false

    private var _topBitmap: Bitmap? = null
    private var _bottomBitmap: Bitmap? = null

    protected val topBitmap: Bitmap get() = _topBitmap!!
    protected val bottomBitmap: Bitmap get() = _bottomBitmap!!

    protected val topLeftPoint = PointF()
    protected val topMiddlePoint = PointF()
    protected val topRightPoint = PointF()
    protected val bottomLeftPoint = PointF()
    protected val bottomMiddlePoint = PointF()
    protected val bottomRightPoint = PointF()

    protected val rightPageRegion = Path()
    protected val leftPageRigion = Path()
    protected val allPageRegion = Path()

    protected val pageWidth get() =  topRightPoint.x - topMiddlePoint.x
    protected val pageHeight get() = bottomRightPoint.y - topRightPoint.y

    protected val touchPos = PointF()                     // 触摸点
    protected val cornerPos = PointF()                 // 页脚顶点

    protected val cylinderAxisPos = PointF()
    protected val cylinderEnglePos = PointF()
    protected val cylinderAxisProjPos = PointF()
    protected val cylinderEngleProjPos = PointF()

    protected val cylinderAxisLineStartPos = PointF()
    protected val cylinderAxisLineEndPos = PointF()
    protected val cylinderEngleLineStartPos = PointF()
    protected val cylinderEngleLineEndPos = PointF()

    protected val cylinderAxisLineProjStartPos = PointF()
    protected val cylinderAxisLineProjEndPos = PointF()
    protected val cylinderEngleLineProjStartPos = PointF()
    protected val cylinderEngleLineProjEndPos = PointF()

    protected val sineStartPos1 = PointF()
    protected val sineStartPos2 = PointF()
    protected val sineMaxPos1 = PointF()
    protected val sineMaxPos2 = PointF()

    /**
     * 仿真翻页时有三个区域：A、B、C
     * A区域：当前页区域
     * B区域：下一页区域
     * C区域：当前页背面区域
     */
    protected val pathA = Path()
    protected val pathB = Path()
    protected val pathC = Path()

    private val regionCMatrixArray = floatArrayOf(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 1F)
    private val regionCMatrix = Matrix()

    /**
     * 设置渲染仿真翻页扭曲部分区域的纵向采样点数量：采样点越多，越精确，但是计算量也越高
     */
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

    override fun updateTouchPosition(curX: Float, curY: Float) {
        touchPos.x = curX
        touchPos.y = curY
        computePoints()
        computePaths()
        computeRegionAMeshVerts()
        computeRegionCMeshVerts()
    }

    protected abstract fun computePoints()

    protected abstract fun computePaths()

    protected abstract fun computeRegionAMeshVerts()

    protected abstract fun computeRegionCMeshVerts()

    protected abstract fun drawShadow(canvas: Canvas)

    override fun render(canvas: Canvas) {
        canvas.withClip(pathA) {
            canvas.drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                regionAMeshVerts, 0, null, 0, null)
        }
        canvas.withClip(pathB) {
            drawBitmap(bottomBitmap, 0F, 0F, null)
        }
        canvas.withClip(pathC) {
            canvas.drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                regionCMeshVerts, 0, null, 0, null)
        }

        drawShadow(canvas)

        if(enableDebugMode) debug(canvas)
    }

    protected abstract fun debug(canvas: Canvas)

    override fun setPages(top: Bitmap, bottom: Bitmap) {
        _topBitmap = top
        _bottomBitmap = bottom
    }

    override fun release() {
        _topBitmap = null
        _bottomBitmap = null
    }

}