package org.peyilo.libreadview.manager

import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.annotation.IntRange
import org.peyilo.libreadview.PageContainer.Gesture
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.abs
import kotlin.math.hypot

abstract class FlipOnReleaseLayoutContainer: DirectionalLayoutManager() {

    private var _scroller: Scroller? = null
    protected val scroller: Scroller
        get() {
            if (_scroller == null) {
                _scroller = getDefaultScroller()        // 如果没设置scoller，就是用默认的
            }
            return _scroller!!
        }

    protected val gesture: Gesture = Gesture()
    private var isSwipeGesture = false                      // 此次手势是否为滑动手势,一旦当前手势移动的距离大于scaledTouchSlop，就被判为滑动手势
    private var needStartAnim = false                       // 此次手势是否需要触发滑动动画
    private var initDire = PageDirection.NONE               // 初始滑动方向，其确定了要滑动的page
    private var realTimeDire = PageDirection.NONE

    /**
     * 最小翻页时间间隔: 限制翻页速度，取值为0时，表示不限制
     */
    @IntRange(from = 0) var minPageTurnInterval = 250

    /**
     * 上次翻页动画执行的时间
     */
    private var lastAnimTimestamp: Long = 0L

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


    private fun getDefaultScroller() = Scroller(pageContainer.context, LinearInterpolator())

    protected fun setScroller(scroller: Scroller) {
        _scroller = scroller
    }

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
     * 只有当isAnimRuning为true时，abortAnim才可能会被调用
     */
    open fun abortAnim() = Unit

    /**
     * 开启翻向下一页的动画
     */
    abstract fun startNextAnim()

    /**
     * 开启翻向上一页的动画
     */
    abstract fun startPrevAnim()

    /**
     * 开启页面复位的动画：比如首先拖动page移动，但是没有松手，这时候再往回拖动，需要执行页面复位动画
     * @param initDire 最开始拖动page移动的方向
     */
    open fun startResetAnim(initDire: PageDirection) = Unit

    open fun onActionDown() = Unit
    open fun onActionUp() = Unit
    open fun onActionMove() = Unit
    open fun onActionCancel() = Unit

    /**
     * 计算实时方向
     */
    abstract fun decideRealTimeDire(curX: Float, curY: Float): PageDirection

    open fun decideEndDire(initDire: PageDirection, realTimeDire: PageDirection): PageDirection {
        return if (initDire == PageDirection.NEXT && realTimeDire == PageDirection.NEXT) {
            PageDirection.NEXT
        } else if (initDire == PageDirection.PREV && realTimeDire == PageDirection.PREV) {
            PageDirection.PREV
        } else {
            PageDirection.NONE
        }
    }

