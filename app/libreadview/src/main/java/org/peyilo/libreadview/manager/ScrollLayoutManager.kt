package org.peyilo.libreadview.manager

import android.view.View
import android.widget.Scroller
import org.peyilo.libreadview.PageContainer.PageDirection
import org.peyilo.libreadview.utils.LogHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 滚动翻页实现
 * TODO: 实现flipToNextPage、flipToPrevPage
 * TODO： 支持pageContainer.onFlipListener
 * TODO: 如果PageContainer中一开始没有child，之后添加新的child，由于没有触发initPagePosition，导致所有的child都叠在一起，也就是
 */
class ScrollLayoutManager: NoFlipOnReleaseLayoutManager.Vertical() {

    private var curPage: View? = null
    private lateinit var prevPages: List<View>
    private lateinit var nextPages: List<View>

    private var lastdy = 0F

    private val scroller: Scroller by lazy { Scroller(pageContainer.context) }

    private var lastScrollY = 0

    private var isScrolling = false

    companion object {
        private const val TAG = "ScrollPageManager"
    }
    
    override fun flipToNextPage(limited: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun flipToPrevPage(limited: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun onInitPagePosition() {
        needInitPagePosition = false
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

    private fun scrollBy(dy: Float) {
        var moveDis = dy
        when {
            moveDis > 0 -> {
                val topTranslationY = getTopTranslationY()
                moveDis = min(moveDis, -topTranslationY)
                curPage?.translationY += moveDis
                prevPages.forEach {
                    it.translationY += moveDis
                }
                nextPages.forEach {
                    it.translationY += moveDis
                }
            }
            moveDis < 0 -> {
                val bottomTranslationY = getBottomTranslationY()
                moveDis = max(moveDis, -bottomTranslationY)
                curPage?.translationY += moveDis
                prevPages.forEach {
                    it.translationY += moveDis
                }
                nextPages.forEach {
                    it.translationY += moveDis
                }
            }
        }

        curPage?.let {
            //  curPage有部分区域移动到屏幕之上的区域，尝试将prevPage进行relayout，放到nextPage之后
            if (curPage!!.translationY < - pageContainer.height / 2) {
                pageContainer.nextCarouselLayout()      // 该函数执行以后，page顺序就发生了改变
                refreshPages()
                if (pageContainer.itemCount >= 3) {
                    pageContainer.apply {
                        getNextPage()?.translationY = curPage!!.translationY + pageContainer.height
                    }
                }
                LogHelper.d(TAG, "onDragging: nextCarouselLayout")
            } else if (curPage!!.translationY > pageContainer.height / 2) {
                pageContainer.prevCarouselLayout()
                refreshPages()
                pageContainer.apply {
                    getPrevPage()?.translationY = curPage!!.translationY - pageContainer.height
                }
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
        // 计算惯性滚动
        if (abs(velocityY) > 0) {
            val maxFlingDis = pageContainer.height * 10
            scroller.fling(
                0, lastScrollY,
                0, velocityY.toInt(),
                0, 0,
                -maxFlingDis, maxFlingDis
            )
            pageContainer.invalidate() // 开始滚动
            isScrolling = true
            lastScrollY = curPage!!.translationY.toInt()
        }
    }

    override fun onActionDown() {
        super.onActionDown()
        // 如果处于滚动状态，结束滚动动画
        if (isScrolling) {
            scroller.forceFinished(true)
            isScrolling = false
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

            // 继续滚动，直到滚动完成
            pageContainer.invalidate()
        } else {
            isScrolling = false
        }
    }
}