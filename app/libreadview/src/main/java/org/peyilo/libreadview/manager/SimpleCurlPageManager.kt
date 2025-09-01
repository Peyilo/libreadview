package org.peyilo.libreadview.manager

import android.graphics.PointF
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.manager.CurlPageManager
import org.peyilo.libreadview.manager.render.CurlRenderer
import org.peyilo.libreadview.manager.render.SimpleCurlRenderer

class SimpleCurlPageManager: CurlPageManager() {

    override val curlRenderer: CurlRenderer = SimpleCurlRenderer()

    private var animMode = AnimMode.None

    private val cornerVertex = PointF()               // 页脚顶点

    private enum class AnimMode {
        TopRightCorner, BottomRightCorner, NextLandscape, PrevLandscape, None
    }

    override fun decideInitDire(dx: Float, dy: Float): PageDirection {
        val initDire = super.decideInitDire(dx, dy)
        animMode = when (initDire) {
            PageDirection.NEXT -> {     // 向下一页翻页时，根据本轮手势的DOWN坐标决定翻页动画的三种不同模式：右上角翻页、右下角翻页、横向翻页
                if (gesture.down.y < containerHeight * 0.4) {               // 右上角翻页
                    AnimMode.TopRightCorner
                } else if (gesture.down.y > containerHeight * 0.8) {    // 右下角翻页
                    AnimMode.BottomRightCorner
                } else {   // 横向翻页
                    AnimMode.NextLandscape
                }
            }
            PageDirection.PREV -> {     // 向上一页翻页时，只有横向翻页一种模式
                AnimMode.PrevLandscape
            }
            else -> AnimMode.None
        }
        if (animMode != AnimMode.None) {
            // 横向翻页通过touchPoint实现，因此也要设置cornerVertex
            if (gesture.down.y < containerHeight / 2) {
                cornerVertex.x = containerWidth.toFloat()
                cornerVertex.y = 0F
                curlRenderer.initControllPosition(containerWidth.toFloat(), 0F)
            } else {
                cornerVertex.x = containerWidth.toFloat()
                cornerVertex.y = containerHeight.toFloat()
                curlRenderer.initControllPosition(containerWidth.toFloat(),  containerHeight.toFloat())
            }
        }
        return initDire
    }

    /**
     * 限制TouchPoint的坐标不出界，并做一些近似处理
     */
    private fun setTouchPoint(x: Float, y: Float) {
        val tx = x
        var ty = 0F
        when (animMode) {
            AnimMode.TopRightCorner, AnimMode.BottomRightCorner -> {
                // 限制touchPoint.y不出界
                ty = y.coerceIn(0F, containerHeight.toFloat())
            }
            AnimMode.NextLandscape, AnimMode.PrevLandscape -> {
                ty = cornerVertex.y
            }
            else -> Unit
        }
        curlRenderer.updateTouchPosition(tx, ty)
    }

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        super.onDragging(initDire, dx, dy)
        setTouchPoint(gesture.cur.x, gesture.cur.y)
        pageContainer.invalidate()
    }

    override fun flipToNextPage(limited: Boolean): Boolean {
        cornerVertex.x = containerWidth.toFloat()
        cornerVertex.y = containerHeight.toFloat()
        curlRenderer.initControllPosition(cornerVertex.x, cornerVertex.y)
        curlRenderer.updateTouchPosition(cornerVertex.x, cornerVertex.y)
        return super.flipToNextPage(limited)
    }

    override fun flipToPrevPage(limited: Boolean): Boolean {
        cornerVertex.x = containerWidth.toFloat()
        cornerVertex.y = containerHeight.toFloat()
        curlRenderer.initControllPosition(cornerVertex.x, cornerVertex.y)
        curlRenderer.updateTouchPosition(0F, cornerVertex.y)
        return super.flipToPrevPage(limited)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
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