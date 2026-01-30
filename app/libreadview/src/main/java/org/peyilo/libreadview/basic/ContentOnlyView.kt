package org.peyilo.libreadview.basic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.IntRange
import org.peyilo.libreadview.AbstractReadView
import org.peyilo.libreadview.basic.page.ChapLoadPage
import org.peyilo.libreadview.basic.page.ContentOnlyReadPage
import org.peyilo.libreadview.content.ContentParser
import org.peyilo.libreadview.content.DefaultContentParser
import org.peyilo.libreadview.content.ReadChapter
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.layout.DefaultPageContentProvider
import org.peyilo.libreadview.layout.PageContentProvider
import org.peyilo.libreadview.load.BookLoader
import org.peyilo.libreadview.load.TextLoader
import org.peyilo.libreadview.load.TxtFileLoader
import org.peyilo.libreadview.util.LogHelper
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

open class ContentOnlyView(
    context: Context, attrs: AttributeSet? = null
): AbstractReadView(context, attrs) {

    private val mAdapterData: AdapterData

    companion object {
        private const val TAG = "ContentOnlyView"

        private const val BOOK_STATUS_INIT = 0
        private const val BOOK_STATUS_PAGINATING = 1
        private const val BOOK_STATUS_READY = 2
    }

    private var bookStatus = AtomicInteger(BOOK_STATUS_INIT)

    /**
     * 尽量不要主动更改book的数据，把这个book当做只读数据来使用
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

        // 文本内容的分页依赖于当前View的宽高，因此需要等待view测量完成以后，
        // 获取到view的宽高，之后才能进行分页
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // onPreDraw在main线程运行
                // 获取ReadContent的宽高，用于分页
                val dimenPair = measureContentView()
                mReadStyle.initBodyDimen(dimenPair.first, dimenPair.second)
                LogHelper.d(TAG, "onPreDraw: contentDimemsion = $dimenPair")
                LogHelper.d(TAG, "onPreDraw: readview.width = $width, readview.height = $height")
                viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    /**
     * 初始化书籍目录信息
     */
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
        mPageContentProvider.paginate(readChapter)
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
        this@ContentOnlyView.mAdapterData.removeAt(chapRange.from)
        var pagesSize = 0
        if (!mReadChapterTable.containsKey(chapIndex)) {
            throw IllegalStateException("The chapter $chapIndex is not loaded.")
        }
        mReadChapterTable[chapIndex]!!.apply {
            this@ContentOnlyView.mAdapterData.insert(
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
        val readPage = mPageDelegate?.createReadPage(context)
            ?: mDefaultPageDelegate.createReadPage(context)
        onPageCreated(readPage)
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
        val lp = readPage.layoutParams
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
        readPage.measure(childWidthSpec, childHeightSpec)

        // 获取测量后的宽高
        val measuredWidth = readPage.getBodyWidth()
        val measuredHeight = readPage.getBodyHeight()

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
                    var needJumpPage = false
                    // initContainerPageIndex(chapIndex)将当前的page置位到章节的第一页，接下来需要跳转到指定的pageIndex
                    // 但是如果在跳转到指定pageIndex之前，用户滑动了页面，这样pageIndex就变了，就不需要跳转了
                    // 此外，如果需要跳转的pageIndex本身就是第一页，也无需再继续跳转
                    if (getCurChapIndex() == chapIndex && getCurChapPageIndex() == 1 && pageIndex != 1) {
                        needJumpPage = true
                    }
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
//                showMessagePage("章节加载失败......")
            }
        } else {
//            showMessagePage("目录加载失败......")
        }
    }

    /**
     * 打开一本书
     * 注意：在Activity.onCreate调用时尽量在readview相关操作中最后调用，否则可能会出现设置的参数不起作用
     *
     * @param loader  用于加载书籍内容的loader
     * @param chapIndex 章节索引，范围1..chapCount
     * @param pageIndex 章节页码，范围1..chapPageCount
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
//        showMessagePage("加载目录中......")

        initBook(chapIndex, pageIndex)
    }

    /**
     * 打开一个本地txt文件
     * @param file 必须是一个存在的文件
     * @param chapIndex 章节索引，范围1..chapCount
     * @param pageIndex 章节页码，范围1..chapPageCount
     * @param encoding 文本编码格式，如"UTF-8"
     */
    fun openFile(
        file: File,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1,
        encoding: String = "UTF-8",
    ) {
        openBook(
            TxtFileLoader(file, encoding = encoding),
            chapIndex, pageIndex
        )
    }

    /**
     * 打开一个assets目录下的txt文件，e.g. "txts/demo.txt"
     * @param assetFileName assets目录下的文件路径
     * @param chapIndex 章节索引，范围1..chapCount
     * @param pageIndex 章节页码，范围1..chapPageCount
     * @param encoding 文本编码格式，如"UTF-8"
     */
    fun openAssetFile(
        assetFileName: String,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1,
        encoding: String = "UTF-8",
    ) {
        val assetManager = context.assets
        val inputStream: InputStream = assetManager.open(assetFileName)
        val bookTitle = File(assetFileName).nameWithoutExtension
        openBook(
            TxtFileLoader(inputStream, bookTitle, encoding),
            chapIndex, pageIndex
        )
    }

    /**
     * 打开一段文本内容，并显示
     * @param text 要显示的文本内容
     */
    fun showText(text: String) {
        openBook(TextLoader(text))
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
    private fun onPageCreated(page: View) {
        mReadStyle.initPage(page)
    }

    enum class PageType {
        CHAP_LOAD_PAGE, READ_PAGE,
    }

    private inner class PageAdapter: Adapter<PageAdapter.PageViewHolder>() {

        inner class PageViewHolder(page: View) : ViewHolder(page)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page: View = when (viewType) {
                PageType.CHAP_LOAD_PAGE.ordinal -> {
                    mPageDelegate?.createChapLoadPage(parent.context)
                        ?: mDefaultPageDelegate.createChapLoadPage(parent.context)
                }
                PageType.READ_PAGE.ordinal -> {
                    val createdPage = mPageDelegate?.createReadPage(parent.context)
                        ?: mDefaultPageDelegate.createReadPage(parent.context)
                    createdPage as View
                }
                else -> throw IllegalStateException("Unknown page type: $viewType")
            }
            onPageCreated(page)
            return PageViewHolder(page)
        }

        // 根据position绑定数据到ViewHolder，不同position的type可能不同，
        // 这里有三种不同的PageType，需要分别处理
        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val type = mAdapterData.getPageType(position)
            when (type) {
                PageType.CHAP_LOAD_PAGE -> {
                    val list = mAdapterData.getPageContent(position) as List<*>
                    val title = list[0] as String
                    val chapIndex = list[1] as Int
                    mPageDelegate?.bindChapLoadPage(holder.itemView, title, chapIndex)
                        ?: mDefaultPageDelegate.bindChapLoadPage(holder.itemView, title, chapIndex)
                }
                PageType.READ_PAGE -> {
                    val page = holder.itemView as ContentOnlyReadPage
                    val pageData = mAdapterData.getPageContent(position) as PageData
                    // 如果是章节的第一页，就使用book的title作为页眉显示的标题
                    val pageDelegate = mPageDelegate ?: mDefaultPageDelegate
                    pageDelegate.bindReadPage(page, pageData, mPageContentProvider)
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return mAdapterData.getPageType(position).ordinal
        }

        override fun getItemCount(): Int = mAdapterData.size
    }

    /**
     * 用于保存ReadView.adapter的数据
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

    /**
     * 设置回调
     */
    fun setCallback(callback: Callback) {
        this.mCallback = callback
    }

    /**
     * 获取指定章节的标题 (chapIndex从1开始)
     */
    override fun getChapTitle(@IntRange(from = 1) chapIndex: Int): String {
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
     * 如果需要自定义ChapLoadPage、ReadPage，可以通过重写这个类并设置
     * setPageDelegate(pageDelegate: PageDelegate)以达到一个自定义的效果
     */
    open class PageDelegate {

        /**
         * 对于每个章节来说，在完成内容的加载以及分页之前，会使用一个占位page面来显示“章节xxx加载中”信息,
         * 直到章节内容加载并分页完成以后，才会将这个page替换为章节的正文内容页面
         */
        open fun createChapLoadPage(context: Context): View = ChapLoadPage(context)

        /**
         * 用于显示章节正文内容的View
         */
        open fun createReadPage(context: Context): ContentOnlyReadPage {
            val res = ContentOnlyReadPage(context)
            return res
        }

        // 绑定章节加载页面的数据
        open fun bindChapLoadPage(page: View, title: String, chapIndex: Int) {
            val page = page as ChapLoadPage
            page.title = title
            page.chapIndex = chapIndex
        }

        // 绑定章节正文页面的数据
        @SuppressLint("SetTextI18n")
        open fun bindReadPage(page: ContentOnlyReadPage, pageData: PageData,
                              provider: PageContentProvider
        ) {
            page.body.content = pageData
            page.body.provider = provider
        }

    }

    /**
     * 设置用于创建和绑定各类Page的委托对象，以实现对各类Page的自定义
     */
    fun setPageDelegate(pageDelegate: PageDelegate) {
        this.mPageDelegate = pageDelegate
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

}