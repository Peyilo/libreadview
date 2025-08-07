package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.IntRange
import org.peyilo.libreadview.data.novel.BookData
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.loader.BookLoader
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.loader.SimpleTextLoader
import org.peyilo.libreadview.parser.ContentParser
import org.peyilo.libreadview.parser.ReadChapter
import org.peyilo.libreadview.parser.SimpleContentParser
import org.peyilo.libreadview.provider.PageContentProvider
import org.peyilo.libreadview.provider.SimlpePageContentProvider
import org.peyilo.libreadview.ui.ChapLoadPage
import org.peyilo.libreadview.ui.MessagePage
import org.peyilo.libreadview.ui.ReadPage
import org.peyilo.libreadview.utils.LogHelper
import java.io.File
import kotlin.math.max

class SimpleReadView(
    context: Context, attrs: AttributeSet? = null
): AbstractReadView(context, attrs) {

    private val mAdapterData: AdapterData

    companion object {
        private const val TAG = "SimpleReadView"
    }

    private var mBookData: BookData? = null

    private val mReadConfig = ReadConfig()

    private lateinit var mBookLoader: BookLoader
    private lateinit var mContentParser: ContentParser
    private lateinit var mPageContentProvider: PageContentProvider
    private val mReadChapterTable = mutableMapOf<Int, ReadChapter>()

    private var callback: Callback? = null

    init {
        mAdapterData = AdapterData()
        adapter = PageAdapter()

        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // onPreDraw在main线程运行
                // 获取ReadContent的宽高，用于分页
                val dimenPair = measureContentView()
                mReadConfig.initContentDimen(dimenPair.first, dimenPair.second)
                LogHelper.d(TAG, "onPreDraw: contentDimemsion = $dimenPair")
                LogHelper.d(TAG, "onPreDraw: readview.width = $width, readview.height = $height")
                viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    private fun initToc(): Boolean {
        val res = try {
            mBookData = mBookLoader.initToc()
            onInitTocSuccess(mBookData!!.chapCount)
            true
        } catch (e: Exception) {
            LogHelper.e(TAG, "initToc: ${e.stackTrace}")
            false
        }
        callback?.onInitToc(res)
        return res
    }

    /**
     * 多个线程调用该函数加载相同章节时，会触发竞态条件，因而需要对该章节的状态进行同步
     * 只加载没有处于Unload状态下的章节
     * 这个函数是线程安全的
     */
    override fun loadChap(@IntRange(from = 1) chapIndex: Int): Boolean = loadChapWithLock(chapIndex) {
        try {
            val chapData = mBookData!!.getChap(chapIndex)
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
        val readPage = ReadPage(context)
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
        val measuredWidth = readPage.content.measuredWidth
        val measuredHeight = readPage.content.measuredHeight

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
                mReadConfig.waitForInitialized()
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
                }

            } else {
                showMessagePage("章节加载失败......")
            }
        } else {
            showMessagePage("目录加载失败......")
        }
    }


    override fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int,
        @IntRange(from = 1) pageIndex: Int
    ) {
        this.mBookLoader = loader
        this.mContentParser = SimpleContentParser()
        this.mPageContentProvider = SimlpePageContentProvider(this.mReadConfig)

        // 进行目录初始化准备工作，如：显示“加载目录中”视图
        showMessagePage("加载目录中......")

        initBook(chapIndex, pageIndex)
    }

    fun openFile(
        file: File,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    ) {
        openBook(SimpleNativeLoader(file), chapIndex, pageIndex)
    }

    fun showText(text: String) {
        openBook(SimpleTextLoader(text))
    }

    private fun showMessagePage(text: String) = post {
        mAdapterData.clear()       // 清除全部pages
        mAdapterData.add(PageType.MESSAGE_PAGE, text)
        adapter.notifyDataSetChanged()
    }

    /**
     * 当目录完成初始化以后，就会调用这个函数
     */
    private fun showAllChapLoadPage() = post {
        mAdapterData.clear()
        for (i in 1..mBookData!!.chapCount) {
            val chap = mBookData!!.getChap(i)
            mAdapterData.add(PageType.CHAP_LOAD_PAGE, listOf(chap.title ?: "", i))
            updateChapPageCount(i, 1)
        }
        adapter.notifyDataSetChanged()
    }



    private enum class PageType {
        MESSAGE_PAGE, CHAP_LOAD_PAGE, READ_PAGE,
    }

    private inner class PageAdapter: Adapter<PageAdapter.PageViewHolder>() {

        inner class PageViewHolder(page: View) : ViewHolder(page)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page: View = when (viewType) {
                PageType.MESSAGE_PAGE.ordinal -> {
                    MessagePage(parent.context)
                }
                PageType.CHAP_LOAD_PAGE.ordinal -> {
                    ChapLoadPage(parent.context)
                }
                PageType.READ_PAGE.ordinal -> {
                    ReadPage(parent.context)
                }
                else -> throw IllegalStateException("Unknown page type: $viewType")
            }
            return PageViewHolder(page)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val type = mAdapterData.getPageType(position)
            when (type) {
                PageType.MESSAGE_PAGE -> {
                    val page = holder.itemView as MessagePage
                    val text = mAdapterData.getPageContent(position) as String
                    page.text = text
                }
                PageType.CHAP_LOAD_PAGE -> {
                    val page = holder.itemView as ChapLoadPage
                    val list = mAdapterData.getPageContent(position) as List<*>
                    page.title = list[0] as String
                    page.chapIndex = list[1] as Int
                }
                PageType.READ_PAGE -> {
                    val page = holder.itemView as ReadPage
                    val pageData = mAdapterData.getPageContent(position) as PageData
                    val indexPair = findChapByPosition(position)
                    page.header.text = mBookData!!.getChap(indexPair.first).title
                    page.progress.text = "${indexPair.second}/${getChapPageCount(indexPair.first)}"
                    page.content.setContent(pageData)
                    page.content.provider = mPageContentProvider

                    LogHelper.d(TAG, "onBindViewHolder: ReadPage $indexPair, ${pageData.pageIndex}, ${page.header.text}, ${page.progress.text}")
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return mAdapterData.getPageType(position).ordinal
        }

        override fun getItemCount(): Int = mAdapterData.size
    }

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
        this.callback = callback
    }

    override fun getChapTitle(chapIndex: Int): String? {
        return mBookData?.getChap(chapIndex)?.title
    }

    interface Callback {
        fun onInitToc(success: Boolean) = Unit
    }
}