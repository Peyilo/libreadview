package org.peyilo.libreadview.manager

import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.manager.render.CurlRenderer
import org.peyilo.libreadview.manager.render.IBookCurlRenderer

class IBookCurlPageManager: CurlPageManager() {

    override val curlRenderer: CurlRenderer = IBookCurlRenderer()

    override fun prepareAnim(initDire: PageDirection) {
        super.prepareAnim(initDire)
        curlRenderer.initControllPosition(gesture.down.x, gesture.down.y)
    }

    override fun onDragging(
        initDire: PageDirection,
        dx: Float,
        dy: Float
    ) {
        super.onDragging(initDire, dx, dy)
        curlRenderer.updateTouchPosition(gesture.cur.x, gesture.cur.y)
        pageContainer.invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            curlRenderer.updateTouchPosition(scroller.currX.toFloat(), scroller.currY.toFloat())
            pageContainer.invalidate()
            // 滑动动画结束
            if (scroller.currX == scroller.finalX && scroller.currY == scroller.finalY) {
                scroller.forceFinished(true)
                isAnimRuning = false
                curlRenderer.release()
            }
        }
    }


}