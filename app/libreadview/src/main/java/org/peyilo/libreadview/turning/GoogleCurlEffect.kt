package org.peyilo.libreadview.turning

import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.turning.render.CurlRenderer
import org.peyilo.libreadview.turning.render.GoogleCurlRenderer

// 实现Moon Reader中的Google Curl翻页效果
class GoogleCurlEffect: CurlEffect() {

    override val curlRenderer: CurlRenderer = GoogleCurlRenderer()

    override fun prepareAnim(initDire: PageDirection) {
        super.prepareAnim(initDire)
        curlRenderer.initControllPosition(gesture.down.x, gesture.down.y)
        (curlRenderer as GoogleCurlRenderer).setInitDirection(initDire)
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
                onAnimEnd()
            }
        }
    }

}