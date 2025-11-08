package org.peyilo.libreadview.turning

import android.graphics.Canvas
import android.view.View
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.turning.render.CurlRenderer
import org.peyilo.libreadview.turning.util.PageBitmapCache
import org.peyilo.libreadview.turning.util.screenshot
import org.peyilo.libreadview.turning.util.screenshotInto

abstract class CurlEffect: FlipOnReleaseEffect.Horizontal(), AnimatedEffect {

    protected val containerWidth get() = pageContainer.width      // 容器的宽度
    protected val containerHeight get() = pageContainer.height    // 容器的高度

    /**
     * 注意控制Bitmap的回收，避免出现内存泄漏
     */
    protected val pageBitmapCache = PageBitmapCache()

    protected abstract val curlRenderer: CurlRenderer

    /**
     * 动画持续时间
     */
    protected var animDuration = 1400
        private set

    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
    }

    override fun prepareAnim(initDire: AbstractPageContainer.PageDirection) {
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
        curlRenderer.setPageSize(pageContainer.width.toFloat(), pageContainer.height.toFloat())
        curlRenderer.setPages(pageBitmapCache.topBitmap!!, pageBitmapCache.bottomBitmap!!)
    }

    override fun prepareAnimForRestore(initDire: PageDirection, afterCarousel: Boolean) {
        if (!afterCarousel) return prepareAnim(initDire)
        // 创建动画所需的bitmap
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
    }

    override fun startNextAnim() {
        curlRenderer.flipToNextPage(scroller, animDuration)
        isAnimRuning = true
        pageContainer.invalidate()
    }

    override fun startPrevAnim() {
        curlRenderer.flipToPrevPage(scroller, animDuration)
        isAnimRuning = true
        pageContainer.invalidate()
    }

    override fun startResetAnim(initDire: AbstractPageContainer.PageDirection) {
        when (initDire) {
            AbstractPageContainer.PageDirection.NEXT -> {
                startPrevAnim()
            }
            AbstractPageContainer.PageDirection.PREV -> {
                startNextAnim()
            }
            else -> throw IllegalStateException("initDire is PageDirection.None")
        }
    }

    override fun abortAnim() {
        super.abortAnim()
        isAnimRuning = false
        scroller.forceFinished(true)
        curlRenderer.release()
        onAnimEnd()
    }

    override fun onNextCarouselLayout() {
        // 由于仿真翻页的动画实现，并不是依靠translationX，因此在动画结束后，
        // 还需将被拖动的View需要主动移出画面内，不像其他PageEffect会通过设置translationX达到动画的效果
        pageContainer.apply {
            getPrevPage()?.translationX = -containerWidth.toFloat()
        }
    }

    override fun onPrevCarouselLayout() {
        // 被拖动的View需要主动移出画面内，不像其他PageEffect会通过设置translationX达到动画的效果
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
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isDragging || isAnimRuning) {
            curlRenderer.render(canvas)
        }
    }

    override fun needDrawChild(): Boolean {         // 在执行动画时，不需要绘制子View
        return !(isDragging || isAnimRuning)
    }

    override fun onDestroy() {
        super.onDestroy()
        pageBitmapCache.clearBitmap()
        curlRenderer.destory()
    }
}