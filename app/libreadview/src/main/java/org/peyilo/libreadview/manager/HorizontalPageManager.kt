package org.peyilo.libreadview.manager

import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.abs

abstract class HorizontalPageManager: PageContainer.PageManager() {

    private var lastX: Float = 0F                       // 上次的触摸点的y坐标
    private var lastRealTimeDire = PageDirection.NONE   // 上次的实时移动方向

    // 设置page的初始位置
    override fun initPagePosition() {
        pageContainer.apply {
            // 将位于当前页之前的page，全部移动到屏幕外的左侧
            getAllPrevPages().forEach { page ->
                page.translationX = -width.toFloat()
            }
        }
    }

    override fun decideInitDire(dx: Float, dy: Float): PageDirection {
        return if (abs(dx) < pageContainer.flipTouchSlop) {
            PageDirection.NONE
        } else if (dx < 0) {
            PageDirection.NEXT
        } else {
            PageDirection.PREV
        }
    }

    // 每轮手势开始的时候需要清除这两个变量状态
    override fun onActionDown() {
        super.onActionDown()
        lastX = gesture.down.x
        lastRealTimeDire = PageDirection.NONE
    }

    override fun decideRealTimeDire(curX: Float, curY: Float): PageDirection {
        val dx = curX - lastX
        return if (abs(dx) >= pageContainer.flipTouchSlop) {
            lastX = curX
            if (dx < 0) {
                lastRealTimeDire = PageDirection.NEXT
                PageDirection.NEXT
            } else {
                lastRealTimeDire = PageDirection.PREV
                PageDirection.PREV
            }
        } else {
            lastRealTimeDire
        }
    }

}