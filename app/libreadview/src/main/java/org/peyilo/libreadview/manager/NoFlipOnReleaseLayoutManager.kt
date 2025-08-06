package org.peyilo.libreadview.manager

import android.view.MotionEvent
import android.view.VelocityTracker
import org.peyilo.libreadview.AbstractPageContainer.Gesture
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import kotlin.math.abs
import kotlin.math.hypot

abstract class NoFlipOnReleaseLayoutManager: DirectionalLayoutManager() {

    protected val gesture: Gesture = Gesture()

    private var isSwipeGesture = false                      // 此次手势是否为滑动手势,一旦当前手势移动的距离大于scaledTouchSlop，就被判为滑动手势
    private var needStartAnim = false                       // 此次手势是否需要触发滑动动画
    private var initDire = PageDirection.NONE               // 初始滑动方向，其确定了要滑动的page

    private var velocityTracker: VelocityTracker? = null


    /**
     * 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
     */
    abstract fun prepareAnim(initDire: PageDirection)

    override fun forceNotInLayoutOrScroll() = Unit

    /**
     * 拖动某个view滑动
     * dx: 当前点到落点的水平偏移量
     * dy: 当前点到落点的垂直偏移量
     */
    open fun onDragging(initDire: PageDirection, dx: Float, dy: Float) = Unit

    /**
     * 遍历pageContainer中的全部child, 返回其translationY的最小值.
     * 返回值一定小于等于0
     */
    protected fun getTopTranslationY(): Float {
        var topTranslationY = 0F
        for (i in 0 until pageContainer.childCount) {
            val page = pageContainer.getChildAt(i)
            if (page.translationY < topTranslationY) {
                topTranslationY = page.translationY
            }
        }
        if (topTranslationY > 0) {
            throw IllegalStateException("getTopTranslationY: topTranslationY is $topTranslationY")
        }
        return topTranslationY
    }

    /**
     * 遍历pageContainer中的全部child，返回其translationY的最大值.
     * 返回值一定大于等于0
     */
    protected fun getBottomTranslationY(): Float {
        var bottomTranslationY = 0F
        for (i in 0 until pageContainer.childCount) {
            val page = pageContainer.getChildAt(i)
            if (page.translationY > bottomTranslationY) {
                bottomTranslationY = page.translationY
            }
        }
        if (bottomTranslationY < 0) {
            throw IllegalStateException("getBottomTranslationY: bottomTranslationY is $bottomTranslationY")
        }
        return bottomTranslationY
    }

    /**
     * 是否可以滑动由child的translationY的最大值决定
     */
    protected fun canMoveDown() = getBottomTranslationY() != 0F

    /**
     * 是否可以滑动由child的translationY的最小值决定
     */
    protected fun canMoveUp() = getTopTranslationY() != 0F

    /**
     * 手指抬起后，不会自动触发翻页动画，也就是每次手势结束后，page都并不会对其PageContainer的边界
     * 主要用于实现类似于滚动翻页的效果
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 初始化 VelocityTracker 实例
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }

        // 将事件加入 VelocityTracker
        velocityTracker?.addMovement(event)

        gesture.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 当手指按下时清空之前的事件
                velocityTracker?.clear()
                onActionDown()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = gesture.dx       // 水平偏移量
                val dy = gesture.dy       // 垂直偏移量
                val distance = hypot(dx, dy)            // 距离
                if (isSwipeGesture || distance > pageContainer.scaledTouchSlop) {
                    if (!isSwipeGesture) {
                        initDire = decideInitDire(dx, dy)
                        if (initDire != PageDirection.NONE) {
                            isSwipeGesture = true
                            needStartAnim = (initDire == PageDirection.NEXT && canMoveDown())
                                    || (initDire == PageDirection.PREV && canMoveUp())
                            if (needStartAnim) prepareAnim(initDire)
                        }
                    }
                    if (needStartAnim) {
                        // 跟随手指滑动
                        onDragging(initDire, dx, dy)
                    }
                }
                onActionMove()
            }
            MotionEvent.ACTION_UP -> {
                // 计算速度
                velocityTracker?.computeCurrentVelocity(1000) // 1000 表示速度单位是像素/秒
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f

                if (isSwipeGesture) {   // 本轮事件构成滑动手势，清除某些状态
                    if (needStartAnim) {
                        onStartScroll(velocityX, velocityY)
                        needStartAnim = false
                    }
                    isSwipeGesture = false
                    initDire = PageDirection.NONE
                } else {
                    // 触发点击事件回调，点击事件回调有两种：OnClickRegionListener和OnClickListener
                    // OnClickRegionListener优先级比OnClickListener高，只有当OnClickRegionListener.onClickRegion返回false时
                    // OnClickListener.onClick才会执行
                    pageContainer.apply {
                        val handled = mOnClickRegionListener?.let { listener ->
                            val xPercent = gesture.down.x / width * 100
                            val yPercent = gesture.up.y / height * 100
                            listener.onClickRegion(xPercent.toInt(), yPercent.toInt())
                        } ?: false

                        if (!handled) performClick()
                    }
                }
                onActionUp(velocityX, velocityY)
            }
            MotionEvent.ACTION_CANCEL -> {
                onActionCancel()
            }
        }
        return true
    }

    open fun onActionDown() = Unit
    open fun onActionUp(velocityX: Float, velocityY: Float) = Unit
    open fun onActionMove() = Unit
    open fun onActionCancel() = Unit

    open fun onStartScroll(velocityX: Float, velocityY: Float) = Unit

    abstract class Horizontal: NoFlipOnReleaseLayoutManager() {

        // 设置page的初始位置
        override fun onInitPagePosition() {
            super.onInitPagePosition()
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

    }

    abstract class Vertical: NoFlipOnReleaseLayoutManager() {

        // 设置page的初始位置
        override fun onInitPagePosition() {
            super.onInitPagePosition()
            pageContainer.apply {
                // 将位于当前页之前的page，全部移动到屏幕外之上
                getAllPrevPages().forEach { page ->
                    page.translationY = -height.toFloat()
                }
            }
        }

        override fun decideInitDire(dx: Float, dy: Float): PageDirection {
            return if (abs(dy) < pageContainer.flipTouchSlop) {
                PageDirection.NONE
            } else if (dy < 0) {
                PageDirection.NEXT
            } else {
                PageDirection.PREV
            }
        }

    }

}