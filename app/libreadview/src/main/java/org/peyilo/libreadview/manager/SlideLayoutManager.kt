package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

class SlideLayoutManager: FlipOnReleaseLayoutContainer.Horizontal(), AnimatedLayoutManager {

    private var primaryView: View? = null
    private var followedView: View? = null

    private var animDuration = 200

    /**
     * 当前正在执行的滑动动画方向，依赖于curAnimDire决定followedView是哪个page (nextPage or prevPage)
     */
    private var curAnimDire: PageDirection = PageDirection.NONE

    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
    }

    override fun onInitPagePosition() {
        super.onInitPagePosition()
        pageContainer.apply {
            // 将位于当前页之后的page，全部移动到屏幕外的右侧
            getAllNextPages().forEach { page ->
                page.translationX = width.toFloat()
            }
        }
    }

    override fun prepareAnim(initDire: PageDirection) {
        when(initDire) {
            PageDirection.NEXT -> {
                primaryView = pageContainer.getCurPage()
                followedView = pageContainer.getNextPage()
            }
            PageDirection.PREV -> {
                primaryView = pageContainer.getCurPage()
                followedView = pageContainer.getPrevPage()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        when (initDire) {
            PageDirection.NEXT -> {
                primaryView!!.translationX = min(dx, 0F)
                followedView!!.translationX = min(dx, 0F) + pageContainer.width
            }
            PageDirection.PREV -> {
                primaryView!!.translationX = max(dx, 0F)
                followedView!!.translationX = max(dx, 0F) - pageContainer.width
            }
            else -> throw IllegalStateException()
        }
        pageContainer.invalidate()          // translationX的改变不会触发view的重绘，因此需要主动调用invalidate()重绘
    }

    /**
     * 控制primaryView和followedView的协同滑动
     * @param x primaryView的偏移量
     */
    private fun scrollTogether(x: Float) {
        primaryView!!.translationX = x
        // 处理followedView的滑动
        val followedTransX: Float = when (curAnimDire) {
            PageDirection.NEXT -> x + pageContainer.width
            PageDirection.PREV -> x - pageContainer.width
            else -> throw IllegalStateException("curAnimDire is PageDirection.None")
        }
        followedView!!.translationX = followedTransX
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTogether(scroller.currX.toFloat())
            // 滑动结束，做一些清理工作
            if (scroller.currX == scroller.finalX) {
                scroller.forceFinished(true)
                isAnimRuning = false
                primaryView = null
                followedView = null
                curAnimDire = PageDirection.NONE
            }
            pageContainer.invalidate()
        }
    }

    override fun startNextAnim() {
        val curX = primaryView!!.translationX.toInt()
        val dx = -(pageContainer.width + curX)
        scroller.startScroll(curX, 0, dx, 0, animDuration)   // dx大于0往左滑，小于0往右滑
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = PageDirection.NEXT
    }

    override fun startPrevAnim() {
        val curX = primaryView!!.translationX.toInt()
        val dx = pageContainer.width - curX
        scroller.startScroll(curX, 0, dx, 0, animDuration)   // dx大于0往左滑，小于0往右滑
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = PageDirection.PREV
    }

    override fun startResetAnim(initDire: PageDirection) {
        val curX = primaryView!!.translationX.toInt()
        val dx = -curX
        scroller.startScroll(curX, 0, dx, 0, animDuration)   // dx大于0往左滑，小于0往右滑
        pageContainer.invalidate()
        isAnimRuning = true
        curAnimDire = initDire
    }

    override fun abortAnim() {
        super.abortAnim()
        scroller.forceFinished(true)
        scrollTogether(scroller.finalX.toFloat())
        isAnimRuning = false
        primaryView = null
        followedView = null
        curAnimDire = PageDirection.NONE
    }

    override fun onNextCarouselLayout() {
        if (pageContainer.itemCount >= 3) {         // 在itemCount=2时，无需处理translationX；itemCount<2的时候，这个函数不会调用
            pageContainer.apply {
                getNextPage()?.translationX = width.toFloat()
            }
        }
    }

    override fun onPrevCarouselLayout() {
        pageContainer.apply {
            getPrevPage()?.translationX = -width.toFloat()  // 这里的PrevPage可能不存在的，如果存在就处理translationX，否则不处理
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        primaryView = null
        followedView = null
    }
}