package org.peyilo.libreadview.turning

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

/**
 * 仿Apple iBook的Slide翻页实现.
 * 实际上是：覆盖(cover)翻页和平移(slide)翻页的结合.
 * 阴影绘制，整个底页覆盖一个阴影区域.
 */
class IBookSlideEffect: FlipOnReleaseEffect.Horizontal(), AnimatedEffect {

    private var primaryView: View? = null
    private var followedView: View? = null

    private var animDuration = 300

    private val slideRadio = 0.25F

    private val shadowPaint: Paint = Paint()

    /**
     * 控制底部page的阴影，越大，则阴影颜色越深，取值范围为0-255
     */
    private val bottomPageShadowAlpha = 80


    /**
     * curAnimDire是用来根据方向，确定primaryView、followedView分别是什么的
     */
    private var curAnimDire: PageDirection = PageDirection.NONE

    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
    }

    override fun onInitPagePosition() {
        super.onInitPagePosition()
        pageContainer.apply {
            getAllNextPages().forEach { page ->
                page.translationX = slideRadio * width.toFloat()
            }
        }
    }

    // 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
    override fun prepareAnim(initDire: PageDirection) {
        when(initDire) {
            PageDirection.NEXT -> {
                primaryView = pageContainer.getCurPage()
                followedView = pageContainer.getNextPage()
            }
            PageDirection.PREV -> {
                primaryView = pageContainer.getPrevPage()
                followedView = pageContainer.getCurPage()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun prepareAnimAfterCarousel(initDire: PageDirection) {
        when(initDire) {
            PageDirection.NEXT -> {
                primaryView = pageContainer.getPrevPage()
                followedView = pageContainer.getCurPage()
            }
            PageDirection.PREV -> {
                primaryView = pageContainer.getCurPage()
                followedView = pageContainer.getNextPage()
            }
            else -> throw IllegalStateException()
        }
    }


    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        when (initDire) {
            PageDirection.NEXT -> {
                primaryView!!.translationX = min(dx, 0F)
                followedView!!.translationX = (min(dx, 0F) + pageContainer.width) * slideRadio
            }
            PageDirection.PREV -> {
                primaryView!!.translationX = max(dx, 0F) - pageContainer.width
                followedView!!.translationX = max(dx, 0F) * slideRadio
            }
            else -> throw IllegalStateException()
        }
        pageContainer.invalidate()          // translationX的改变不会触发view的重绘，因此需要主动调用invalidate()重绘
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            primaryView!!.apply {
                translationX = scroller.currX.toFloat()
            }
            followedView!!.apply {
                translationX = (pageContainer.width + scroller.currX) * slideRadio
            }
            if (scroller.currX == scroller.finalX) {
                scroller.forceFinished(true)
                onAnimEnd()
            }
            pageContainer.invalidate()
        }
    }

    override fun startNextAnim() {
        val curX = primaryView!!.translationX.toInt()
        val dx = -(pageContainer.width + curX)
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = PageDirection.NEXT
    }

    override fun startPrevAnim() {
        val curX = primaryView!!.translationX.toInt()
        val dx = -curX
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = PageDirection.PREV
    }

    override fun startResetAnim(initDire: PageDirection) {
        val curX = primaryView!!.translationX.toInt()
        val dx = when (initDire) {
            PageDirection.NEXT -> -curX
            PageDirection.PREV -> -curX - pageContainer.width
            else -> throw IllegalStateException("initDire is PageDirection.None")
        }
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = initDire
    }

    override fun abortAnim() {
        super.abortAnim()
        scroller.forceFinished(true)
        primaryView!!.apply {
            translationX = scroller.finalX.toFloat()
        }
        followedView!!.apply {
            translationX = (pageContainer.width + scroller.finalX) * slideRadio
        }
        onAnimEnd()
    }

    override fun onAnimEnd() {
        super.onAnimEnd()
        primaryView = null
        followedView = null
        isAnimRuning = false
        curAnimDire = PageDirection.NONE
    }

    private fun getTranslateX(position: Int): Float {
        val containerPageCount = pageContainer.getContainerPageCount()
        return when {
            containerPageCount >= 3 -> when {
                position == 0 && !pageContainer.isLastPage() -> slideRadio * pageContainer.width.toFloat()
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                position == 1 && pageContainer.isFirstPage() -> slideRadio * pageContainer.width.toFloat()
                position == 2 && !pageContainer.isFirstPage() -> -pageContainer.width.toFloat()
                else -> 0F
            }
            containerPageCount == 2 -> when {
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                position == 0 && pageContainer.isFirstPage() -> slideRadio * pageContainer.width.toFloat()
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
        private const val TAG = "IBookSlideEffect"
    }

    override fun onDestroy() {
        super.onDestroy()
        primaryView = null
        followedView = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        if ((isAnimRuning || isDragging) && primaryView != null) {
            val shadowWidth = -primaryView!!.translationX
            val scale = 1 - shadowWidth / pageContainer.width
            val alpha = (bottomPageShadowAlpha * scale).toInt()
            shadowPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRect(pageContainer.width + primaryView!!.translationX, 0F,
                pageContainer.width.toFloat(), pageContainer.height.toFloat(),
                shadowPaint
            )
        }
    }
}