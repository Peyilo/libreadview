package org.peyilo.libreadview.turning

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.graphics.get
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.turning.render.CurlRenderer
import org.peyilo.libreadview.turning.render.IBookCurlRenderer
import org.peyilo.libreadview.turning.util.screenshot
import org.peyilo.libreadview.turning.util.screenshotInto

class IBookCurlEffect: CurlEffect() {

    override val curlRenderer: CurlRenderer = IBookCurlRenderer()

    override fun prepareAnim(initDire: PageDirection) {
        super.prepareAnim(initDire)
        curlRenderer.initControllPosition(gesture.down.x, gesture.down.y)
        val backTintColor = when (initDire) {
            PageDirection.NEXT -> {
                getAverageBackgroundColor(pageContainer.getCurPage()!!)
            }
            PageDirection.PREV -> {
                getAverageBackgroundColor(pageContainer.getPrevPage()!!)
            }
            else -> throw IllegalStateException()
        }
        setBackTintColor(backTintColor)
    }

    override fun prepareAnimForRestore(initDire: PageDirection, afterCarousel: Boolean) {
        if (!afterCarousel) {
            super.prepareAnim(initDire)
            val backTintColor = when (initDire) {
                PageDirection.NEXT -> {
                    getAverageBackgroundColor(pageContainer.getCurPage()!!)
                }
                PageDirection.PREV -> {
                    getAverageBackgroundColor(pageContainer.getPrevPage()!!)
                }
                else -> throw IllegalStateException()
            }
            setBackTintColor(backTintColor)
        } else {
            when (initDire) {
                PageDirection.NEXT -> {
                    if (pageBitmapCache.topBitmap != null) {
                        pageContainer.getPrevPage()!!.screenshotInto(pageBitmapCache.topBitmap!!)
                    } else {
                        pageBitmapCache.topBitmap = pageContainer.getPrevPage()!!.screenshot()
                    }
                    if (pageBitmapCache.bottomBitmap != null) {
                        pageContainer.getCurPage()!!.screenshotInto(pageBitmapCache.bottomBitmap!!)
                    } else {
                        pageBitmapCache.bottomBitmap = pageContainer.getCurPage()!!.screenshot()
                    }
                }
                PageDirection.PREV -> {
                    if (pageBitmapCache.topBitmap != null) {
                        pageContainer.getCurPage()!!.screenshotInto(pageBitmapCache.topBitmap!!)
                    } else {
                        pageBitmapCache.topBitmap = pageContainer.getCurPage()!!.screenshot()
                    }
                    if (pageBitmapCache.bottomBitmap != null) {
                        pageContainer.getNextPage()!!.screenshotInto(pageBitmapCache.bottomBitmap!!)
                    } else {
                        pageBitmapCache.bottomBitmap = pageContainer.getNextPage()!!.screenshot()
                    }
                }
                else -> throw IllegalStateException()
            }
            curlRenderer.setPageSize(pageContainer.width.toFloat(), pageContainer.height.toFloat())
            curlRenderer.setPages(pageBitmapCache.topBitmap!!, pageBitmapCache.bottomBitmap!!)

            val backTintColor = when (initDire) {
                PageDirection.NEXT -> {
                    getAverageBackgroundColor(pageContainer.getPrevPage()!!)
                }
                PageDirection.PREV -> {
                    getAverageBackgroundColor(pageContainer.getCurPage()!!)
                }
                else -> throw IllegalStateException()
            }
            setBackTintColor(backTintColor)
        }
    }

    // 取Bitmap的平均颜色，默认采样4x4的网格
    private fun getAverageColor(bitmap: Bitmap, sampleGridSize: Int = 4): Int {
        val width = bitmap.width
        val height = bitmap.height

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L

        // 网格间隔
        val stepX = width.toFloat() / sampleGridSize
        val stepY = height.toFloat() / sampleGridSize

        for (i in 0 until sampleGridSize) {
            for (j in 0 until sampleGridSize) {
                val x = (i * stepX).toInt().coerceAtMost(width - 1)
                val y = (j * stepY).toInt().coerceAtMost(height - 1)
                val color = bitmap[x, y]
                val alpha = Color.alpha(color)
                if (alpha > 0) {
                    rSum += Color.red(color)
                    gSum += Color.green(color)
                    bSum += Color.blue(color)
                    count++
                }
            }
        }

        return if (count > 0) {
            val r = (rSum / count).toInt()
            val g = (gSum / count).toInt()
            val b = (bSum / count).toInt()
            Color.rgb(r, g, b)
        } else {
            Color.TRANSPARENT
        }
    }

    // 获取View的背景颜色，如果是Bitmap则取平均颜色
    private fun getAverageBackgroundColor(view: View): Int {
        val bg = view.background
        return when (bg) {
            is ColorDrawable -> bg.color
            is BitmapDrawable -> getAverageColor(bg.bitmap)
            else -> Color.TRANSPARENT
        }
    }


    override fun onDragging(
        initDire: PageDirection,
        dx: Float,
        dy: Float
    ) {
        super.onDragging(initDire, dx, dy)
        curlRenderer.updateTouchPosition(gesture.cur.x, gesture.cur.y)
        pageContainer.invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            curlRenderer.updateTouchPosition(scroller.currX.toFloat(), scroller.currY.toFloat())
            pageContainer.invalidate()
            // 滑动动画结束
            if (scroller.currX == scroller.finalX && scroller.currY == scroller.finalY) {
                scroller.forceFinished(true)
                isAnimRuning = false
                curlRenderer.release()
                onAnimEnd()
            }
        }
    }

    private fun setBackTintColor(color: Int) {
        (curlRenderer as IBookCurlRenderer).setBackTintColor(color)
    }

}