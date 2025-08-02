package org.peyilo.libreadview.manager

import org.peyilo.libreadview.PageContainer.PageDirection
import org.peyilo.libreadview.PageContainer.LayoutManager

abstract class DirectionalLayoutManager: LayoutManager() {

    /**
     * 在某次手势中，一旦滑动的距离超过一定距离（scaledTouchSlop），说明就是滑动手势，然后就会调用该函数
     * 用来决定本次滑动手势的方向，滑动手势的方向决定了哪个Page被选中
     * 一旦返回了NEXT或PREV，该函数在本轮手势中将不会再被调用
     * 如果返回了NONE，该函数就会在接下来的MOVE事件中一直被调用，直至本次手势结束会返回非NONE值为止
     */
    abstract fun decideInitDire(dx: Float, dy: Float): PageDirection

}