package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

/**
 * 覆盖翻页实现
 */
class CoverLayoutManager: CoverShadowLayoutManager(), AnimatedLayoutManager {

    private var draggedView: View? = null           // 当前滑动手势选中的page

    private var animDuration = 300

    override fun setAnimDuration(animDuration: Int) {
        this.animDuration = animDuration
    }

    // 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
    override fun prepareAnim(initDire: PageDirection) {
        draggedView = when(initDire) {
            PageDirection.NEXT -> pageContainer.getCurPage()!!
            PageDirection.PREV -> pageContainer.getPrevPage()!!
            else -> throw IllegalStateException()
        }
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        when (initDire) {
            PageDirection.NEXT -> {
                draggedView!!.translationX = min(dx, 0F)
            }
            PageDirection.PREV -> {
                draggedView!!.translationX = max(dx, 0F) - pageContainer.width
            }
            else -> throw IllegalStateException()
        }
        pageContainer.invalidate()          // translationX的改变不会触发view的重绘，因此需要主动调用invalidate()重绘
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            draggedView!!.apply {
                translationX = scroller.currX.toFloat()
            }
            if (scroller.currX == scroller.finalX) {
                scroller.forceFinished(true)
                isAnimRuning = false
                draggedView = null
            }
            pageContainer.invalidate()
        }
    }

    override fun startNextAnim() {
        val curX = draggedView!!.translationX.toInt()
        val dx = -(pageContainer.width + curX)
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
    }

    override fun startPrevAnim() {
        val curX = draggedView!!.translationX.toInt()
        val dx = -curX
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
    }

    override fun startResetAnim(initDire: PageDirection) {
        val curX = draggedView!!.translationX.toInt()
        val dx = when (initDire) {
            PageDirection.NEXT -> -curX
            PageDirection.PREV -> -curX - pageContainer.width
            else -> throw IllegalStateException("initDire is PageDirection.None")
        }
        scroller.startScroll(curX, 0, dx, 0, animDuration)
        pageContainer.invalidate()
        isAnimRuning = true
    }

    override fun abortAnim() {
        super.abortAnim()
        scroller.forceFinished(true)
        draggedView!!.apply {
            translationX = scroller.finalX.toFloat()
        }
        draggedView = null
        isAnimRuning = false
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

    override fun destroy() {
        super.destroy()
        draggedView = null
    }

    override fun getCoverShadowView(): View? = draggedView

}

