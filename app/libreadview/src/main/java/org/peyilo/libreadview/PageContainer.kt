package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.annotation.IntRange
import kotlin.math.hypot
import kotlin.math.max

/**
 * Usage:
 * > val pageContainer: PageContainer = findViewById(R.id.pageContainerTop)
 * > pageContainer.pageManager = CoverPageManager()
 * > pageContainer.adapter = MyAdapter()
 *
 * TODO: 实现动态添加item，为page的动态添加、删除、修改提供支持
 * TODO: 测试100种类型以上page时的回收复用表现
 * TODO: 对于水平翻页的PageManager，考虑支持向上或向下的手势，以实现类似于起点阅读、番茄小说类似的书签、段评功能
 * TODO：横屏、竖屏状态改变时，需要保存状态、并恢复状态
 */
open class PageContainer(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): ViewGroup(context, attrs, defStyleAttr, defStyleRes) {

    /**
     * 区分点击和滑动的界限，默认为24
     */
    var scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * 触发翻页的最小滑动距离
     */
    var flipTouchSlop = 40

    // TODO: 处理这些构造函数
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    /**
     * 这么设计，是因为需要对外提供pageContainer.pageManager = CoverPageManager()的简单操作
     * 同时，保证PageContainer内部获取pageManager时不必总是pageManager!!
     */
    private var _pageManager: PageManager? = null
    var pageManager: PageManager
        set(value) {
            _pageManager?.let {
                pageCache.getAllPages().forEach { page ->
                    _pageManager!!.onResetPage(page)        // 在PageManager移除之前，对所有的page状态进行复位
                }
                _pageManager!!.destroy()
            }
            _pageManager = value        // 清除pageManager中包含的PageContainer引用
            value.setPageContainer(this)
        }
        get() = _pageManager
            ?: throw IllegalStateException("PageManager is not initialized. Did you forget to set it?")

