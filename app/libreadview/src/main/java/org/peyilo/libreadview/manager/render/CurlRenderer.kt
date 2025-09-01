package org.peyilo.libreadview.manager.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Scroller

interface CurlRenderer {

    fun setPageSize(width: Float, height: Float)

    fun initControllPosition(x: Float, y: Float)

    fun updateTouchPosition(curX: Float, curY: Float)

    fun render(canvas: Canvas)

    fun setPages(top: Bitmap, bottom: Bitmap)

    fun release()

    fun flipToNextPage(scroller: Scroller, animDuration: Int)

    fun flipToPrevPage(scroller: Scroller, animDuration: Int)

}