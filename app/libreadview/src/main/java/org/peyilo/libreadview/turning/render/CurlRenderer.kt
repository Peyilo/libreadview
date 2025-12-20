package org.peyilo.libreadview.turning.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PointF
import android.widget.Scroller

abstract class CurlRenderer {

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
    
    open fun setPageSize(width: Float, height: Float) {
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

    abstract fun initControllPosition(x: Float, y: Float)

    abstract fun updateTouchPosition(curX: Float, curY: Float)

    abstract fun render(canvas: Canvas)

    open fun setPages(top: Bitmap, bottom: Bitmap) {
        _topBitmap = top
        _bottomBitmap = bottom
    }

    open fun release() {
        _topBitmap = null
        _bottomBitmap = null
    }

    abstract fun flipToNextPage(scroller: Scroller, animDuration: Int)

    abstract fun flipToPrevPage(scroller: Scroller, animDuration: Int)

    open fun destory() = Unit

}