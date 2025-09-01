package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.PageGLSurfaceView
import org.peyilo.libreadview.manager.render.GlslPageRenderer
import org.peyilo.libreadview.manager.util.PageBitmapCache
import org.peyilo.libreadview.manager.util.screenshot
import org.peyilo.libreadview.manager.util.screenshotInto

class GLSLCurlPageManager: FlipOnReleaseLayoutManager.Horizontal(), AnimatedLayoutManager {

    private val pageBitmapCache = PageBitmapCache()

    private val renderer by lazy { GlslPageRenderer(glView) }

    override fun prepareAnim(initDire: PageDirection) {
        // 创建动画所需的bitmap
        when (initDire) {
            PageDirection.NEXT -> {
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
            PageDirection.PREV -> {
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
            else -> throw IllegalStateException()
        }
        enbleGLView()
        renderer.setDownPos(gesture.down.x, gesture.down.y)
        renderer.setTouchPos(gesture.cur.x, gesture.cur.y)
        renderer.setBitmaps(pageBitmapCache.topBitmap!!, pageBitmapCache.bottomBitmap!!)
    }

    override fun createRenderer(): PageGLSurfaceView.PageRenderer? = renderer

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        pageContainer.invalidate()
        renderer.setTouchPos(gesture.cur.x, gesture.cur.y)
    }

    override fun onActionUp() {
        super.onActionUp()
        disableGLView()
    }

    override fun startNextAnim() {}

    override fun startPrevAnim() {}

    override fun setAnimDuration(animDuration: Int) {}

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
    }

    companion object {
        private const val TAG = "GLSLCurlPageManager"
    }
}