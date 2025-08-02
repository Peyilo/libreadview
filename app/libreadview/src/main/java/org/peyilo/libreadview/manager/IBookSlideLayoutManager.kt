package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

/**
 * 仿Apple iBook的Slide翻页实现
 * 实际上是：覆盖(cover)翻页和平移(slide)翻页的结合
 */
class IBookSlideLayoutManager: CoverShadowLayoutManager(), AnimatedLayoutManager {

    private var primaryView: View? = null
    private var followedView: View? = null

    private var animDuration = 300

    private val slideRadio = 0.25F

    /**
     * curAnimDire是用来根据方向，确定primaryView、followedView分别是什么的
     */
    private var curAnimDire: PageDirection = PageDirection.NONE

    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
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
        primaryView = null
        followedView = null
        isAnimRuning = false
        curAnimDire = PageDirection.NONE
    }

    override fun onNextCarouselLayout() {
        if (pageContainer.itemCount >= 3) {         // 在itemCount=2时，无需处理translationX；itemCount<2的时候，这个函数不会调用
            pageContainer.apply {
                getNextPage()?.translationX = 0F
            }
        }
    }

    override fun onPrevCarouselLayout() {
        pageContainer.apply {
            getPrevPage()?.translationX = -width.toFloat()  // 这里的PrevPage可能不存在的，如果存在就处理translationX，否则不处理
        }
    }

    override fun onAddPage(view: View, position: Int) {
        super.onAddPage(view, position)
        when {
            pageContainer.itemCount >= 3 -> {
                if (position == 2 && pageContainer.curPageIndex != 1) {
                    view.translationX = -pageContainer.width.toFloat()
                } else if (position == 1 && pageContainer.curPageIndex == pageContainer.itemCount)
                    view.translationX = -pageContainer.width.toFloat()
                else {
                    view.translationX = 0F
                }
            }
            pageContainer.itemCount == 2 -> {
                if (position == 1 && pageContainer.curPageIndex == 2) {
                    view.translationX = -pageContainer.width.toFloat()
                } else {
                    view.translationX = 0F
                }
            }
            pageContainer.itemCount == 1 -> {
                view.translationX = 0F
            }
        }
    }


    override fun destroy() {
        super.destroy()
        primaryView = null
        followedView = null
    }

    override fun getCoverShadowView(): View? = primaryView

}