    /**
     * 手指抬起后，会自动触发翻页动画，也就是每次手势结束后，page都会对其PageContainer的边界
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val internal = System.currentTimeMillis() - lastAnimTimestamp
                // 如果还有动画正在执行、并且翻页动画间隔大于minPageTurnInterval，就结束动画
                // 如果小于最小翻页时间间隔minPageTurnInterval，就不强制结束动画
                if (internal > minPageTurnInterval && isAnimRuning) {
                    abortAnim()
                }
                onActionDown()
            }
            MotionEvent.ACTION_MOVE -> {
                val internal = System.currentTimeMillis() - lastAnimTimestamp
                // 如果还有动画正在执行、并且翻页动画间隔大于minPageTurnInterval，就结束动画
                // 如果小于最小翻页时间间隔minPageTurnInterval，就不强制结束动画
                if (internal > minPageTurnInterval) {
                    if (isAnimRuning) {
                        abortAnim()
                    }
                    val dx = gesture.dx       // 水平偏移量
                    val dy = gesture.dy       // 垂直偏移量
                    val distance = hypot(dx, dy)            // 距离
                    realTimeDire = decideRealTimeDire(event.x, event.y)
                    if (isSwipeGesture || distance > pageContainer.scaledTouchSlop) {
                        if (!isSwipeGesture) {
                            initDire = decideInitDire(dx, dy)
                            if (initDire != PageDirection.NONE) {
                                isSwipeGesture = true
                                needStartAnim = (initDire == PageDirection.NEXT && pageContainer.hasNextPage())
                                        || (initDire == PageDirection.PREV && pageContainer.hasPrevPage())
                                if (needStartAnim) prepareAnim(initDire)
                            }
                        }
                        if (needStartAnim) {
                            // 跟随手指滑动
                            isDragging = true
                            onDragging(initDire, dx, dy)
                        }
                    }
                }
                onActionMove()
            }
            MotionEvent.ACTION_UP -> {
                if (isSwipeGesture) {   // 本轮事件构成滑动手势，清除某些状态
                    if (needStartAnim) {
                        // 决定最后的翻页方向，并且播放翻页动画
                        when (decideEndDire(initDire, realTimeDire)) {
                            PageDirection.NEXT -> {
                                startNextAnim()
                                pageContainer.nextCarouselLayout()
                                onNextCarouselLayout()
                                pageContainer.onFlip(PageDirection.NEXT, pageContainer.mCurPageIndex)
                            }
                            PageDirection.PREV -> {
                                startPrevAnim()
                                pageContainer.prevCarouselLayout()
                                onPrevCarouselLayout()
                                pageContainer.onFlip(PageDirection.PREV, pageContainer.mCurPageIndex)
                            }
                            PageDirection.NONE -> {
                                startResetAnim(initDire)
                                pageContainer.onFlip(PageDirection.NONE, pageContainer.mCurPageIndex)
                            }
                        }
                        needStartAnim = false
                        lastAnimTimestamp = System.currentTimeMillis()
                    }
                    isSwipeGesture = false
                    isDragging = false
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
                onActionUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                onActionCancel()
            }
        }
        return true
    }

    private fun flipToNextWithNoLimit() {
        if (isAnimRuning) {
            abortAnim()
        }
        prepareAnim(PageDirection.NEXT)
        startNextAnim()
        pageContainer.nextCarouselLayout()
        onNextCarouselLayout()
        pageContainer.onFlip(PageDirection.NEXT, pageContainer.mCurPageIndex)
        lastAnimTimestamp = System.currentTimeMillis()
    }

    private fun flipToPrevWithNoLimit() {
        if (isAnimRuning) {
            abortAnim()
        }
        prepareAnim(PageDirection.PREV)
        startPrevAnim()
        pageContainer.prevCarouselLayout()
        onPrevCarouselLayout()
        pageContainer.onFlip(PageDirection.PREV, pageContainer.mCurPageIndex)
        lastAnimTimestamp = System.currentTimeMillis()
    }

    /**
     * 调用该函数，将会直接触发翻向下一页动画，对一定时间内的翻页次数并没有限制
     */
    override fun flipToNextPage(limited: Boolean): Boolean {
        if (!limited) {
            flipToNextWithNoLimit()
            return true
        }
        val internal =  System.currentTimeMillis() - lastAnimTimestamp
        if (internal > minPageTurnInterval) {
            flipToNextWithNoLimit()
            return true
        }
        return false
    }

    /**
     * 调用该函数，将会直接触发翻向上一页，对一定时间内的翻页次数并没有限制
     */
    override fun flipToPrevPage(limited: Boolean): Boolean {
        if (!limited) {
            flipToPrevWithNoLimit()
            return true
        }
        val internal =  System.currentTimeMillis() - lastAnimTimestamp
        if (internal > minPageTurnInterval) {
            flipToPrevWithNoLimit()
            return true
        }
        return false
    }

    /**
     * 需要在这个函数里处理新添加的Page的位置
     * 调用顺序：
     *      startNextAnim() -> pageContainer.nextCarouselLayout() -> onNextCarouselLayout() -> onFlip
     *  所以在onNextCarouselLayout()中获取的page顺序已经更新过位置
     */
    abstract fun onNextCarouselLayout()

    /**
     * 需要在这个函数里处理新添加的Page的位置
     * 调用顺序：
     *      ACTION_UP -> startPrevAnim() -> pageContainer.prevCarouselLayout() -> onPrevCarouselLayout() -> onFlip
     *  所以在onNextCarouselLayout()中获取的page顺序已经更新过位置
     */
    abstract fun onPrevCarouselLayout()


    abstract class Horizontal: FlipOnReleaseLayoutContainer() {

        private var lastX: Float = 0F                       // 上次的触摸点的y坐标
        private var lastRealTimeDire = PageDirection.NONE   // 上次的实时移动方向

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

    abstract class Vertical: FlipOnReleaseLayoutContainer() {

        private var lastY: Float = 0F                       // 上次的触摸点的y坐标
        private var lastRealTimeDire = PageDirection.NONE   // 上次的实时移动方向

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

        // 每轮手势开始的时候需要清除这两个变量状态
        override fun onActionDown() {
            super.onActionDown()
            lastY = gesture.down.y
            lastRealTimeDire = PageDirection.NONE
        }

        override fun decideRealTimeDire(curX: Float, curY: Float): PageDirection {
            val dy = curY - lastY
            return if (abs(dy) >= pageContainer.flipTouchSlop) {
                lastY = curY
                if (dy < 0) {
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
}