    private var _adapter: Adapter<out ViewHolder>? = null
    var adapter: Adapter<out ViewHolder>
        get() = _adapter ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) {
            _adapter = value
            populateViews()
        }
    @Suppress("UNCHECKED_CAST")
    private val viewHolderAdapter: Adapter<ViewHolder> get() = (adapter as Adapter<ViewHolder>)

    val itemCount: Int get() = _adapter?.getItemCount() ?: 0

    /**
     * 根据点击区域监听点击事件
     */
    private var onClickRegionListener: OnClickRegionListener? = null

    /**
     * 监听翻页事件，在翻页完成时调用
     */
    private var onFlipListener: OnFlipListener? = null

    /**
     * 表示当前页码，从1开始
     */
    @IntRange(from = 1)
    var curPageIndex = 1
        private set


    private val _pageCache: PageCache<out ViewHolder> by lazy { PageCache(this) }
    @Suppress("UNCHECKED_CAST")
    private val pageCache: PageCache<ViewHolder> get() = _pageCache as PageCache<ViewHolder>

    private val maxAttachedPage = 3             // child view最大数量

    /**
     * 最小翻页时间间隔: 限制翻页速度，取值为0时，表示不限制
     */
    @IntRange(from = 0) var minPageTurnInterval = 250

    /**
     * 上次翻页动画执行的时间
     */
    private var lastAnimTimestamp: Long = 0L

    /**
     * 初始化页码：设置一个标记位curPageIndex，在adapter设置之后决定显示页码
     * 注意，这个函数仅提供初始化页码功能，不能用于任意页码跳转。
     * 该函数建议在设置Adapter之前调用，这样消耗少一点。
     */
    fun initPageIndex(@IntRange(from = 1) pageIndex: Int) {
        curPageIndex = pageIndex
        if (_adapter != null) {
            populateViews()
        }
    }

    companion object {
        /**
         * 给定当前页 index、展示页数 MAX_PAGES、总页数 numPages，
         * 返回一个以当前页为中心、但页码范围在 [1, numPages] 之间的页码列表。
         */
        fun getPageRange(index: Int, maxPages: Int, numPages: Int): List<Int> {
            val half = maxPages / 2
            var start = index - half
            var end = start + maxPages - 1

            // 调整 start 和 end 使其在合法范围内
            if (start < 1) {
                start = 1
                end = minOf(maxPages, numPages)
            } else if (end > numPages) {
                end = numPages
                start = maxOf(1, end - maxPages + 1)
            }

            return (start..end).toList()
        }

    }

    /**
     * 设置adapter的时候，初始化Child View
     */
    private fun populateViews() {
        removeAllViews()
        _adapter ?: return
        pageCache.setAdapter(adapter)
        val count = itemCount
        // 约束curPageIndex在有效取值范围内, 如果count=0，将curPageIndex也约束到1
        curPageIndex = if (count == 0) 1 else curPageIndex.coerceIn(1, count)
        val pageRange = getPageRange(curPageIndex, maxAttachedPage, count)
        pageRange.reversed().forEach { pageIndex ->
            val position = pageIndex - 1            // 页码转position
            val holder = pageCache.getViewHolder(position)
            viewHolderAdapter.onBindViewHolder(holder, position)
            addView(holder.itemView)
            pageCache.attach(holder)
        }
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _pageManager?.let {
            _pageManager!!.destroy()
            _pageManager = null
        }
        pageCache.destroy()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount > 3) {
            throw IllegalStateException("only support childCount <= 3")
        }

        // Step 1: 测量自身，尽可能的大
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)

        // Step 2: 测量子View，每个子View都和其父View一样大
        // 父容器的内边距
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom

        // 可用空间（扣除 padding）
        val availableWidth = widthSize - paddingLeft - paddingRight
        val availableHeight = heightSize - paddingTop - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                var childWidth = availableWidth
                var childHeight = availableHeight
                // 子 View 的测量尺寸 = 可用空间 - margin
                val lp = child.layoutParams
                if (lp is MarginLayoutParams) {
                    childWidth -= lp.leftMargin + lp.rightMargin
                    childHeight -= lp.topMargin + lp.bottomMargin
                }
                // 防止margin设置得过大，出现负数的width、height
                childWidth = max(0, childWidth)
                childHeight = max(0, childHeight)
                // 开始测量子View
                val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                child.measure(childWidthSpec, childHeightSpec)
            }
        }
    }

    /**
     * 为margin提供支持: xml解析LayoutParams
     */
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentLeft = paddingLeft
        val parentTop = paddingTop
        val parentRight = r - l - paddingRight
        val parentBottom = b - t - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                var left = parentLeft
                var top = parentTop
                var right = parentRight
                var bottom = parentBottom
                val lp = child.layoutParams
                // 处理margin
                if (lp is MarginLayoutParams) {
                    left += lp.leftMargin
                    top += lp.topMargin
                    right -= lp.rightMargin
                    bottom -= lp.bottomMargin
                }
                child.layout(left, top, right, bottom)
            }
        }
        if (pageManager.needPagePositionInit) {
            pageManager.initPagePosition()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return pageManager.onTouchEvent(event)
    }

    open fun hasPrevPage(): Boolean = curPageIndex > 1

    open fun hasNextPage(): Boolean = curPageIndex < viewHolderAdapter.getItemCount()

    /**
     * 翻向下一页
     * @return 是否翻页成功
     */
    fun flipToNextPage(limited: Boolean = true): Boolean {
        if (hasNextPage()) {
            if (!limited) {
                pageManager.flipToNextPage()
                return true
            }
            val internal =  System.currentTimeMillis() - lastAnimTimestamp
            if (internal > minPageTurnInterval) {
                pageManager.flipToNextPage()
                return true
            }
        }
        return false
    }

    /**
     * 翻向上一页
     * @return 是否翻页成功
     */
    fun flipToPrevPage(limited: Boolean = true): Boolean {
        if (hasPrevPage()) {
            if (!limited) {
                pageManager.flipToPrevPage()
                return true
            }
            val internal =  System.currentTimeMillis() - lastAnimTimestamp
            if (internal > minPageTurnInterval) {
                pageManager.flipToPrevPage()
                return true
            }
        }
        return false
    }

    abstract class PageManager {

        /**
         * 注意及时清除PageManager中的pageContainer引用，避免内存泄露
         */
        private var _pageContainer: PageContainer? = null
        protected val pageContainer: PageContainer
            get() = _pageContainer ?: throw IllegalStateException("PageContainer is not initialized. Did you forget to call setPageContainer()?")

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

        var needPagePositionInit = true
            protected set

        internal fun setPageContainer(pageContainer: PageContainer) {
            _pageContainer = pageContainer
        }

        open fun destroy() {
            _pageContainer?.let {
                _pageContainer = null
            }
        }

        private fun getDefaultScroller() = Scroller(pageContainer.context, LinearInterpolator())

        protected fun setScroller(scroller: Scroller) {
            _scroller = scroller
        }

        /**
         * 设置page的初始位置
         */
        abstract fun initPagePosition()

        /**
         * 在某次手势中，一旦滑动的距离超过一定距离（scaledTouchSlop），说明就是滑动手势，然后就会调用该函数
         * 用来决定本次滑动手势的方向，滑动手势的方向决定了哪个Page被选中
         * 一旦返回了NEXT或PREV，该函数在本轮手势中将不会再被调用
         * 如果返回了NONE，该函数就会在接下来的MOVE事件中一直被调用，直至本次手势结束会返回非NONE值为止
         */
        abstract fun decideInitDire(dx: Float, dy: Float): PageDirection

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

        fun onTouchEvent(event: MotionEvent): Boolean {
            gesture.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val internal = System.currentTimeMillis() - pageContainer.lastAnimTimestamp
                    // 如果还有动画正在执行、并且翻页动画间隔大于minPageTurnInterval，就结束动画
                    // 如果小于最小翻页时间间隔minPageTurnInterval，就不强制结束动画
                    if (internal > pageContainer.minPageTurnInterval && isAnimRuning) {
                        abortAnim()
                    }
                    onActionDown()
                }
                MotionEvent.ACTION_MOVE -> {
                    val internal = System.currentTimeMillis() - pageContainer.lastAnimTimestamp
                    // 如果还有动画正在执行、并且翻页动画间隔大于minPageTurnInterval，就结束动画
                    // 如果小于最小翻页时间间隔minPageTurnInterval，就不强制结束动画
                    if (internal > pageContainer.minPageTurnInterval) {
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
                                    pageContainer.onFlipListener?.onFlip(PageDirection.NEXT, pageContainer.curPageIndex)
                                }
                                PageDirection.PREV -> {
                                    startPrevAnim()
                                    pageContainer.prevCarouselLayout()
                                    onPrevCarouselLayout()
                                    pageContainer.onFlipListener?.onFlip(PageDirection.NEXT, pageContainer.curPageIndex)
                                }
                                PageDirection.NONE -> {
                                    startResetAnim(initDire)
                                    pageContainer.onFlipListener?.onFlip(PageDirection.NONE, pageContainer.curPageIndex)
                                }
                            }
                            needStartAnim = false
                            pageContainer.lastAnimTimestamp = System.currentTimeMillis()
                        }
                        isSwipeGesture = false
                        isDragging = false
                        initDire = PageDirection.NONE
                    } else {
                        // 触发点击事件回调，点击事件回调有两种：OnClickRegionListener和OnClickListener
                        // OnClickRegionListener优先级比OnClickListener高，只有当OnClickRegionListener.onClickRegion返回false时
                        // OnClickListener.onClick才会执行
                        pageContainer.apply {
                            val handled = onClickRegionListener?.let { listener ->
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

        /**
         * 这个函数会在PageContainer.computeScroll()中被调用，可以用来获取Scroller的偏移，实现平滑的动画
         */
        open fun computeScroll() = Unit

        /**
         * 需要在这个函数里处理新添加的Page的位置
         * 调用顺序：
         *      startNextAnim() -> pageContainer.nextCarouselLayout() -> onNextCarouselLayout()
         *  所以在onNextCarouselLayout()中获取的page顺序已经更新过位置
         */
        abstract fun onNextCarouselLayout()

        /**
         * 需要在这个函数里处理新添加的Page的位置
         * 调用顺序：
         *      startPrevAnim() -> pageContainer.prevCarouselLayout() -> onPrevCarouselLayout()
         *  所以在onNextCarouselLayout()中获取的page顺序已经更新过位置
         */
        abstract fun onPrevCarouselLayout()

        /**
         * 调用该函数，将会直接触发翻向下一页动画
         */
        open fun flipToNextPage() {
            if (isAnimRuning) {
                abortAnim()
            }
            prepareAnim(PageDirection.NEXT)
            startNextAnim()
            pageContainer.nextCarouselLayout()
            onNextCarouselLayout()
            pageContainer.onFlipListener?.onFlip(PageDirection.NEXT, pageContainer.curPageIndex)
            pageContainer.lastAnimTimestamp = System.currentTimeMillis()
        }

        /**
         * 调用该函数，将会直接触发翻向上一页
         */
        open fun flipToPrevPage() {
            if (isAnimRuning) {
                abortAnim()
            }
            prepareAnim(PageDirection.PREV)
            startPrevAnim()
            pageContainer.prevCarouselLayout()
            onPrevCarouselLayout()
            pageContainer.onFlipListener?.onFlip(PageDirection.NEXT, pageContainer.curPageIndex)
            pageContainer.lastAnimTimestamp = System.currentTimeMillis()
        }

        open fun dispatchDraw(canvas: Canvas) = Unit

        /**
         * TODO: 失败的设计，不应该让各个PageManager互相影响
         * 当PageManager改变时，会调用这个函数，PageManager的实现类需要保证在该函数内恢复所有Page的初始状态，
         * 以免影响其他的PageManager的正常工作
         */
        open fun onResetPage(view: View) = Unit

    }

    enum class PageDirection {
        PREV, NEXT, NONE
    }

    class Gesture {
        val cur = PointF()
        val down = PointF()
        val up = PointF()

        val dx get() = cur.x - down.x               // 水平偏移量
        val dy get() = cur.y - down.y               // 垂直偏移量

        fun onTouchEvent(event: MotionEvent) {
            cur.set(event.x, event.y)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    down.set(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> {
                    up.set(event.x, event.y)
                }
            }
        }
    }

    class PageCache<VH: ViewHolder>(
        viewGroup: ViewGroup
    ) {
        private var _viewGroup: ViewGroup? = viewGroup
        private var _adapter: Adapter<*>? = null

        @Suppress("UNCHECKED_CAST")
        private val adapter: Adapter<ViewHolder> get() = (_adapter!! as Adapter<ViewHolder>)
        private val viewGroup: ViewGroup get() = _viewGroup ?: throw IllegalStateException("viewGroup is already clear, are you calling PageCache.destroy?")

        @IntRange(from = 0)
        var maxCachedPage = 5

        private val attachedPages = mutableMapOf<View, ViewHolder>()
        private val cachedPages = mutableListOf<ViewHolder>()

        companion object {
            private const val TAG = "PageContainer.PageCache"
        }

        fun setAdapter(adapter: Adapter<out VH>) {
            _adapter?.let {
                clear()
            }
            _adapter = adapter
        }

        /**
         * 根据指定的position获取一个与position对应类型相同的ViewHolder进行复用
         * 如果缓存中有这个类型的ViewHolder，就进行复用；如果没有，就重新创建一个
         */
        fun getViewHolder(@IntRange(from = 0) position: Int): ViewHolder {
            val viewType = adapter.getItemViewType(position)

            // 尝试从缓存中查找匹配的 ViewHolder
            var cacheIndex = -1
            var viewHolder: ViewHolder? = null
            for ((index, holder) in cachedPages.withIndex()) {
                if (holder.viewType == viewType) {
                    viewHolder = holder
                    cacheIndex = index
                    break  // 找到了就退出循环
                }
            }
            if (cacheIndex != -1) {                 // 命中缓存
                cachedPages.remove(viewHolder)
                Log.d(TAG, "getViewHolder: use cached page")
            }

            // 如果没有找到，则创建新的
            if (viewHolder == null) {
                viewHolder = adapter.onCreateViewHolder(viewGroup, viewType)
                viewHolder.viewType = viewType
                Log.d(TAG, "getViewHolder: create new page")
            }

            return viewHolder
        }

        /**
         * 从attachedPages中回收
         */
        fun recycle(view: View) {
            if (view !in attachedPages) {
                throw IllegalStateException("make ture that the view is contained in attachedPages!")
            }
            cachedPages.add(attachedPages[view]!!)
            attachedPages.remove(view)
            // 如果cachedPages超过maxCachedPage，就应该按照LRU的规则清理
            if (cachedPages.size > maxCachedPage) {
                val removed = cachedPages.removeAt(0) // 移除最久未用的
                removed.onRecycled()
                Log.d(TAG, "recycle: remove cached page")
            }
        }

        /**
         * 添加attachedPage
         */
        fun attach(viewHolder: ViewHolder) {
            attachedPages[viewHolder.itemView] = viewHolder
        }

        fun destroy() {
            clear()
            _adapter = null
            _viewGroup = null
        }

        fun clear() {
            attachedPages.clear()
            cachedPages.clear()
        }

        fun getAttachedPage(view: View): ViewHolder {
            if (view !in attachedPages) {
                throw IllegalStateException("make ture that the view is contained in attachedPages!")
            }
            return attachedPages[view]!!
        }

        fun getAttachedPageType(view: View): Int = getAttachedPage(view).viewType

        fun getAllPages(): List<View> {
            val res = mutableListOf<View>()
            cachedPages.forEach {
                res.add(it.itemView)
            }
            attachedPages.forEach {
                res.add(it.key)
            }
            return res
        }
    }

    abstract class ViewHolder(val itemView: View) {
        internal var viewType = 0

        /**
         * ViewHolder被清除的时候，会调用这个函数，可以在这里做一下清除工作
         */
        open fun onRecycled() = Unit
    }

    abstract class Adapter<VH : ViewHolder> {
        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, @IntRange(from = 0) position: Int)
        abstract fun getItemCount(): Int
        open fun getItemViewType(@IntRange(from = 0) position: Int) = 0
    }

    override fun computeScroll() {
        pageManager.computeScroll()
    }

    /**
     * 调整child的先后关系：按照下一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    private fun nextCarouselLayout() {
        val isFirst = isFirstPage()
        curPageIndex++
        if (isFirst || isLastPage()) return

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getPrevPage()!!         // 移除最顶层的prevPage，插入到最底层，即nextPage下面
        removeView(temp)
        val nextPageIndex = curPageIndex + 1
        if (nextPageIndex <= viewHolderAdapter.getItemCount()) {
            if (pageCache.getAttachedPageType(temp) == viewHolderAdapter.getItemViewType(nextPageIndex - 1)) {
                addView(temp, 0)
                viewHolderAdapter.onBindViewHolder(pageCache.getAttachedPage(temp), nextPageIndex - 1)
            } else {
                // viewtype不同，得从缓存中获取，或者创建新的
                pageCache.recycle(temp)
                val holder = pageCache.getViewHolder(nextPageIndex - 1)
                viewHolderAdapter.onBindViewHolder(holder, nextPageIndex - 1)
                addView(holder.itemView, 0)
                pageCache.attach(holder)
            }
        } else {
            addView(temp, 0)
        }
    }

    /**
     * 调整child的先后关系：按照上一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    private fun prevCarouselLayout() {
        val isLast = isLastPage()
        curPageIndex--
        if (isLast || isFirstPage()) return

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getNextPage()!!         // 移除最底层的nextPage，插入到最顶层，即prevPage上面
        removeView(temp)
        val prevPageIndex = curPageIndex - 1
        if (prevPageIndex >= 1) {
            if (pageCache.getAttachedPageType(temp) == viewHolderAdapter.getItemViewType(prevPageIndex - 1)) {
                addView(temp, 2)
                viewHolderAdapter.onBindViewHolder(pageCache.getAttachedPage(temp), prevPageIndex - 1)
            } else {
                // viewtype不同，得从缓存中获取，或者创建新的
                pageCache.recycle(temp)
                val holder = pageCache.getViewHolder(prevPageIndex - 1)
                viewHolderAdapter.onBindViewHolder(holder, prevPageIndex - 1)
                addView(holder.itemView, 2)
                pageCache.attach(holder)
            }
        } else {
            addView(temp, 2)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        pageManager.dispatchDraw(canvas)
    }

    interface OnClickRegionListener {
        /**
         * 点击事件回调
         * OnClickRegionListener优先级比OnClickListener高，只有当OnClickRegionListener.onClickRegion返回false时，OnClickListener.onClick才会执行
         * @param xPercent 点击的位置在x轴方向上的百分比，例如xPercent=50，表示点击的位置为屏幕x轴方向上的中间
         * @param yPercent 点击的位置在y轴方向上的百分比
         * @return 返回true表示事件已被处理，将不会再调用OnClickListener.onClick；否则，将会调用OnClickListener.onClick
         */
        fun onClickRegion(xPercent: Int, yPercent: Int): Boolean
    }

    /**
     * 设置点击区域事件回调
     */
    fun setOnClickRegionListener(listener: OnClickRegionListener) {
        this.onClickRegionListener = listener
    }

    /**
     * 设置点击区域事件回调
     */
    fun setOnClickRegionListener(listener: (xPercent: Int, yPercent: Int) -> Boolean) {
        this.onClickRegionListener = object : OnClickRegionListener {
            override fun onClickRegion(xPercent: Int, yPercent: Int): Boolean = listener(xPercent, yPercent)
        }
    }

    /**
     * 当前页码为最后一页
     */
    fun isLastPage() = curPageIndex >= itemCount

    /**
     * 当前页码为最后一页
     */
    fun isFirstPage() = curPageIndex <= 1

    fun getPrevPage(): View? {
        // itemCount <= 1，必然没有prevPage
        if (itemCount <= 1) return null
        return if (isFirstPage()) {
            null
        } else if (isLastPage()) {
            getChildAt(1)!!
        } else {
            getChildAt(2)!!
        }
    }

    fun getCurPage(): View? {
        // itemCount=0，没有page
        if (itemCount == 0) return null
        return if (isFirstPage()) {
            getChildAt(childCount - 1)!!              // 当前页为第一页，因此对于的page为最上面的view
        } else if (isLastPage()) {
            getChildAt(0)!!                           // 当前页为最后一页，因此对于的page为最下面的view
        } else {
            getChildAt(1)!!                           // 当前页既不是第一页也不是最后一页，显然childCount=3，且对应的page一定为中间的view
        }
    }

    fun getNextPage(): View? {
        // itemCount <= 1，必然没有nextPage
        if (itemCount <= 1) return null
        return if (isFirstPage()) {
            getChildAt(childCount - 2)!!
        } else if (isLastPage()) {
            null
        } else {
            getChildAt(0)
        }
    }

    /**
     * 获取位于当前page之前的全部pages
     * 如果当前页为最后一页，且childCount=3，此时其实有2个prevPage
     * 如果当前页为第一页，没有prevPage
     * 如果当前既不是第一页、也不是最后一页，那么只有1个prevPage
     */
    fun getAllPrevPages(): List<View> {
        val res = mutableListOf<View>()
        if (itemCount >= 2) {
            if (isLastPage()) {
                if (itemCount >= 3) {
                    res.add(getChildAt(1)!!)
                    res.add(getChildAt(2)!!)
                } else {
                    res.add(getChildAt(1)!!)
                }
            } else if (!isFirstPage()) {
                res.add(getChildAt(2)!!)
            }
        }
        return res
    }

    /**
     * 获取位于当前page之后的全部pages
     */
    fun getAllNextPages(): List<View> {
        val res = mutableListOf<View>()
        if (itemCount >= 2) {
            if (isFirstPage()) {
                if (itemCount >= 3) {
                    res.add(getChildAt(0)!!)
                    res.add(getChildAt(1)!!)
                } else {
                    res.add(getChildAt(0)!!)
                }
            } else if (!isLastPage()) {
                res.add(getChildAt(0)!!)
            }
        }
        return res
    }

    interface OnFlipListener {
        /**
         * 监听翻页事件，当翻页执行完以后，就会调用该回调
         * @param flipDirection 翻页方向，翻向下一页为NEXT，上一页为PREV，翻页未完成直接复位为NONE
         * @param curPageIndex 翻页事件完成以后得当前页码
         */
        fun onFlip(flipDirection: PageDirection, @IntRange(from = 1) curPageIndex: Int)
    }

    /**
     * 设置翻页回调
     */
    fun setOnFlipListener(listener: OnFlipListener) {
        this.onFlipListener = listener
    }

    /**
     * 设置翻页回调
     */
    fun setOnFlipListener(listener: (flipDirection: PageDirection, curPageIndex: Int) -> Unit) {
        this.onFlipListener = object : OnFlipListener {
            override fun onFlip(flipDirection: PageDirection, @IntRange(from = 1) curPageIndex: Int) = listener(flipDirection, curPageIndex)
        }
    }

    /**
     * 设置cached page最大数量
     */
    fun setMaxCachedPageNum(num: Int) {
        pageCache.maxCachedPage = num
    }

    /**
     * 保存状态
     * TODO：尽可能保存所有能影响PageContainer行为的状态，如PageManager、Adapter
     * TODO: 无需处理子View的状态，子View的状态应该交由它自己处理
     */
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.scaledTouchSlop = this.scaledTouchSlop
        savedState.flipTouchSlop = this.flipTouchSlop
        savedState.curPageIndex = this.curPageIndex
        savedState.maxCachedPage = this.pageCache.maxCachedPage
        savedState.minPageTurnInterval = this.minPageTurnInterval
        return savedState
    }

    /**
     * 恢复状态
     */
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            this.scaledTouchSlop = state.scaledTouchSlop
            this.flipTouchSlop = state.flipTouchSlop
            initPageIndex(state.curPageIndex)       // 无法通过简单的设置curPageIndex达到恢复的目的
            setMaxCachedPageNum(state.maxCachedPage)
            this.minPageTurnInterval = state.minPageTurnInterval
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * 状态类
     * 当横屏/竖屏状态发生改变时，保存PageContainer状态，以便能够重建PageContainer的状态
     */
    private class SavedState : BaseSavedState {
        var scaledTouchSlop: Int = 0
        var flipTouchSlop: Int = 0
        var curPageIndex: Int = 0
        var maxCachedPage: Int = 0
        var minPageTurnInterval: Int = 0


        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            scaledTouchSlop = parcel.readInt()
            flipTouchSlop = parcel.readInt()
            curPageIndex = parcel.readInt()
            maxCachedPage = parcel.readInt()
            minPageTurnInterval = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(scaledTouchSlop)
            out.writeInt(flipTouchSlop)
            out.writeInt(curPageIndex)
            out.writeInt(maxCachedPage)
            out.writeInt(minPageTurnInterval)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

}