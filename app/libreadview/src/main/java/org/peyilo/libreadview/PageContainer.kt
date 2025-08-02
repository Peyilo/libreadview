package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.database.Observable
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
import androidx.annotation.IntRange
import androidx.core.view.isNotEmpty
import kotlin.math.max
import kotlin.math.min

/**
 * Usage:
 * > val pageContainer: PageContainer = findViewById(R.id.pageContainerTop)
 * > pageContainer.pageManager = CoverPageManager()
 * > pageContainer.adapter = MyAdapter()
 *
 * TODO: 测试100种类型以上page时的回收复用表现
 * TODO: 对于水平翻页的PageManager，考虑支持向上或向下的手势，以实现类似于起点阅读、番茄小说类似的书签、段评功能
 * TODO：横屏、竖屏状态改变时，需要保存状态、并恢复状态
 * TODO: 如果PageContainer中一开始没有child，之后添加新的child，由于没有触发initPagePosition，导致所有的child都叠在一起，也就是
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
    private var _layoutManager: LayoutManager? = null
    var layoutManager: LayoutManager
        set(value) {
            _layoutManager?.let {
                pageCache.getAllPages().forEach { page ->
                    _layoutManager!!.onResetPage(page)        // 在PageManager移除之前，对所有的page状态进行复位
                }
                _layoutManager!!.destroy()
            }
            _layoutManager = value        // 清除pageManager中包含的PageContainer引用
            value.setPageContainer(this)
        }
        get() = _layoutManager
            ?: throw IllegalStateException("LayoutManager is not initialized. Did you forget to set it?")

    private var _adapter: Adapter<out ViewHolder>? = null
    var adapter: Adapter<out ViewHolder>
        get() = _adapter ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) {
            _adapter?.apply {
                unregisterAll()         // 注销全部观察者
            }
            _adapter = value
            pageCache.setAdapter(adapter)
            adapter.apply {
                registerAdapterDataObserver(PageDataObserver())
                notifyDataSetChanged()
            }
        }
    @Suppress("UNCHECKED_CAST")
    private val viewHolderAdapter: Adapter<ViewHolder> get() = (adapter as Adapter<ViewHolder>)

    val itemCount: Int get() = _adapter?.getItemCount() ?: 0

    /**
     * 根据点击区域监听点击事件
     */
    internal var onClickRegionListener: OnClickRegionListener? = null

    /**
     * 监听翻页事件，在翻页完成时调用
     */
    internal var onFlipListener: OnFlipListener? = null

    private val curPageIndexLock = Any()
    /**
     * 表示当前页码，从1开始
     */
    @IntRange(from = 1)
    var curPageIndex = 1
        internal set(value) {
            synchronized(curPageIndexLock) {
                if (field != value) {
                    Log.d(TAG, "setCurPageIndex: $field -> $value")
                }
                field = value
            }
        }
        get() {
            synchronized(curPageIndexLock) {
                return field
            }
        }


    private val _pageCache: PageCache<out ViewHolder> by lazy { PageCache(this) }
    @Suppress("UNCHECKED_CAST")
    private val pageCache: PageCache<ViewHolder> get() = _pageCache as PageCache<ViewHolder>

    private val maxAttachedPage = 3             // child view最大数量

    /**
     * 初始化页码：设置一个标记位curPageIndex，在adapter设置之后决定显示页码
     * 注意，这个函数仅提供初始化页码功能，不能用于任意页码跳转。
     * 该函数建议在设置Adapter之前调用，这样消耗少一点。
     */
    fun initPageIndex(@IntRange(from = 1) pageIndex: Int) {
        curPageIndex = pageIndex
        if (_adapter != null) {
            // TODO：不应该通知观察者数据集改变了，这样开销很大
            _adapter!!.notifyDataSetChanged()
        }
    }

    companion object {

        private const val TAG = "PageContainer"

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



    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _layoutManager?.let {
            _layoutManager!!.destroy()
            _layoutManager = null
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
        if (layoutManager.needInitPagePosition) {
            layoutManager.initPagePosition()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return layoutManager.onTouchEvent(event)
    }

    open fun hasPrevPage(): Boolean = curPageIndex > 1

    open fun hasNextPage(): Boolean = curPageIndex < viewHolderAdapter.getItemCount()

    /**
     * 翻向下一页
     * @return 是否翻页成功
     */
    fun flipToNextPage(limited: Boolean = true): Boolean {
        if (hasNextPage()) {
            return layoutManager.flipToNextPage(limited)
        }
        return false
    }

    /**
     * 翻向上一页
     * @return 是否翻页成功
     */
    fun flipToPrevPage(limited: Boolean = true): Boolean {
        if (hasPrevPage()) {
            return layoutManager.flipToPrevPage(limited)
        }
        return false
    }

    abstract class LayoutManager {

        /**
         * 注意及时清除PageManager中的pageContainer引用，避免内存泄露
         */
        private var _pageContainer: PageContainer? = null
        protected val pageContainer: PageContainer
            get() = _pageContainer ?: throw IllegalStateException("PageContainer is not initialized. Did you forget to call setPageContainer()?")

        internal fun setPageContainer(pageContainer: PageContainer) {
            _pageContainer = pageContainer
        }

        var needInitPagePosition = true
            protected set

        open fun destroy() {
            _pageContainer?.let {
                _pageContainer = null
            }
        }

        /**
         * 设置page的初始位置
         */
        open fun initPagePosition() {
            needInitPagePosition = false
        }

        abstract fun onTouchEvent(event: MotionEvent): Boolean

        /**
         * 这个函数会在PageContainer.computeScroll()中被调用，可以用来获取Scroller的偏移，实现平滑的动画
         */
        open fun computeScroll() = Unit

        /**
         * 调用该函数，将会直接触发翻向下一页动画，返回值表示是否翻页成功
         */
        abstract fun flipToNextPage(limited: Boolean = true): Boolean

        /**
         * 调用该函数，将会直接触发翻向上一页，返回值表示是否翻页成功
         */
        abstract fun flipToPrevPage(limited: Boolean = true): Boolean

        open fun dispatchDraw(canvas: Canvas) = Unit

        /**
         * TODO: 失败的设计，不应该让各个PageManager互相影响
         * 当PageManager改变时，会调用这个函数，PageManager的实现类需要保证在该函数内恢复所有Page的初始状态，
         * 以免影响其他的PageManager的正常工作
         */
        open fun onResetPage(view: View) = Unit

        open fun onAddPage(view: View, position: Int) = Unit

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

        /**
         * 清空全部缓存，并且设置新的adapter
         */
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
                if (holder.mItemViewType == viewType) {
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
                viewHolder = adapter.createViewHolder(viewGroup, viewType)
                Log.d(TAG, "getViewHolder: create new page")
            }

            Log.d(TAG, "getViewHolder: ${viewHolder.mPosition} -> $position")

            adapter.bindViewHolder(viewHolder, position)
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

        fun getAttachedPageType(view: View): Int = getAttachedPage(view).mItemViewType
        fun getAttachedPagePosition(view: View): Int = getAttachedPage(view).mPosition

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

        fun getAllAttachedViewHolder(): List<ViewHolder> {
            val res = mutableListOf<ViewHolder>()
            attachedPages.forEach {
                res.add(it.value)
            }
            return res
        }
    }

    abstract class ViewHolder(val itemView: View) {
        internal var mItemViewType: Int = INVALID_TYPE

        internal var mPosition: Int = NO_POSITION

        internal var mOldPosition: Int = NO_POSITION

        internal var mItemId: Long = NO_ID

        /**
         * ViewHolder被清除的时候，会调用这个函数，可以在这里做一下清除工作
         */
        open fun onRecycled() = Unit

        companion object {
            const val NO_POSITION = -1
            const val INVALID_TYPE = -1
            const val NO_ID = -1L
        }

    }

    /**
     * 观察者模式：观察者
     */
    abstract class AdapterDataObserver {
        open fun onDatasetChanged() {
            // Do nothing
        }

        open fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            // do nothing
        }

        open fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            // do nothing
        }

        open fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            // do nothing
        }

    }

    /**
     * 观察者模式：被观察者
     */
    class AdapterDataObservable: Observable<AdapterDataObserver>() {

        fun hasObservers(): Boolean {
            return !mObservers.isEmpty()
        }

        fun notifyDatasetChanged() {
            // since onChanged() is implemented by the app, it could do anything, including
            // removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (i in mObservers.indices.reversed()) {
                mObservers[i].onDatasetChanged()
            }
        }

        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
            for (i in mObservers.indices.reversed()) {
                mObservers[i].onItemRangeChanged(positionStart, itemCount)
            }
        }

        fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
            // since onItemRangeInserted() is implemented by the app, it could do anything,
            // including removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (i in mObservers.indices.reversed()) {
                mObservers[i].onItemRangeInserted(positionStart, itemCount)
            }
        }

        fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
            // since onItemRangeRemoved() is implemented by the app, it could do anything, including
            // removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (i in mObservers.indices.reversed()) {
                mObservers[i].onItemRangeRemoved(positionStart, itemCount)
            }
        }

    }

    abstract class Adapter<VH : ViewHolder> {
        // 通过AdapterDataObservable()提供观察者模式的实现
        private val mObservable = AdapterDataObservable()

        fun createViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder = onCreateViewHolder(parent, viewType)
            holder.mItemViewType = viewType
            return holder
        }

        fun bindViewHolder(holder: VH, @IntRange(from = 0) position: Int) {
            onBindViewHolder(holder, position)
            holder.mPosition = position
        }

        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, @IntRange(from = 0) position: Int)
        abstract fun getItemCount(): Int
        open fun getItemViewType(@IntRange(from = 0) position: Int) = 0

        /**
         * Register a new observer to listen for data changes.
         *
         * @param observer Observer to register
         *
         */
        fun registerAdapterDataObserver(observer: AdapterDataObserver) {
            mObservable.registerObserver(observer)
        }

        /**
         * Unregister an observer currently listening for data changes.
         *
         *
         * The unregistered observer will no longer receive events about changes
         * to the adapter.
         *
         * @param observer Observer to unregister
         *
         */
        fun unregisterAdapterDataObserver(observer: AdapterDataObserver) {
            mObservable.unregisterObserver(observer)
        }

        fun unregisterAll() {
            mObservable.unregisterAll()
        }

        fun notifyDataSetChanged() {
            mObservable.notifyDatasetChanged()
        }

        fun notifyItemChanged(position: Int) {
            mObservable.notifyItemRangeChanged(position, 1)
        }

        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
            mObservable.notifyItemRangeChanged(positionStart, itemCount)
        }

        fun notifyItemInserted(position: Int) {
            mObservable.notifyItemRangeInserted(position, 1)
        }

        fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
            mObservable.notifyItemRangeInserted(positionStart, itemCount)
        }

        /**
         * Notify any registered observers that the item previously located at position has been removed from the data set.
         *
         * @param position Position of the item that has now been removed
         */
        fun notifyItemRemoved(position: Int) {
            mObservable.notifyItemRangeRemoved(position, 1)
        }

        fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
            mObservable.notifyItemRangeRemoved(positionStart, itemCount)
        }

    }

    override fun addView(child: View?, index: Int) {
        val index = if (index == -1) childCount else index
        super.addView(child, index)
        layoutManager.onAddPage(child!!, index)
    }

    private inner class PageDataObserver : AdapterDataObserver() {

        /**
         * 这会移除PageContainer中的全部child，并且为pageCache设置新的adapter，这会导致pageCache中的缓存全部被清空。
         * 同时，还会根据设置的adapter，往pageContainer中添加新的child
         */
        override fun onDatasetChanged() {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                pageCache.recycle(child)
            }
            if (isNotEmpty()) {
                removeAllViews()
            }

            val oldPageIndex = curPageIndex

            val count = itemCount
            val pageRange = getPageRange(curPageIndex, maxAttachedPage, count)
            pageRange.reversed().forEachIndexed { i,  pageIndex ->
                val position = pageIndex - 1            // 页码转position
                val holder = pageCache.getViewHolder(position)
                addView(holder.itemView)
                pageCache.attach(holder)
            }

            Log.d(TAG, "onDatasetChanged: oldPageIndex = $oldPageIndex, curPageIndex = $curPageIndex")
        }

        /**
         * page数量没有发生改变，只是某些位置page的内容改变了，注意：page的类型也可能发生了改变。
         * 因此，仅当改变的page处于attach状态下，才需要进行视图更新
         */
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = curPageIndex

            // 先根据curPageIndex获取目前处于attach状态下的page的indices
            val pageRange = getPageRange(curPageIndex, maxAttachedPage, this@PageContainer.itemCount)
            val attachedPages = mutableSetOf<Int>()
            pageRange.forEach {
                attachedPages.add(it - 1)
            }

            // 然后，再根据positionStart和itemCount，获取需要更新的page的indices
            val updatedItems = (positionStart until positionStart + itemCount).toSet()

            // 取两个部分的交集，即需要视图更新的page的indices
            val pagesToUpdate = attachedPages.intersect(updatedItems)

            if (pagesToUpdate.isEmpty()) return

            // 遍历所有的child，更新pagesToUpdate中对应的page
            val childCount = childCount
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val cache = pageCache.getAttachedPage(child)
                // 不在pagesToUpdate中，直接跳过
                if (!pagesToUpdate.contains(cache.mPosition)) continue

                // page的type并没有改变
                if (viewHolderAdapter.getItemViewType(cache.mPosition) == cache.mItemViewType) {
                    viewHolderAdapter.bindViewHolder(cache, cache.mPosition)
                    child.invalidate()
                } else {
                    // page的type发生了改变: 先移除child，然后再创建新的viewholder，然后添加进children view中
                    removeView(child)
                    pageCache.recycle(child)
                    val holder = pageCache.getViewHolder(cache.mPosition)
                    addView(holder.itemView, i)     // 添加回原本的位置
                    pageCache.attach(holder)
                }
            }

            Log.d(TAG, "onItemRangeChanged: oldPageIndex = $oldPageIndex, curPageIndex = $curPageIndex")
        }

        /**
         * 插入之后的目标，是尽量保证当前显示的页面不变
         */
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = curPageIndex

            // 插入之前的itemCount
            val prevItemCount = this@PageContainer.itemCount - itemCount

            // 获取目前处于attach状态下的page的position
            val attachedPagesPosition = mutableListOf<Int>()
            pageCache.getAllAttachedViewHolder().forEach {
                attachedPagesPosition.add(it.mPosition)
            }
            attachedPagesPosition.sort()


            when {
                attachedPagesPosition.isEmpty() -> {
                    val count = itemCount
                    // 约束curPageIndex在有效取值范围内, 如果count=0，将curPageIndex也约束到1
                    curPageIndex = curPageIndex.coerceIn(1, count)
                    val pageRange = getPageRange(curPageIndex, maxAttachedPage, count)
                    pageRange.reversed().forEachIndexed { i,  pageIndex ->
                        val position = pageIndex - 1            // 页码转position
                        val holder = pageCache.getViewHolder(position)
                        addView(holder.itemView)
                        pageCache.attach(holder)
                    }
                }
                // 所有插入的pages，都在attachedPages之后，因此不会对已有视图有任何影响
                positionStart > attachedPagesPosition[attachedPagesPosition.size - 1] -> {
                    // 在新数据插入之前，当前页为最后一页
                    if (curPageIndex == prevItemCount) {
                        if (prevItemCount >= 3) {
                            // 移除最靠前的attachPage，然后在当前page之后插入一个新的
                            assert(attachedPagesPosition.size == 3)
                            assert(childCount == 3)
                            val removedView = getChildAt(2)
                            removeView(removedView)
                            pageCache.recycle(removedView)
                            // 获取下一页的holder: curPageIndex = curPosition + 1
                            val holder = pageCache.getViewHolder(curPageIndex)
                            addView(holder.itemView, 0)
                            pageCache.attach(holder)
                        } else if (prevItemCount == 2) {
                            // 当前只有两个attachPage，且当前页为最后一页，因此需要再当前页后面，插入一个新的page
                            assert(attachedPagesPosition.size == 2)
                            assert(childCount == 2)
                            // 获取下一页的holder: curPageIndex = curPosition + 1
                            val holder = pageCache.getViewHolder(curPageIndex)
                            addView(holder.itemView, 0)
                            pageCache.attach(holder)
                        } else {
                            assert(attachedPagesPosition.size == 1)
                            assert(childCount == 1)
                            // 插入之前只有一页，因此需要尝试在后面插入两页、或者一页
                            val addCount = min(2, itemCount)
                            for (i in 0 until addCount) {
                                val holder = pageCache.getViewHolder(curPageIndex + i)
                                // 如果是插入两次，第二次插入就会把第一次插入的往前挤，刚好达到了目的
                                addView(holder.itemView, 0)
                                pageCache.attach(holder)
                            }
                        }
                    } else if (curPageIndex == 1 && prevItemCount == 2) {
                        assert(attachedPagesPosition.size == 2)
                        assert(childCount == 2)
                        // 因为当前有两个attachpage，且当前显示的page为第一页，因此需要插入一个page在最底下
                        // 获取下下一页的holder: curPageIndex + 1 = curPosition + 2
                        val holder = pageCache.getViewHolder(curPageIndex + 1)
                        addView(holder.itemView, 0)
                        pageCache.attach(holder)
                    }
                    // TODO: 如果page，依赖于itemCount，因此需要更新当前所有attach
                }
                curPageIndex - 1 < positionStart -> {
                    onDatasetChanged()
                }
                positionStart <= attachedPagesPosition[0] -> {
                    curPageIndex += itemCount
                    onDatasetChanged()
                }
                // 插入起始位置位于curPage之前
                positionStart < curPageIndex - 1 -> {
                    curPageIndex += itemCount
                    onDatasetChanged()
                }
                // 插入位置就位于curPage的位置
                positionStart == curPageIndex - 1 -> {
                    onDatasetChanged()
                }
                else -> throw IllegalStateException()
            }
            Log.d(TAG, "onItemRangeInserted: oldPageIndex = $oldPageIndex, curPageIndex = $curPageIndex")
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = curPageIndex

            // 获取目前处于attach状态下的page的position
            val attachedPagesPosition = mutableListOf<Int>()
            pageCache.getAllAttachedViewHolder().forEach {
                attachedPagesPosition.add(it.mPosition)
            }
            attachedPagesPosition.sort()

            when {
                attachedPagesPosition.isEmpty() -> {
                    throw IllegalStateException("onItemRangeRemoved($positionStart, $itemCount):  attachedPagesPosition.isEmpty()")
                }
                // 删除的item全在attachedPages之前
                positionStart + itemCount <= attachedPagesPosition[0] -> {
                    curPageIndex -= itemCount
                    pageCache.getAllAttachedViewHolder().forEach {
                        it.mPosition -= itemCount
                        viewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 删除的item全在attachedPages之后
                positionStart > attachedPagesPosition[attachedPagesPosition.size - 1] -> {
                    // do nothing
                }
                // 删除的item和attachedPages有重合，且删除的item都位于curPage之前，未触及curPage
                positionStart + itemCount <= curPageIndex -1 -> {
                    curPageIndex -= itemCount
                    onDatasetChanged()
                }
                // 删除的item和attachedPages有重合，且删除的item都位于curPage之后，未触及curPage
                positionStart > curPageIndex - 1 -> {
                    onDatasetChanged()
                }
                // 删除的item触及了curPage
                else -> {
                    if (this@PageContainer.itemCount < curPageIndex) {
                        curPageIndex = this@PageContainer.itemCount
                    }
                    onDatasetChanged()
                }
            }
            Log.d(TAG, "onItemRangeRemoved: oldPageIndex = $oldPageIndex, curPageIndex = $curPageIndex")
        }

    }

    internal open fun onFlip(flipDirection: PageDirection, @IntRange(from = 1) curPageIndex: Int) {
        onFlipListener?.onFlip(flipDirection, curPageIndex)
    }

    override fun computeScroll() {
        layoutManager.computeScroll()
    }

    /**
     * 调整child的先后关系：按照下一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    internal fun nextCarouselLayout() {
        val isFirst = isFirstPage()
        curPageIndex++
        if (isFirst || isLastPage()) return

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getPrevPage()!!         // 移除最顶层的prevPage，插入到最底层，即nextPage下面
        removeView(temp)
        val nextPageIndex = curPageIndex + 1
        if (nextPageIndex <= viewHolderAdapter.getItemCount()) {
            // viewtype不同，得从缓存中获取，或者创建新的
            pageCache.recycle(temp)
            val holder = pageCache.getViewHolder(nextPageIndex - 1)
            addView(holder.itemView, 0)
            pageCache.attach(holder)
        } else {
            addView(temp, 0)
        }
    }

    /**
     * 调整child的先后关系：按照上一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    internal fun prevCarouselLayout() {
        val isLast = isLastPage()
        curPageIndex--
        if (isLast || isFirstPage()) return

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getNextPage()!!         // 移除最底层的nextPage，插入到最顶层，即prevPage上面
        removeView(temp)
        val prevPageIndex = curPageIndex - 1
        if (prevPageIndex >= 1) {
            // viewtype不同，得从缓存中获取，或者创建新的
            pageCache.recycle(temp)
            val holder = pageCache.getViewHolder(prevPageIndex - 1)
            addView(holder.itemView, 2)
            pageCache.attach(holder)
        } else {
            addView(temp, 2)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        layoutManager.dispatchDraw(canvas)
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
                    res.add(getChildAt(1)!!)
                    res.add(getChildAt(0)!!)
                } else {
                    res.add(getChildAt(0)!!)
                }
            } else if (!isLastPage()) {
                res.add(getChildAt(0)!!)
            }
        }
        return res
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

    interface OnFlipListener {
        /**
         * 监听翻页事件，当翻页执行完以后，就会调用该回调
         * @param flipDirection 翻页方向，翻向下一页为NEXT，上一页为PREV，翻页未完成直接复位为NONE
         * @param curPageIndex 翻页事件完成以后得当前页码
         */
        fun onFlip(flipDirection: PageDirection, @IntRange(from = 1) curPageIndex: Int)
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


        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            scaledTouchSlop = parcel.readInt()
            flipTouchSlop = parcel.readInt()
            curPageIndex = parcel.readInt()
            maxCachedPage = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(scaledTouchSlop)
            out.writeInt(flipTouchSlop)
            out.writeInt(curPageIndex)
            out.writeInt(maxCachedPage)
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