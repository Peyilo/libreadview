package org.peyilo.libreadview.simple


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.IntRange
import org.peyilo.libreadview.AbstractReadView
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.loader.BookLoader
import org.peyilo.libreadview.loader.TextLoader
import org.peyilo.libreadview.loader.TxtFileLoader
import org.peyilo.libreadview.parser.ContentParser
import org.peyilo.libreadview.parser.DefaultContentParser
import org.peyilo.libreadview.parser.ReadChapter
import org.peyilo.libreadview.provider.DefaultPageContentProvider
import org.peyilo.libreadview.provider.PageContentProvider
import org.peyilo.libreadview.simple.page.ChapLoadPage
import org.peyilo.libreadview.simple.page.ContentMetrics
import org.peyilo.libreadview.simple.page.MessagePage
import org.peyilo.libreadview.simple.page.ReadPage
import org.peyilo.libreadview.util.DisplayUtil
import org.peyilo.libreadview.util.LogHelper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class SimpleReadView(
    context: Context, attrs: AttributeSet? = null
): AbstractReadView(context, attrs) {

    private val mAdapterData: AdapterData

    companion object {
        private const val TAG = "SimpleReadView"

        private const val BOOK_STATUS_INIT = 0
        private const val BOOK_STATUS_PAGINATING = 1
        private const val BOOK_STATUS_READY = 2
    }

    private var bookStatus = AtomicInteger(BOOK_STATUS_INIT)

    /**
     * 不要主动更改book的数据，只能通过openBook()函数
     */
    var book: Book? = null
        private set

    internal val mReadStyle = ReadStyle(context)

    private lateinit var mBookLoader: BookLoader
    private lateinit var mContentParser: ContentParser
    private lateinit var mPageContentProvider: PageContentProvider
    private val mReadChapterTable = mutableMapOf<Int, ReadChapter>()

    private var mPageDelegate: PageDelegate? = null
    private val mDefaultPageDelegate: PageDelegate by lazy { PageDelegate() }

    private var mCallback: Callback? = null

    init {
        mAdapterData = AdapterData()
        adapter = PageAdapter()

        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // onPreDraw在main线程运行
                // 获取ReadContent的宽高，用于分页
                val dimenPair = measureContentView()
                mReadStyle.initContentDimen(dimenPair.first, dimenPair.second)
                LogHelper.d(TAG, "onPreDraw: contentDimemsion = $dimenPair")
                LogHelper.d(TAG, "onPreDraw: readview.width = $width, readview.height = $height")
                viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    private fun initToc(): Boolean {
        val res = try {
            book = mBookLoader.initToc()
            onInitTocSuccess(book!!.chapCount)
            true
        } catch (e: Exception) {
            LogHelper.e(TAG, "initToc: ${e.stackTrace}")
            false
        }
        mCallback?.onInitTocResult(res)
        return res
    }

    /**
     * 多个线程调用该函数加载相同章节时，会触发竞态条件，因而需要对该章节的状态进行同步
     * 只加载没有处于Unload状态下的章节
     * 这个函数是线程安全的
     */
    override fun loadChap(@IntRange(from = 1) chapIndex: Int): Boolean = loadChapWithLock(chapIndex) {
        try {
            val chapData = book!!.getChap(chapIndex - 1)
            mBookLoader.loadChap(chapData)
            mReadChapterTable[chapIndex] = mContentParser.parse(chapData)    // 解析ChapData
            LogHelper.d(TAG, "loadChap: $chapIndex")
            return@loadChapWithLock true
        } catch (e: Exception) {        // 加载失败
            LogHelper.d(TAG, "loadChap: ${e.stackTrace}")
        }
        return@loadChapWithLock false
    }

    /**
     * 分割指定章节，分割结果保存在mReadChapterTable中
     * 线程安全的
     */
    override fun splitChap(@IntRange(from = 1) chapIndex: Int): Boolean = splitChapWithLock(chapIndex) {
        val readChapter = mReadChapterTable[chapIndex]!!
        mPageContentProvider.split(readChapter)
        LogHelper.d(TAG, "splitChap: $chapIndex")
        return@splitChapWithLock true
    }

    /**
     * 在章节加载并分页完成以后，可以调用该函数将分割完的pages填充到adapterData中
     * 只能在主线程调用
     */
    override fun inflateChap(@IntRange(from = 1) chapIndex: Int): Boolean = inflateChapWithLock(chapIndex) {
        val chapRange = getChapPageRange(chapIndex)
        assert(chapRange.size == 1 && mAdapterData.getPageType(chapRange.from) == PageType.CHAP_LOAD_PAGE)
        this@SimpleReadView.mAdapterData.removeAt(chapRange.from)
        var pagesSize = 0
        if (!mReadChapterTable.containsKey(chapIndex)) {
            throw IllegalStateException("The chapter $chapIndex is not loaded.")
        }
        mReadChapterTable[chapIndex]!!.apply {
            this@SimpleReadView.mAdapterData.insert(
                chapRange.from, PageType.READ_PAGE,
                pages
            )
            pagesSize = pages.size
        }
        updateChapPageCount(chapIndex, pagesSize)
        adapter.notifyItemRangeReplaced(chapRange.from, 1, pagesSize)
        LogHelper.d(TAG, "inflateChap: $chapIndex")
        return@inflateChapWithLock true
    }

    /**
     * 在不addview的情况下，获取ReadPage.content的宽高，以便于进行分页。
     * 注意：这个函数要在ReadView测量完成后调用才可以获取正确的宽高
     */
    private fun measureContentView(): Pair<Int, Int> {
        val contentMetrics = mPageDelegate?.createReadPage(context)
            ?: mDefaultPageDelegate.createReadPage(context)
        if (contentMetrics !is View) {
            throw IllegalStateException("The readpage must be a view implemented the ContentMetrics interface.")
        }
        val createdReadPage = contentMetrics as View
        val widthSize = width
        val heightSize = height

        // 测量子View，每个子View都和其父View一样大
        // 父容器的内边距
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom

        // 可用空间（扣除 padding）
        val availableWidth = widthSize - paddingLeft - paddingRight
        val availableHeight = heightSize - paddingTop - paddingBottom

        var childWidth = availableWidth
        var childHeight = availableHeight
        // 子 View 的测量尺寸 = 可用空间 - margin
        val lp = createdReadPage.layoutParams
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
        createdReadPage.measure(childWidthSpec, childHeightSpec)

        // 获取测量后的宽高
        val measuredWidth = contentMetrics.getContentWidth()
        val measuredHeight = contentMetrics.getContentHeight()

        // 返回子视图的测量宽高
        return Pair(measuredWidth, measuredHeight)
    }

    /**
     * 初始化目录，并且预加载并分割章节
     * @param pageIndex page在指定的章节中的位置
     */
    private fun initBook(chapIndex: Int, pageIndex: Int) = startTask {
        val initTocRes = initToc()                     // initToc()是一个耗时任务，不能在主线程执行
        if (initTocRes) {
            // 目录初始化已经完成，接下来要开始加载章节内容，可以先将“加载目录中”视图清除，替换为“章节xxx加载中”视图
            // 由于涉及UI更新，需要在主线程执行
            initContainerPageIndex(chapIndex)
            showAllChapLoadPage()
            LogHelper.d(TAG, "initBook: showAllChapLoadPage() curContainerPageIndex=${getCurContainerPageIndex()}")
            val loadChapRes = loadNearbyChapters(chapIndex)
            if (loadChapRes) {
                // 等待视图宽高数据，用来分页
                // 等待视图布局完成，然后获取视图的宽高
                mReadStyle.waitForInitialized()
                bookStatus.set(BOOK_STATUS_PAGINATING)
                splitNearbyChapters(chapIndex)
                post {
                    val chapRange = getChapPageRange(chapIndex)
                    val needJumpPage = getCurContainerPageIndex() - 1 == chapRange.from
                    inflateNearbyChapters(chapIndex)
                    // 如果在目录完成初始化之后，章节内容加载之前，滑动了页面，这就会造成pageIndex改变
                    // 这样也就没必要，跳转到指定pageIndex了
                    LogHelper.d(TAG, "initBook: needJumpPage = $needJumpPage, curContainerPageIndex = ${getCurContainerPageIndex()}, chapRange = $chapRange")
                    if (needJumpPage) {
                        navigateBook(chapIndex, pageIndex)
                    }
                    bookStatus.set(BOOK_STATUS_READY)
                }

            } else {
                showMessagePage("章节加载失败......")
            }
        } else {
            showMessagePage("目录加载失败......")
        }
    }

    /**
     * 在Activity.onCreate调用时尽量在readview相关操作中最后调用，否则可能会出现设置的参数不起作用
     */
    override fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int,
        @IntRange(from = 1) pageIndex: Int
    ) {
        this.mBookLoader = loader
        this.mContentParser = DefaultContentParser()
        this.mPageContentProvider = DefaultPageContentProvider(this.mReadStyle)

        // 进行目录初始化准备工作，如：显示“加载目录中”视图
        showMessagePage("加载目录中......")

        initBook(chapIndex, pageIndex)
    }

    fun openFile(
        file: File,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    ) {
        openBook(TxtFileLoader(file), chapIndex, pageIndex)
    }

    fun showText(text: String) {
        openBook(TextLoader(text))
    }

    private fun showMessagePage(text: String) = post {
        mAdapterData.clear()       // 清除全部pages
        mAdapterData.add(PageType.TOC_INIT_PAGE, text)
        adapter.notifyDataSetChanged()
    }

    /**
     * 当目录完成初始化以后，就会调用这个函数
     */
    private fun showAllChapLoadPage(delayed: Boolean = true) {
        val task = Runnable {
            mAdapterData.clear()
            for (i in 1..book!!.chapCount) {
                mAdapterData.add(PageType.CHAP_LOAD_PAGE, listOf(getChapTitle(i), i))
                updateChapPageCount(i, 1)
            }
            adapter.notifyDataSetChanged()
        }
        if (delayed) {
            post { task.run() }
        } else {
            task.run()
        }
    }

    override fun setPageBackground(drawable: Drawable) {
        mReadStyle.mPageBackground = drawable
        // 设置背景后，刷新当前页面
        traverseAllCreatedPages {
            it.background = mReadStyle.mPageBackground
        }
    }

    /**
     * page创建完成以后调用这个函数，可以在这里对page进行一些初始化操作
     */
    private fun onPageCreated(page: View, viewType: Int) {
        page.background = mReadStyle.mPageBackground
        if (viewType == PageType.READ_PAGE.ordinal) {
            val readPage = page as ReadPage
            readPage.header.setTextColor(mReadStyle.mHeaderAndFooterTextColor)
            readPage.progress.setTextColor(mReadStyle.mHeaderAndFooterTextColor)
            readPage.clock.setTextColor(mReadStyle.mHeaderAndFooterTextColor)
        }
    }

    enum class PageType {
        TOC_INIT_PAGE, CHAP_LOAD_PAGE, READ_PAGE,
    }

    private inner class PageAdapter: Adapter<PageAdapter.PageViewHolder>() {

        inner class PageViewHolder(page: View) : ViewHolder(page)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page: View = when (viewType) {
                PageType.TOC_INIT_PAGE.ordinal -> {
                    mPageDelegate?.createTocInitPage(parent.context)
                        ?: mDefaultPageDelegate.createTocInitPage(parent.context)
                }
                PageType.CHAP_LOAD_PAGE.ordinal -> {
                    mPageDelegate?.createChapLoadPage(parent.context)
                        ?: mDefaultPageDelegate.createChapLoadPage(parent.context)
                }
                PageType.READ_PAGE.ordinal -> {
                    val createdPage = mPageDelegate?.createReadPage(parent.context)
                        ?: mDefaultPageDelegate.createReadPage(parent.context)
                    if (createdPage !is View) {
                        throw IllegalStateException("The readpage must be a view implemented the ContentMetrics interface.")
                    }
                    createdPage as View
                }
                else -> throw IllegalStateException("Unknown page type: $viewType")
            }
            onPageCreated(page, viewType)
            return PageViewHolder(page)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val type = mAdapterData.getPageType(position)
            when (type) {
                PageType.TOC_INIT_PAGE -> {
                    val text = mAdapterData.getPageContent(position) as String
                    mPageDelegate?.bindTocInitPage(holder.itemView, text)
                        ?: mDefaultPageDelegate.bindTocInitPage(holder.itemView, text)
                }
                PageType.CHAP_LOAD_PAGE -> {
                    val list = mAdapterData.getPageContent(position) as List<*>
                    val title = list[0] as String
                    val chapIndex = list[1] as Int
                    mPageDelegate?.bindChapLoadPage(holder.itemView, title, chapIndex)
                        ?: mDefaultPageDelegate.bindChapLoadPage(holder.itemView, title, chapIndex)
                }
                PageType.READ_PAGE -> {
                    val page = holder.itemView as ReadPage
                    val pageData = mAdapterData.getPageContent(position) as PageData
                    val indexPair = findChapByPosition(position)
                    val title = getChapTitle(indexPair.first)
                    mPageDelegate?.bindReadPage(holder.itemView, pageData, title,
                        indexPair.first, indexPair.second,
                        getChapPageCount(indexPair.first), mPageContentProvider)
                        ?: mDefaultPageDelegate.bindReadPage(holder.itemView, pageData, title,
                            indexPair.first, indexPair.second,
                            getChapPageCount(indexPair.first), mPageContentProvider)

                    LogHelper.d(TAG, "onBindViewHolder: ReadPage $indexPair, ${pageData.pageIndex}, ${page.header.text}, ${page.progress.text}")
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return mAdapterData.getPageType(position).ordinal
        }

        override fun getItemCount(): Int = mAdapterData.size
    }

    /**
     * 用于保存SimpleReadView.adapter的数据
     */
    private class AdapterData {

        private val pages = mutableListOf<Pair<PageType, Any>>()

        val size get() =  pages.size

        fun clear() = pages.clear()

        fun add(pageType: PageType, pageContent: Any) {
            pages.add(Pair(pageType, pageContent))
        }

        fun getPageType(i: Int) = pages[i].first

        fun getPageContent(i: Int) = pages[i].second

        fun insert(i: Int, elements: List<Pair<PageType, Any>>) {
            pages.addAll(i, elements)
        }

        fun insert(i: Int, pageType: PageType, elements: List<*>) {
            val temp = mutableListOf<Pair<PageType, Any>>()
            elements.forEach {
                temp.add(Pair(pageType, it!!))
            }
            pages.addAll(i, temp)
        }

        fun removeAt(i: Int): Pair<PageType, Any> = pages.removeAt(i)

    }

    fun setCallback(callback: Callback) {
        this.mCallback = callback
    }

    override fun getChapTitle(chapIndex: Int): String {
        if (book == null) {
            throw IllegalStateException("the mBook is not initialized")
        }
        return book!!.getChap(chapIndex - 1).title
    }

    interface Callback {
        /**
         * 当目录完成初始化，就会调用这个函数
         */
        fun onInitTocResult(success: Boolean) = Unit

    }

    /**
     * 如果需要自定义TocInitPage、ChapLoadPage、ReadPage，可以通过重写这个类并设置setPageDelegate(
     * pageDelegate: PageDelegate)以达到一个自定义的效果
     */
    open class PageDelegate {

        open fun createTocInitPage(context: Context): View = MessagePage(context)

        open fun createChapLoadPage(context: Context): View = ChapLoadPage(context)

        /**
         * 创建的ReadView必须是一个实现了ContentMetrics接口的View
         */
        open fun createReadPage(context: Context): ContentMetrics {
            val res = ReadPage(context)
            return res
        }

        open fun bindTocInitPage(page: View, text: String) {
            val page = page as MessagePage
            page.text = text
        }

        open fun bindChapLoadPage(page: View, title: String, chapIndex: Int) {
            val page = page as ChapLoadPage
            page.title = title
            page.chapIndex = chapIndex
        }

        @SuppressLint("SetTextI18n")
        open fun bindReadPage(page: ContentMetrics, pageData: PageData, title: String, chapIndex: Int,
                              chapPageIndex: Int, chapPageCount: Int,
                              provider: PageContentProvider
        ) {
            val page = page as ReadPage
            page.header.text = title
            page.progress.text = "${chapPageIndex}/${chapPageCount}"
            page.content.setContent(pageData)
            page.content.provider = provider
        }

    }

    fun setPageDelegate(pageDelegate: PageDelegate) {
        this.mPageDelegate = pageDelegate
    }

    /**
     * 获取章节正文文字大小（单位：sp）
     */
    fun getContentTextSize(): Float = DisplayUtil.pxToSp(context, mReadStyle.contentTextSize)

    /**
     * 获取章节标题文字大小
     */
    fun getTitleTextSize(): Float = DisplayUtil.pxToSp(context, mReadStyle.titleTextSize)

    /**
     * 获取章节正文文字颜色
     */
    fun getContentTextColor(): Int = mReadStyle.contentTextColor

    /**
     * 获取章节标题文字颜色
     */
    fun getTitleTextColor(): Int = mReadStyle.titleTextColor

    /**
     * 获取页面的左边距
     */
    fun getPagePaddingLeft() = DisplayUtil.pxToDp(context, mReadStyle.paddingLeft)

    /**
     * 获取页面的右边距
     */
    fun getPagePaddingRight() = DisplayUtil.pxToDp(context, mReadStyle.paddingRight)

    /**
     * 获取页面的上边距
     */
    fun getPagePaddingTop() = DisplayUtil.pxToDp(context, mReadStyle.paddingTop)

    /**
     * 获取页面的下边距
     */
    fun getPagePaddingBottom() = DisplayUtil.pxToDp(context, mReadStyle.paddingBottom)

    /**
     * 获取段落首行缩进
     */
    fun getFirstParaIndent() = DisplayUtil.pxToDp(context, mReadStyle.firstParaIndent)

    /**
     * 获取标题与正文的间距
     */
    fun getTitleMargin() = DisplayUtil.pxToDp(context, mReadStyle.titleMargin)

    /**
     * 获取正文文字的边距
     */
    fun getTextMargin() = DisplayUtil.pxToDp(context, mReadStyle.contentTextMargin)

    /**
     * 获取正文文字的行间距
     */
    fun getLineMargin() = DisplayUtil.pxToDp(context, mReadStyle.lineMargin)

    /**
     * 获取段落间距
     */
    fun getParaMargin() = DisplayUtil.pxToDp(context, mReadStyle.paraMargin)


    /**
     * 当影响分页的参数发生变化时，调用这个函数，并在runnable中更改参数,
     * 这个函数会在runnable.run()前后做一些必要的处理
     */
    internal fun onReadviewLayoutInvalidated(runnable: Runnable) {
        val bookStatus = bookStatus.get()
        if (bookStatus == BOOK_STATUS_PAGINATING) {
            throw IllegalStateException("The book is paginating now, please try again later.")
        }
        if (bookStatus == BOOK_STATUS_INIT) {
            runnable.run()
        } else {
            // bookStatus == BOOK_STATUS_READY
            val curChapIndex = getCurChapIndex()
            val curChapPageIndex = getCurChapPageIndex()
            val oldChapPageCount = getChapPageCount(curChapIndex)
            navigatePage(curChapIndex)
            showAllChapLoadPage(delayed = false)
            runnable.run()

            // 设置文字大小后，刷新当前页面
            invalidateSplittedChapters()
            splitNearbyChapters(curChapIndex)
            val chapRange = getChapPageRange(curChapIndex)
            val needJumpPage = getCurContainerPageIndex() - 1 == chapRange.from
            inflateNearbyChapters(curChapIndex)
            val newChapPageCount = getChapPageCount(curChapIndex)

            if (needJumpPage) {
                val targetChapPageIndex = mapChapPageIndex(curChapPageIndex, oldChapPageCount, newChapPageCount)
                navigateBook(curChapIndex, targetChapPageIndex)
            }
        }
    }

    /**
     * 由于字体大小、行间距等参数的改变，可能会导致章节页数发生变化；
     * 这个函数用于将旧的chapPageIndex映射到新的chapPageIndex
     * @param chapPageIndex 旧的章节页码，范围1..oldChapPageCount
     * @param oldChapPageCount 旧的章节总页数，必须大于0
     * @param newChapPageCount 新的章节总页数，必须大于0
     * @return 映射后的新的章节页码，范围1..newChapPageCount
     */
    private fun mapChapPageIndex(chapPageIndex: Int, oldChapPageCount: Int, newChapPageCount: Int): Int {
        require(chapPageIndex in 1..oldChapPageCount) {
            "chapPageIndex 必须在 1..$oldChapPageCount 范围内"
        }
        if (oldChapPageCount <= 1) return 1 // 避免除零，只有一页就始终映射为 1

        return 1 + (chapPageIndex - 1) * (newChapPageCount - 1) / (oldChapPageCount - 1)
    }

    /**
     * 设置页面的边距 (请在ui线程调用)
     */
    fun setPagePadding(left: Float, top: Float, right: Float, bottom: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingLeft = DisplayUtil.dpToPx(context, left)
        mReadStyle.paddingTop = DisplayUtil.dpToPx(context, top)
        mReadStyle.paddingRight = DisplayUtil.dpToPx(context, right)
        mReadStyle.paddingBottom = DisplayUtil.dpToPx(context, bottom)
    }

    /**
     * 设置页面的水平边距 (请在ui线程调用)
     */
    fun setPageHorizontalPadding(left: Float, right: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingLeft = DisplayUtil.dpToPx(context, left)
        mReadStyle.paddingRight = DisplayUtil.dpToPx(context, right)
    }

    /**
     * 设置页面的垂直边距 (请在ui线程调用)
     */
    fun setPageVerticalPadding(top: Float, bottom: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingTop = DisplayUtil.dpToPx(context, top)
        mReadStyle.paddingBottom = DisplayUtil.dpToPx(context, bottom)
    }

    /**
     * 设置页面的左边距
     */
    fun setPageLeftPadding(left: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingLeft = DisplayUtil.dpToPx(context, left)
    }

    /**
     * 设置页面的右边距
     */
    fun setPageRightPadding(right: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingRight = DisplayUtil.dpToPx(context, right)
    }

    /**
     * 设置页面的上边距
     */
    fun setPageTopPadding(top: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingTop = DisplayUtil.dpToPx(context, top)
    }

    /**
     * 设置页面的下边距
     */
    fun setPageBottomPadding(bottom: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paddingBottom = DisplayUtil.dpToPx(context, bottom)
    }

    /**
     * 设置段落首行缩进 (请在ui线程调用)
     */
    fun setFirstParaIndent(indent: Float) = onReadviewLayoutInvalidated {
        mReadStyle.firstParaIndent = indent
    }

    /**
     * 设置标题与正文的间距 (请在ui线程调用)
     */
    fun setTitleMargin(margin: Float) = onReadviewLayoutInvalidated {
        mReadStyle.titleMargin = DisplayUtil.dpToPx(context, margin)
    }

    /**
     * 设置正文文字的边距 (请在ui线程调用)
     */
    fun setContentTextMargin(margin: Float) = onReadviewLayoutInvalidated {
        mReadStyle.contentTextMargin = DisplayUtil.dpToPx(context, margin)
    }

    /**
     * 设置标题文字间距 (请在ui线程调用)
     */
    fun setTitleTextMargin(margin: Float) = onReadviewLayoutInvalidated {
        mReadStyle.titleTextMargin = DisplayUtil.dpToPx(context, margin)
    }

    /**
     * 设置行间距 (请在ui线程调用)
     */
    fun setLineMargin(margin: Float) = onReadviewLayoutInvalidated {
        mReadStyle.lineMargin = DisplayUtil.dpToPx(context, margin)
    }

    /**
     * 设置段落间距 (请在ui线程调用)
     */
    fun setParaMargin(margin: Float) = onReadviewLayoutInvalidated {
        mReadStyle.paraMargin = DisplayUtil.dpToPx(context, margin)
    }

    /**
     * 设置章节标题文字大小 (请在ui线程调用)
     */
    fun setTitleTextSize(size: Float) = onReadviewLayoutInvalidated {
        mReadStyle.titlePaint.textSize = DisplayUtil.spToPx(context, size)
    }

    /**
     * 设置章节正文文字大小 (请在ui线程调用)
     */
    fun setContentTextSize(size: Float) = onReadviewLayoutInvalidated {
        mReadStyle.contentPaint.textSize = DisplayUtil.spToPx(context, size)
    }

    /**
     * 设置章节标题文字字体 (请在ui线程调用)
     */
    fun setTitleTypeface(typeface: Typeface) = onReadviewLayoutInvalidated {
        mReadStyle.titlePaint.typeface = typeface
    }

    /**
     * 设置章节正文文字字体 (请在ui线程调用)
     */
    fun setContentTypeface(typeface: Typeface) = onReadviewLayoutInvalidated {
        mReadStyle.contentPaint.typeface = typeface
    }

    /**
     * 设置章节标题文字颜色
     */
    fun setTitleTextColor(color: Int) {
        mReadStyle.titlePaint.color = color
        // 设置文字颜色后，刷新当前页面
        traverseAllCreatedPages {
            if (it is ReadPage) {
                it.content.invalidate()
            }
        }
    }

    /**
     * 设置章节正文文字颜色
     */
    fun setContentTextColor(color: Int) {
        mReadStyle.contentPaint.color = color
        // 设置文字颜色后，刷新当前页面
        traverseAllCreatedPages {
            if (it is ReadPage) {
                it.content.invalidate()
            }
        }
    }

    /**
     * 设置页眉和页脚文字颜色
     */
    fun setHeaderAndFooterTextColor(color: Int) {
        // 设置文字颜色后，刷新当前页面
        mReadStyle.mHeaderAndFooterTextColor = color
        traverseAllCreatedPages {
            if (it is ReadPage) {
                it.header.setTextColor(color)
                it.progress.setTextColor(color)
                it.clock.setTextColor(color)
            }
        }
    }

}