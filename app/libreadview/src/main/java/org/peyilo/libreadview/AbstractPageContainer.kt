package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.database.Observable
import android.graphics.Canvas
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.core.view.isNotEmpty
import org.peyilo.libreadview.utils.LogHelper
import kotlin.math.max
import kotlin.math.min

/**
 *
 * TODO: 测试100种类型以上page时的回收复用表现
 * TODO: 对于水平翻页的PageManager，考虑支持向上或向下的手势，以实现类似于起点阅读、番茄小说类似的书签、段评功能
 * TODO：横屏、竖屏状态改变时，需要保存状态、并恢复状态
 */
abstract class AbstractPageContainer(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): ViewGroup(context, attrs, defStyleAttr, defStyleRes), PageNavigator {

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
            _layoutManager?.apply {
                forceNotInLayoutOrScroll()
                destory()
            }
            _layoutManager = value        // 清除pageManager中包含的PageContainer引用
            value.setPageContainer(this)
            resetPagePosition()
            value.initPagePosition(false)
        }
        get() = _layoutManager
            ?: throw IllegalStateException("LayoutManager is not initialized. Did you forget to set it?")

    private var innerAdapter: Adapter<out ViewHolder>? = null
    @Suppress("UNCHECKED_CAST")
    private val mViewHolderAdapter: Adapter<ViewHolder> get() = (innerAdapter!! as Adapter<ViewHolder>)

    /**
     * 根据点击区域监听点击事件
     */
    internal var mOnClickRegionListener: OnClickRegionListener? = null

    /**
     * 监听翻页事件，在翻页完成时调用
     */
    private var mOnPageChangeListener: OnPageChangeListener? = null

    /**
     * mCurContainerPageIndex变量的同步锁
     */
    private val mCurPageIndexLock = Any()

    /**
     * 表示当前显示页面的页码，从1开始
     */
    @IntRange(from = 1)
    private var mCurContainerPageIndex = 1
        set(value) = synchronized(mCurPageIndexLock) {
            if (field != value) {
                LogHelper.d(TAG, "setCurPageIndex: $field -> $value")
            }
            field = value
        }
        get() = synchronized(mCurPageIndexLock) { field }


    private val _pageCache: PageCache<out ViewHolder> by lazy { PageCache(this) }

    /**
     * Page缓存
     */
    @Suppress("UNCHECKED_CAST")
    private val mPageCache: PageCache<ViewHolder> get() = _pageCache as PageCache<ViewHolder>

    /**
     * child view最大数量
     */
    private val mMaxAttachedPage = 3

    private var initContainerPageIndexFlag = true

    /**
     * 初始化mCurContainerPageIndex变量, 并且将initContainerPageIndexFlag置为false, 只能调用一次
     */
    @Synchronized
    protected fun initContainerPageIndex(@IntRange(from = 1) pageIndex: Int) {
        if (initContainerPageIndexFlag) {
            initContainerPageIndexFlag = false
            mCurContainerPageIndex = pageIndex
        } else {
            throw IllegalStateException("initContainerPageIndex() should be called only once")
        }
    }

    private fun resetPagePosition() {
        mPageCache.getAllPages().forEach {
            it.translationX = 0F
            it.translationY = 0F
        }
    }

    protected fun getInnerAdapter(): Adapter<out ViewHolder>? = innerAdapter

    protected fun setInnerAdapter(adapter: Adapter<out ViewHolder>) {
        innerAdapter?.apply {
            unregisterAll()         // 注销全部观察者
        }
        innerAdapter = adapter
        mPageCache.setAdapter(adapter)
        adapter.apply {
            registerAdapterDataObserver(PageDataObserver())
            notifyDataSetChanged()
        }
    }

    companion object {

        private const val TAG = "AbstractPageContainer"

        /**
         * 给定当前页 index、展示页数 MAX_PAGES、总页数 numPages，
         * 返回一个以当前页为中心、但页码范围在 [1, numPages] 之间的页码列表。
         * @param index 当前页的页码
         * @param maxPages 返回的列表中包含的pageIndex数不会超过 maxPages
         * @param numPages 总页数
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
     * 在这里处理一下收尾与数据销毁工作：如销毁
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _layoutManager?.apply {
            destory()
            _layoutManager = null
        }
        mPageCache.destroy()
    }

    /**
     * 每个子view都将会和PageContainer一样大
     */
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

    /**
     * 负责实现各种翻页模式
     */
    abstract class LayoutManager {

        /**
         * 注意及时清除PageManager中的pageContainer引用，避免内存泄露
         */
        private var _pageContainer: AbstractPageContainer? = null
        protected val pageContainer: AbstractPageContainer
            get() = _pageContainer ?: throw IllegalStateException("AbstractPageContainer is not initialized. Did you forget to call setPageContainer()?")

        internal fun setPageContainer(pageContainer: AbstractPageContainer) {
            _pageContainer = pageContainer
        }

        /**
         * 只有当needInitPagePosition为true的时候，AbstractPageContainer.onLayout中才会调用onInitPagePosition()
         */
        internal var needInitPagePosition = true
            private set

        /**
         * 销毁LayoutManager
         */
        fun destory() {
            _pageContainer?.let {
                _pageContainer = null
            }
            onDestroy()
        }

        protected fun requestReInitPagePosition() {
            needInitPagePosition = true
        }

        /**
         * 在这里进行一些销毁操作
         */
        protected open fun onDestroy() = Unit

        /**
         * 初始化Page的位置，调用该函数会将needInitPagePosition置为false，并回调onInitPagePosition()
         */
        fun initPagePosition(changeState: Boolean = true) {
            if (changeState) needInitPagePosition = false
            onInitPagePosition()
        }

        /**
         * 设置page的初始位置
         */
        protected open fun onInitPagePosition() = Unit

        abstract fun onTouchEvent(event: MotionEvent): Boolean

        /**
         * 这个函数会在PageContainer.computeScroll()中被调用，可以用来获取Scroller的偏移，实现平滑的动画
         */
        open fun computeScroll() = Unit

        /**
         * 调用该函数，将会直接触发翻向下一页动画，返回值表示是否翻页成功
         *  @param limited 是否限制翻页速度（一定时间内的翻页次数）
         */
        abstract fun flipToNextPage(limited: Boolean = true): Boolean

        /**
         * 调用该函数，将会直接触发翻向上一页，返回值表示是否翻页成功
         * @param limited 是否限制翻页速度（一定时间内的翻页次数）
         */
        abstract fun flipToPrevPage(limited: Boolean = true): Boolean

        /**
         * force the state of layoutmanager is not in layout or scroll.
         * this method will abort the animation of layoutmanager and enture the view not in layout.
         */
        abstract fun forceNotInLayoutOrScroll()

        /**
         * 可以在这里进行绘制阴影、动画
         * @param canvas 内容可以绘制在这个画布上
         */
        open fun dispatchDraw(canvas: Canvas) = Unit

        /**
         * @param view the view be initialized
         * @param position the view will be added to this position thro ViewGroup.addView(view, position)
         */
        abstract fun onAddPage(view: View, position: Int)

    }

    /**
     * 翻页方向：有三种向上翻页、向下翻页，以及第三种：复位翻页(也就是一开始拖动了页面，但是手势表明，用户想取消
     * 翻页的行为), 同时None也表示没有翻页方向
     */
    enum class PageDirection {
        PREV, NEXT, NONE
    }

    /**
     * 用于记录某次手势过程中，当前位置以及起始位置，并提供了计算偏移量的API
     */
    class Gesture {
        val cur = PointF()
        val down = PointF()
        val up = PointF()

        /**
         * 水平偏移量
         */
        val dx get() = cur.x - down.x

        /**
         * 垂直偏移量
         */
        val dy get() = cur.y - down.y

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

    /**
     * Page的缓存池
     */
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
            private const val TAG = "AbstractPageContainer.PageCache"
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
         * @param position the position of itemView in Adapter
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
                LogHelper.d(TAG, "getViewHolder: use cached page")
            }

            // 如果没有找到，则创建新的
            if (viewHolder == null) {
                viewHolder = adapter.createViewHolder(viewGroup, viewType)
                LogHelper.d(TAG, "getViewHolder: create new page")
            }

            LogHelper.d(TAG, "getViewHolder: ${viewHolder.mPosition} -> $position")

            adapter.bindViewHolder(viewHolder, position)
            return viewHolder
        }

        /**
         * 从attachedPages中回收，并加入cachedPages缓存中
         */
        fun recycleAttachedView(view: View) {
            if (view !in attachedPages) {
                throw IllegalStateException("make ture that the view is contained in attachedPages!")
            }
            val viewHolder = attachedPages[view]!!
            cachedPages.add(viewHolder)
            attachedPages.remove(view)
            viewHolder.onRecycled()

            // 如果cachedPages超过maxCachedPage，就应该按照LRU的规则清理
            if (cachedPages.size > maxCachedPage) {
                val removed = cachedPages.removeAt(0) // 移除最久未用的
                removed.onRemoved()
                LogHelper.d(TAG, "recycle: remove cached page")
            }
        }

        /**
         * 添加attachedPage
         * @param viewHolder the view to attach
         */
        fun attachView(viewHolder: ViewHolder) {
            attachedPages[viewHolder.itemView] = viewHolder
        }

        /**
         * 销毁PageCache：这会将保存的adapter和PageContainer置为null，同时清理全部attached、cached pages
         */
        fun destroy() {
            clear()
            _adapter = null
            _viewGroup = null
        }

        /**
         * 清理全部attached、cached pages
         */
        fun clear() {
            attachedPages.forEach {
                it.value.onRemoved()
            }
            cachedPages.forEach {
                it.onRemoved()
            }
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

        /**
         * 返回全部attachedPages和cachedPages中保存的View
         */
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
        /**
         * ViewHolder保存的ItemView的类型
         */
        internal var mItemViewType: Int = INVALID_TYPE

        /**
         * ViewHolder保存的ItemView在Adapter中的位置
         */
        internal var mPosition: Int = NO_POSITION

        /**
         * 从attach状态（即作为PageContainer的子View）变为了cache，被放入了缓存池中，以便复用。
         * 这个时候，会调用这个函数
         */
        open fun onRecycled() = Unit

        /**
         * 当缓存池满了，根据LRU规则，从缓存池中被移除的时候（移除之后，将不再有机会作为子View），会回调这个函数。
         * 可以在这里做一下清除工作
         */
        open fun onRemoved() = Unit

        companion object {
            const val NO_POSITION = -1
            const val INVALID_TYPE = -1
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

        open fun onItemRangeReplaced(positionStart: Int, oldItemCount: Int, newItemCount: Int) {
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

        fun notifyItemRangeReplaced(positionStart: Int, oldItemCount: Int, newItemCount: Int) {
            for (i in mObservers.indices.reversed()) {
                mObservers[i].onItemRangeReplaced(positionStart, oldItemCount, newItemCount)
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

        fun notifyItemRangeReplaced(positionStart: Int, oldItemCount: Int, newItemCount: Int) {
            mObservable.notifyItemRangeReplaced(positionStart, oldItemCount, newItemCount)
        }

        fun notifyItemReplaced(positionStart: Int, newItemCount: Int) {
            mObservable.notifyItemRangeReplaced(positionStart, 1, newItemCount)
        }

    }

    override fun addView(child: View?, index: Int) {
        val index = if (index == -1) childCount else index
        super.addView(child, index)
        layoutManager.onAddPage(child!!, index)
    }

    private inner class PageDataObserver : AdapterDataObserver() {

        /**
         * 当确定，某个操作会影响当前attachedPages发生改变（不是内容，而是View实例）时，需要调用这个函数保证更新当前attachedPages时，所有的attachedPage不应该不处于滚动或布局中央状态
         */
        private fun forceNotInLayoutOrScroll() {
            if (_layoutManager == null) return
            layoutManager.forceNotInLayoutOrScroll()
        }

        /**
         * 这会移除PageContainer中的全部child，并且为pageCache设置新的adapter，这会导致pageCache中的缓存全部被清空。
         * 同时，还会根据设置的adapter，往pageContainer中添加新的child
         */
        override fun onDatasetChanged() {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                mPageCache.recycleAttachedView(child)
            }
            if (isNotEmpty()) {
                removeAllViews()
            }
            forceNotInLayoutOrScroll()

            val oldPageIndex = mCurContainerPageIndex

            val conatinerPageCount = getContainerPageCount()
            val pageRange = getPageRange(mCurContainerPageIndex, mMaxAttachedPage, conatinerPageCount)
            pageRange.reversed().forEachIndexed { i,  pageIndex ->
                val position = pageIndex - 1            // 页码转position
                val holder = mPageCache.getViewHolder(position)
                addView(holder.itemView)
                mPageCache.attachView(holder)
            }

            LogHelper.d(TAG, "onDatasetChanged: oldPageIndex = $oldPageIndex, curPageIndex = $mCurContainerPageIndex")
        }

        /**
         * page数量没有发生改变，只是某些位置page的内容改变了，注意：page的类型也可能发生了改变。
         * 因此，仅当改变的page处于attach状态下，才需要进行视图更新
         */
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = mCurContainerPageIndex

            // 先根据curPageIndex获取目前处于attach状态下的page的indices
            val pageRange = getPageRange(mCurContainerPageIndex, mMaxAttachedPage, getContainerPageCount())
            val attachedPages = mutableSetOf<Int>()
            pageRange.forEach {
                attachedPages.add(it - 1)
            }

            // 然后，再根据positionStart和itemCount，获取需要更新的page的indices
            val updatedItems = (positionStart until positionStart + itemCount).toSet()

            // 取两个部分的交集，即需要视图更新的page的indices
            val pagesToUpdate = attachedPages.intersect(updatedItems)

            // 如果要更新的item和当前的attachedPages毫无关系，直接退出
            if (pagesToUpdate.isEmpty()) return

            // 遍历所有的child，更新pagesToUpdate中对应的page
            val childCount = childCount
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val cache = mPageCache.getAttachedPage(child)
                // 不在pagesToUpdate中，直接跳过
                if (!pagesToUpdate.contains(cache.mPosition)) continue

                // page的type并没有改变
                if (mViewHolderAdapter.getItemViewType(cache.mPosition) == cache.mItemViewType) {
                    mViewHolderAdapter.bindViewHolder(cache, cache.mPosition)
                    child.invalidate()
                } else {
                    // 因为已经影响到了attachedPages，需要保证它们不处于滚动或布局中央
                    forceNotInLayoutOrScroll()
                    // page的type发生了改变: 先移除child，然后再创建新的viewholder，然后添加进children view中
                    removeView(child)
                    mPageCache.recycleAttachedView(child)
                    val holder = mPageCache.getViewHolder(cache.mPosition)
                    addView(holder.itemView, i)     // 添加回原本的位置
                    mPageCache.attachView(holder)
                }
            }

            LogHelper.d(TAG, "onItemRangeChanged: oldPageIndex = $oldPageIndex, curPageIndex = $mCurContainerPageIndex")
        }

        /**
         * 插入之后的目标，是尽量保证当前显示的页面不变
         */
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = mCurContainerPageIndex

            // 插入之前的itemCount
            val prevItemCount = getContainerPageCount() - itemCount

            // 获取目前处于attach状态下的page的position
            val attachedPagesPosition = mutableListOf<Int>()
            mPageCache.getAllAttachedViewHolder().forEach {
                attachedPagesPosition.add(it.mPosition)
            }
            attachedPagesPosition.sort()

            when {
                // 插入之前，不存在任何item
                attachedPagesPosition.isEmpty() -> {
                    val count = itemCount
                    // 约束curPageIndex在有效取值范围内, 如果count=0，将curPageIndex也约束到1
                    mCurContainerPageIndex = mCurContainerPageIndex.coerceIn(1, count)
                    val pageRange = getPageRange(mCurContainerPageIndex, mMaxAttachedPage, count)
                    pageRange.reversed().forEachIndexed { i,  pageIndex ->
                        val position = pageIndex - 1            // 页码转position
                        val holder = mPageCache.getViewHolder(position)
                        addView(holder.itemView)
                        mPageCache.attachView(holder)
                    }
                }
                // 所有插入的pages，都在attachedPages之后，因此不会对已有视图有任何影响
                positionStart > attachedPagesPosition[attachedPagesPosition.size - 1] -> {
                    // 在新数据插入之前，当前页为最后一页
                    if (mCurContainerPageIndex == prevItemCount) {
                        if (prevItemCount >= 3) {
                            // 移除最靠前的attachPage，然后在当前page之后插入一个新的
                            assert(attachedPagesPosition.size == 3)
                            assert(childCount == 3)
                            val removedView = getChildAt(2)
                            removeView(removedView)
                            mPageCache.recycleAttachedView(removedView)
                            // 获取下一页的holder: curPageIndex = curPosition + 1
                            val holder = mPageCache.getViewHolder(mCurContainerPageIndex)
                            addView(holder.itemView, 0)
                            mPageCache.attachView(holder)
                        } else if (prevItemCount == 2) {
                            // 当前只有两个attachPage，且当前页为最后一页，因此需要再当前页后面，插入一个新的page
                            assert(attachedPagesPosition.size == 2)
                            assert(childCount == 2)
                            // 获取下一页的holder: curPageIndex = curPosition + 1
                            val holder = mPageCache.getViewHolder(mCurContainerPageIndex)
                            addView(holder.itemView, 0)
                            mPageCache.attachView(holder)
                        } else {
                            assert(attachedPagesPosition.size == 1)
                            assert(childCount == 1)
                            // 插入之前只有一页，因此需要尝试在后面插入两页、或者一页
                            val addCount = min(2, itemCount)
                            for (i in 0 until addCount) {
                                val holder = mPageCache.getViewHolder(mCurContainerPageIndex + i)
                                // 如果是插入两次，第二次插入就会把第一次插入的往前挤，刚好达到了目的
                                addView(holder.itemView, 0)
                                mPageCache.attachView(holder)
                            }
                        }
                    } else if (mCurContainerPageIndex == 1 && prevItemCount == 2) {
                        assert(attachedPagesPosition.size == 2)
                        assert(childCount == 2)
                        // 因为当前有两个attachpage，且当前显示的page为第一页，因此需要插入一个page在最底下
                        // 获取下下一页的holder: curPageIndex + 1 = curPosition + 2
                        val holder = mPageCache.getViewHolder(mCurContainerPageIndex + 1)
                        addView(holder.itemView, 0)
                        mPageCache.attachView(holder)
                    }
                    // 如果page，依赖于itemCount，因此需要更新当前所有attach
                    mPageCache.getAllAttachedViewHolder().forEach {
                        mViewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 插入的位置在curPae之后，但是已经影响到了attachedPages
                mCurContainerPageIndex - 1 < positionStart -> {
                    onDatasetChanged()
                }
                // 插入的位置在attachedPages之前，不影响当前的attachedPages
                positionStart <= attachedPagesPosition[0] -> {
                    mCurContainerPageIndex += itemCount
                    mPageCache.getAllAttachedViewHolder().forEach {
                        it.mPosition += itemCount
                        mViewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 插入起始位置位于curPage之前,但是已经影响到attachedPages了
                positionStart < mCurContainerPageIndex - 1 -> {
                    mCurContainerPageIndex += itemCount
                    onDatasetChanged()
                }
                // 插入位置就位于curPage的位置
                positionStart == mCurContainerPageIndex - 1 -> {
                    onDatasetChanged()
                }
                else -> throw IllegalStateException()
            }
            LogHelper.d(TAG, "onItemRangeInserted: oldPageIndex = $oldPageIndex, curPageIndex = $mCurContainerPageIndex")
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (itemCount == 0) return

            val oldPageIndex = mCurContainerPageIndex

            // 获取目前处于attach状态下的page的position
            val attachedPagesPosition = mutableListOf<Int>()
            mPageCache.getAllAttachedViewHolder().forEach {
                attachedPagesPosition.add(it.mPosition)
            }
            attachedPagesPosition.sort()

            when {
                attachedPagesPosition.isEmpty() -> {
                    throw IllegalStateException("onItemRangeRemoved($positionStart, $itemCount):  attachedPagesPosition.isEmpty()")
                }
                // 删除的item全在attachedPages之前
                positionStart + itemCount <= attachedPagesPosition[0] -> {
                    mCurContainerPageIndex -= itemCount
                    mPageCache.getAllAttachedViewHolder().forEach {
                        it.mPosition -= itemCount
                        mViewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 删除的item全在attachedPages之后
                positionStart > attachedPagesPosition[attachedPagesPosition.size - 1] -> {
                    // do nothing
                }
                // 删除的item和attachedPages有重合，且删除的item都位于curPage之前，未触及curPage
                positionStart + itemCount <= mCurContainerPageIndex -1 -> {
                    mCurContainerPageIndex -= itemCount
                    onDatasetChanged()
                }
                // 删除的item和attachedPages有重合，且删除的item都位于curPage之后，未触及curPage
                positionStart > mCurContainerPageIndex - 1 -> {
                    onDatasetChanged()
                }
                // 删除的item触及了curPage
                else -> {
                    if (getContainerPageCount() < mCurContainerPageIndex) {
                        mCurContainerPageIndex = getContainerPageCount()
                    }
                    onDatasetChanged()
                }
            }
            LogHelper.d(TAG, "onItemRangeRemoved: oldPageIndex = $oldPageIndex, curPageIndex = $mCurContainerPageIndex")
        }

        override fun onItemRangeReplaced(positionStart: Int, oldItemCount: Int, newItemCount: Int) {
            // TODO: 先只实现oldItemCount为1的情形
            assert(oldItemCount == 1)
            if (newItemCount == 0) onItemRangeRemoved(positionStart, oldItemCount)

            val oldPageIndex = mCurContainerPageIndex

            // replace之前的itemCount
            val prevItemCount = getContainerPageCount() - oldItemCount + newItemCount

            // 获取目前处于attach状态下的page的position
            val attachedPagesPosition = mutableListOf<Int>()
            mPageCache.getAllAttachedViewHolder().forEach {
                attachedPagesPosition.add(it.mPosition)
            }
            attachedPagesPosition.sort()

            when {
                // 所有删除、再插入的pages，都在attachedPages之后，因此不会对已有视图有任何影响
                positionStart > attachedPagesPosition[attachedPagesPosition.size - 1] -> {
                    mPageCache.getAllAttachedViewHolder().forEach {
                        mViewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 所有删除、再插入的pages，都在curPage之后
                mCurContainerPageIndex - 1 < positionStart -> {
                    onDatasetChanged()
                }
                // 被替换的第一个就是curPage
                mCurContainerPageIndex - 1 == positionStart -> {
                    onDatasetChanged()
                }
                // 所有删除、再插入的pages，都在attachedPages之前
                positionStart + oldItemCount <= attachedPagesPosition[0] -> {
                    mCurContainerPageIndex += newItemCount - oldItemCount
                    mPageCache.getAllAttachedViewHolder().forEach {
                        it.mPosition += newItemCount - oldItemCount
                        mViewHolderAdapter.bindViewHolder(it, it.mPosition)
                        it.itemView.invalidate()
                    }
                }
                // 所有删除、再插入的pages，都在curPage之前
                positionStart < mCurContainerPageIndex - 1 -> {
                    mCurContainerPageIndex += newItemCount - oldItemCount
                    onDatasetChanged()
                }
            }

            LogHelper.d(TAG, "onItemRangeReplaced: oldPageIndex = $oldPageIndex, curPageIndex = $mCurContainerPageIndex")
        }

    }

    protected open fun onPageChanged(@IntRange(from = 1) oldPageIndex: Int, @IntRange(from = 1) newPageIndex: Int) {
        mOnPageChangeListener?.onPageChanged(oldPageIndex, newPageIndex)
    }

    override fun computeScroll() {
        layoutManager.computeScroll()
    }

    /**
     * 调整child的先后关系：按照下一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    fun nextCarouselLayout() {
        val isFirst = isFirstPage()
        val oldPageIndex = mCurContainerPageIndex
        val newPageIndex = mCurContainerPageIndex + 1
        mCurContainerPageIndex = newPageIndex
        if (isFirst || isLastPage()) {
            onPageChanged(oldPageIndex, newPageIndex)
            return
        }

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getPrevPage()!!         // 移除最顶层的prevPage，插入到最底层，即nextPage下面
        removeView(temp)
        val nextPageIndex = mCurContainerPageIndex + 1
        if (nextPageIndex <= mViewHolderAdapter.getItemCount()) {
            // 从缓存中获取，或者创建新的
            mPageCache.recycleAttachedView(temp)
            val holder = mPageCache.getViewHolder(nextPageIndex - 1)
            addView(holder.itemView, 0)
            mPageCache.attachView(holder)
        } else {
            addView(temp, 0)
        }
        onPageChanged(oldPageIndex, newPageIndex)
    }

    /**
     * 调整child的先后关系：按照上一页的顺序进行循环轮播
     * 注意：只有当itemCount>3时，该函数才会调整childView的位置
     */
    fun prevCarouselLayout() {
        val isLast = isLastPage()
        val oldPageIndex = mCurContainerPageIndex
        val newPageIndex = mCurContainerPageIndex - 1
        mCurContainerPageIndex--
        if (isLast || isFirstPage()) {
            onPageChanged(oldPageIndex, newPageIndex)
            return
        }

        // itemCount >= 3（必要不充分条件）时才会执行以下代码
        val temp = getNextPage()!!         // 移除最底层的nextPage，插入到最顶层，即prevPage上面
        removeView(temp)
        val prevPageIndex = mCurContainerPageIndex - 1
        if (prevPageIndex >= 1) {
            // viewtype不同，得从缓存中获取，或者创建新的
            mPageCache.recycleAttachedView(temp)
            val holder = mPageCache.getViewHolder(prevPageIndex - 1)
            addView(holder.itemView, 2)
            mPageCache.attachView(holder)
        } else {
            addView(temp, 2)
        }
        onPageChanged(oldPageIndex, newPageIndex)
    }

    /**
     * 是否有上一页
     */
    fun hasPrevPage(): Boolean = mCurContainerPageIndex > 1

    /**
     * 是否有下一页
     */
    fun hasNextPage(): Boolean = mCurContainerPageIndex < mViewHolderAdapter.getItemCount()

    /**
     * 翻向下一页：该函数内部会检查是否有下一页
     * @return 是否翻页成功，翻页成功与否受到是否有下一页以及翻页速度限制
     */
    fun flipToNextPage(limited: Boolean = true): Boolean {
        if (hasNextPage()) {
            return layoutManager.flipToNextPage(limited)
        }
        return false
    }

    /**
     * 翻向上一页：该函数内部会检查是否有上一页
     * @return 是否翻页成功，翻页成功与否受到是否有上一页以及翻页速度限制
     */
    fun flipToPrevPage(limited: Boolean = true): Boolean {
        if (hasPrevPage()) {
            return layoutManager.flipToPrevPage(limited)
        }
        return false
    }

    /**
     * PageContainer中的containerPage数量，实际上就是adapter中的item数量
     *
     * 当containerPageCount >= 3时，AbstractPageContainer.childCount为3；
     * 当containerPageCount == 2时，AbstractPageContainer.childCount为2；
     * 当containerPageCount == 1时，AbstractPageContainer.childCount为1；
     * 当containerPageCount == 0时，AbstractPageContainer.childCount为0；
     *
     */
    override fun getContainerPageCount(): Int = innerAdapter?.getItemCount() ?: 0

    override fun getCurContainerPageIndex(): Int = mCurContainerPageIndex

    override fun navigatePage(pageIndex: Int): Boolean {
        val pageCount = getContainerPageCount()
        if (pageIndex > pageCount || pageIndex <= 0) {
            return false
        }
        val oldPageIndex = mCurContainerPageIndex
        mCurContainerPageIndex = pageIndex
        innerAdapter!!.notifyDataSetChanged()
        onPageChanged(oldPageIndex, pageIndex)
        return true
    }

    override fun navigateToFirstPage(): Boolean = navigatePage(1)

    override fun navigateToLastPage(): Boolean = navigatePage(getContainerPageCount())

    override fun navigateToNextPage(): Boolean = flipToNextPage(false)

    override fun navigateToPrevPage(): Boolean = flipToPrevPage(false)

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        layoutManager.dispatchDraw(canvas)
    }

    /**
     * 当前页码为最后一页
     */
    fun isLastPage() = mCurContainerPageIndex >= getContainerPageCount()

    /**
     * 当前页码为第一页
     */
    fun isFirstPage() = mCurContainerPageIndex <= 1


    fun getPrevPage(): View? {
        // itemCount <= 1，必然没有prevPage
        if (getContainerPageCount() <= 1) return null
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
        if (getContainerPageCount() == 0) return null
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
        if (getContainerPageCount() <= 1) return null
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
        if (getContainerPageCount() >= 2) {
            if (isLastPage()) {
                if (getContainerPageCount() >= 3) {
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
        if (getContainerPageCount() >= 2) {
            if (isFirstPage()) {
                if (getContainerPageCount() >= 3) {
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

    interface OnPageChangeListener {

        /**
         * 当页面发生切换时回调。
         *
         * @param oldPageIndex 原来的页码（从 1 开始）
         * @param newPageIndex 当前的新页码（从 1 开始）
         */
        fun onPageChanged(
            @IntRange(from = 1) oldPageIndex: Int,
            @IntRange(from = 1) newPageIndex: Int
        )

    }

    /**
     * 设置点击区域事件回调
     */
    fun setOnClickRegionListener(listener: OnClickRegionListener) {
        this.mOnClickRegionListener = listener
    }

    /**
     * 设置点击区域事件回调
     */
    fun setOnClickRegionListener(listener: (xPercent: Int, yPercent: Int) -> Boolean) {
        this.mOnClickRegionListener = object : OnClickRegionListener {
            override fun onClickRegion(xPercent: Int, yPercent: Int): Boolean = listener(xPercent, yPercent)
        }
    }

    /**
     * 设置翻页回调
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener) {
        this.mOnPageChangeListener = listener
    }

    /**
     * 设置翻页回调
     */
    fun setOnPageChangeListener(listener: (oldPageIndex: Int, newPageIndex: Int) -> Unit) {
        this.mOnPageChangeListener = object : OnPageChangeListener {
            override fun onPageChanged(@IntRange(from = 1) oldPageIndex: Int, @IntRange(from = 1) newPageIndex: Int)
            = listener(oldPageIndex, newPageIndex)
        }
    }

    /**
     * 设置cached page最大数量
     */
    fun setMaxCachedPageNum(num: Int) {
        mPageCache.maxCachedPage = num
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
        savedState.maxCachedPage = this.mPageCache.maxCachedPage
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
        var maxCachedPage: Int = 0


        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            scaledTouchSlop = parcel.readInt()
            flipTouchSlop = parcel.readInt()
            maxCachedPage = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(scaledTouchSlop)
            out.writeInt(flipTouchSlop)
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