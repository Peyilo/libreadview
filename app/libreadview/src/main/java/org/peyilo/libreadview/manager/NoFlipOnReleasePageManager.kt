package org.peyilo.libreadview.manager

import android.view.MotionEvent
import org.peyilo.libreadview.PageContainer.Gesture
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.abs
import kotlin.math.hypot

abstract class NoFlipOnReleasePageManager: DirectionalPageManager() {

    protected val gesture: Gesture = Gesture()

    private var isSwipeGesture = false                      // 此次手势是否为滑动手势,一旦当前手势移动的距离大于scaledTouchSlop，就被判为滑动手势
    private var needStartAnim = false                       // 此次手势是否需要触发滑动动画
    private var initDire = PageDirection.NONE               // 初始滑动方向，其确定了要滑动的page

    /**
     * 动画播放状态（不包含拖动状态的动画），若为true则表示当前动画还没有播放完
     * 这个状态需要子类维护，只有当isAnimRuning为true时，abortAnim才可能会被调用
     */
    protected var isAnimRuning = false

    /**
     * 表示当前某个page正在处于被拖动中
     */
    protected var isDragging = false
        private set

    /**
     * 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
     */
    abstract fun prepareAnim(initDire: PageDirection)

    /**
     * 拖动某个view滑动
     * dx: 当前点到落点的水平偏移量
     * dy: 当前点到落点的垂直偏移量
     */
    open fun onDragging(initDire: PageDirection, dx: Float, dy: Float) = Unit

    /**
     * 手指抬起后，不会自动触发翻页动画，也就是每次手势结束后，page都并不会对其PageContainer的边界
     * 主要用于实现类似于滚动翻页的效果
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                            needStartAnim = initDire != PageDirection.NONE
                            if (needStartAnim) prepareAnim(initDire)
                        }
                    }
                    if (needStartAnim) {
                        // 跟随手指滑动
                        isDragging = true
                        onDragging(initDire, dx, dy)
                    }
                }
                onActionMove()
            }
            MotionEvent.ACTION_UP -> {
                if (isSwipeGesture) {   // 本轮事件构成滑动手势，清除某些状态
                    if (needStartAnim) {
                        needStartAnim = false
                    }
                    isSwipeGesture = false
                    isDragging = false
                    initDire = PageDirection.NONE
                }
                onActionUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                onActionCancel()
            }
        }
        return true
    }

    open fun onActionDown() = Unit
    open fun onActionUp() = Unit
    open fun onActionMove() = Unit
    open fun onActionCancel() = Unit

    abstract class Horizontal: NoFlipOnReleasePageManager() {

        // 设置page的初始位置
        override fun initPagePosition() {
            super.initPagePosition()
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

    abstract class Vertical: NoFlipOnReleasePageManager() {

        // 设置page的初始位置
        override fun initPagePosition() {
            super.initPagePosition()
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