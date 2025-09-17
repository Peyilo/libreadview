package org.peyilo.libreadview.turning

import android.view.View
import android.widget.Scroller
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.util.LogHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 滚动翻页实现
 */
class ScrollEffect: NoFlipOnReleaseEffect.Vertical(), AnimatedEffect {

    private var curPage: View? = null
    private var prevPages: List<View>? = null
    private var nextPages: List<View>? = null

    private var lastdy = 0F

    private val scroller: Scroller by lazy { Scroller(pageContainer.context) }

    private var lastScrollY = 0

    private var isScrolling = false
    private var isFliping = false

    /**
     * 每次滑动手势的最大翻页数量, 某种程度上会影响滑动动画的滑动速度,最大翻页数量越大,滑动速度越快
     */
    var maxPagesPerSwipe = 10

    private var animDuration = 300

    /**
     * Sets the animation duration for scrolling or flipping actions.
     * 这个函数设置的翻页动画持续时间，只对flipToNextPage、flipToPrevPage的动画有效
     *
     * @param animDuration The duration of the animation in milliseconds.
     */
    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
    }

    companion object {
        private const val TAG = "ScrollPageEffect"
    }

    override fun forceNotInLayoutOrScroll() {
        super.forceNotInLayoutOrScroll()
        // 先恢复到初始位置
        pageContainer.apply {
            for (i in 0 until getPageChildCount()) {
                val child = getPageChildAt(i)
                child.translationY = getTranslateY(i)
            }
        }
        LogHelper.d(TAG, "forceNotInLayoutOrScroll: clearPages")
        clearPages()
    }

    override fun flipToNextWithNoLimit() {
        abortAnim()
        refreshPages()
        val curY = curPage!!.translationY.toInt()
        val dy = -(pageContainer.height + curY)
        scroller.startScroll(0, curY, 0, dy, animDuration)
        pageContainer.invalidate()
        isFliping = true
        lastScrollY = curY
    }

    override fun flipToPrevWithNoLimit() {
        abortAnim()
        refreshPages()
        val curY = curPage!!.translationY.toInt()
        val dy = pageContainer.height - curY
        scroller.startScroll(0, curY, 0, dy, animDuration)
        pageContainer.invalidate()
        isFliping = true
        lastScrollY = curY
    }

    override fun abortAnim() {
        // 如果处于滚动状态，结束滚动动画，停在当前位置
        if (isScrolling) {
            scroller.forceFinished(true)
            isScrolling = false
            clearPages()
            LogHelper.d(TAG, "abortAnim - isScrolling: clearPages")
        }
        // 如果是执行翻页动画，结束滚动动画的话，就应该跳到最终位置
        if (isFliping) {
            scroller.forceFinished(true)
            isFliping = false
            val deltaY = scroller.finalY - lastScrollY
            scrollBy(deltaY.toFloat())
            clearPages()
            LogHelper.d(TAG, "abortAnim - isFliping: clearPages")
        }
    }

    override fun onInitPagePosition() {
        pageContainer.apply {
            getAllPrevPages().forEachIndexed { index, page ->
                page.translationY = -height.toFloat() * (index + 1)
            }
            // 将位于当前页之后的page，全部移动到屏幕外之下
            getAllNextPages().forEachIndexed { index, page ->
                page.translationY = height.toFloat() * (index + 1)
            }
        }
    }

    private fun refreshPages() {
        curPage = pageContainer.getCurPage()
        prevPages = pageContainer.getAllPrevPages()
        nextPages = pageContainer.getAllNextPages()
    }

    override fun prepareAnim(initDire: PageDirection) {
        if (initDire == PageDirection.NONE)
            throw IllegalStateException("prepareAnim: initDire is PageDirection.None")
        lastdy = 0F
        refreshPages()
    }

    /**
     * 滚动page
     */
    private fun scrollBy(dy: Float) {
        var moveDis = dy
        when {
            moveDis > 0 -> {
                val topTranslationY = getTopTranslationY()
                moveDis = min(moveDis, -topTranslationY)
                curPage?.translationY += moveDis
                prevPages?.forEach {
                    it.translationY += moveDis
                }
                nextPages?.forEach {
                    it.translationY += moveDis
                }
            }
            moveDis < 0 -> {
                val bottomTranslationY = getBottomTranslationY()
                moveDis = max(moveDis, -bottomTranslationY)
                curPage?.translationY += moveDis
                prevPages?.forEach {
                    it.translationY += moveDis
                }
                nextPages?.forEach {
                    it.translationY += moveDis
                }
            }
        }

        curPage?.let {
            //  curPage有部分区域移动到屏幕之上的区域，尝试将prevPage进行relayout，放到nextPage之后
            if (curPage!!.translationY < - pageContainer.height / 2) {
                nextCarouselLayout()      // 该函数执行以后，page顺序就发生了改变
                refreshPages()
                LogHelper.d(TAG, "onDragging: nextCarouselLayout")
            } else if (curPage!!.translationY > pageContainer.height / 2) {
                prevCarouselLayout()
                refreshPages()
                LogHelper.d(TAG, "onDragging: prevCarouselLayout")
            }
        }
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        if (initDire == PageDirection.NONE)
            throw IllegalStateException("onDragging: initDire is PageDirection.None")
        scrollBy(dy - lastdy)
        lastdy = dy
    }

    override fun onStartScroll(velocityX: Float, velocityY: Float) {
        super.onStartScroll(velocityX, velocityY)
        if (curPage == null) {
            LogHelper.e(TAG, "onStartScroll: the curpage is null")
            return
        }
        // 计算惯性滚动
        lastScrollY = curPage!!.translationY.toInt()
        if (abs(velocityY) > 0) {
            val maxFlingDis = pageContainer.height * maxPagesPerSwipe
            scroller.fling(
                0, lastScrollY,
                0, velocityY.toInt(),
                0, 0,
                -maxFlingDis, maxFlingDis
            )
            pageContainer.invalidate() // 开始滚动
            isScrolling = true
        }
    }

    override fun computeScroll() {
        super.computeScroll()

        // 判断滚动是否完成
        if (scroller.computeScrollOffset()) {
            // 获取当前滚动偏移量
            val deltaY = scroller.currY - lastScrollY
            lastScrollY = scroller.currY
            scrollBy( deltaY.toFloat())
            if (scroller.currY == scroller.finalY) {
                scroller.forceFinished(true)
                isScrolling = false
                isFliping = false
                clearPages()
                LogHelper.d(TAG, "computeScroll: clearPages")
            }
            // 继续滚动，直到滚动完成
            pageContainer.invalidate()
        }
    }

    /**
     * 所有的page都是不重叠的,平铺布局
     */
    private fun getTranslateY(position: Int): Float {
        val containerPageCount = pageContainer.getContainerPageCount()
        return when {
            containerPageCount >= 3 -> when {
                position == 0 && pageContainer.isFirstPage() -> 2 * pageContainer.height.toFloat()
                position == 0 && !pageContainer.isLastPage() -> pageContainer.height.toFloat()
                position == 1 && pageContainer.isLastPage() -> -pageContainer.height.toFloat()
                position == 1 && pageContainer.isFirstPage() -> pageContainer.height.toFloat()
                position == 2 && !pageContainer.isFirstPage() -> -pageContainer.height.toFloat()
                position == 2 && pageContainer.isLastPage() -> -2 * pageContainer.height.toFloat()
                else -> 0F
            }
            containerPageCount == 2 -> when {
                position == 1 && pageContainer.isLastPage() -> -pageContainer.height.toFloat()
                position == 0 && pageContainer.isFirstPage() -> pageContainer.height.toFloat()
                else -> 0F
            }
            containerPageCount == 1 -> 0F
            else -> throw IllegalStateException("onAddPage: The pageContainer.itemCount is 0.")
        }
    }

    override fun onAddPage(view: View, position: Int) {
        var dx: Float
        try {
            // getCurPage()有可能抛出异常
            val curPage = pageContainer.getCurPage()
            dx = curPage?.translationY ?: 0F
        } catch (_: Exception) {
            dx = 0F
        }
        view.translationY = getTranslateY(position) + dx
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPages()
        LogHelper.d(TAG, "onDestroy: clearPages")
    }

    private fun clearPages() {
        curPage = null
        prevPages = null
        nextPages = null
    }
}