package org.peyilo.libreadview.turning

import android.view.View
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

class SlideEffect: FlipOnReleaseEffect.Horizontal(), AnimatedEffect {

    companion object {
        private const val TAG = "SlideEffect"
    }

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

    override fun prepareAnimForRestore(initDire: PageDirection, afterCarousel: Boolean) {
        if (!afterCarousel) return prepareAnim(initDire)
        when(initDire) {
            PageDirection.NEXT -> {
                primaryView = pageContainer.getPrevPage()
                followedView = pageContainer.getCurPage()
            }
            PageDirection.PREV -> {
                primaryView = pageContainer.getNextPage()
                followedView = pageContainer.getCurPage()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        // 虽然translationX赋值为float类型，但是实际上显示时是以int类型进行显示的，因此这里需要转换为int类型
        // 如果不这样，两个page之间会出现很小的缝隙
        when (initDire) {
            PageDirection.NEXT -> {
                val dx = min(dx, 0F).toInt()
                primaryView!!.translationX = dx.toFloat()
                followedView!!.translationX = dx.toFloat() + pageContainer.width
            }
            PageDirection.PREV -> {
                val dx = max(dx, 0F).toInt()
                primaryView!!.translationX = dx.toFloat()
                followedView!!.translationX = dx.toFloat() - pageContainer.width
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
        val x = x.toInt().toFloat()     // 确保x为整数，避免出现缝隙
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
                onAnimEnd()
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
        onAnimEnd()
    }

    override fun onAnimEnd() {
        super.onAnimEnd()
        isAnimRuning = false
        primaryView = null
        followedView = null
        curAnimDire = PageDirection.NONE
    }

    private fun getTranslateX(position: Int): Float {
        val containerPageCount = pageContainer.getContainerPageCount()
        return when {
            containerPageCount >= 3 -> when {
                position == 0 && !pageContainer.isLastPage() -> pageContainer.width.toFloat()
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                position == 1 && pageContainer.isFirstPage() -> pageContainer.width.toFloat()
                position == 2 && !pageContainer.isFirstPage() -> -pageContainer.width.toFloat()
                else -> 0F
            }
            containerPageCount == 2 -> when {
                position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                position == 0 && pageContainer.isFirstPage() -> pageContainer.width.toFloat()
                else -> 0F
            }
            containerPageCount == 1 -> 0F
            else -> throw IllegalStateException("onAddPage: The pageContainer.itemCount is 0.")
        }
    }

    override fun onAddPage(view: View, position: Int) {
        view.translationX = getTranslateX(position)
    }

    override fun onDestroy() {
        super.onDestroy()
        primaryView = null
        followedView = null
    }
}