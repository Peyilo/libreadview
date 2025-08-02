package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import java.io.File
import java.util.concurrent.Executors

class ReadView(
    context: Context, attrs: AttributeSet? = null
): PageContainer(context, attrs) {

    private val mAdapterData: Pages

    init {
        mAdapterData = Pages()
        adapter = PageAdapter()
    }

    companion object {
        private const val TAG = "ReadView"
    }


    /**
     * 当前章节序号
     */
    @IntRange(from = 1)
    private var mCurChapIndex = 1

    /**
     * 当前页在本章节中的序号
     */
    @IntRange(from = 1)
    private var mCurPageIndexInChap = 1

    /**
     * 预处理章节数：需要预处理当前章节之前的preprocessBefore个章节
     */
    private var mPreprocessBefore = 0
    /**
     * 预处理章节数：需要预处理当前章节之后的preprocessBehind个章节
     */
    private var mPreprocessBehind = 0

    private var mBookData: BookData? = null

    private val mReadConfig = ReadConfig()

    private lateinit var mBookLoader: BookLoader
    private lateinit var mContentParser: ContentParser
    private lateinit var mPageContentProvider: PageContentProvider

    private val mChapStatusTable = mutableMapOf<Int, ChapStatus>()
    private val mReadChapterTable = mutableMapOf<Int, ReadChapter>()
    private val mLocksForChap = mutableMapOf<Int, Any>()

    private val mThreadPool by lazy { Executors.newFixedThreadPool(10) }

    private var mAttachedToWindow = false


    private enum class ChapStatus {
        Unload,                 // 未加载
        Nonpaged,               // 未分页
        Finished                // 全部完成
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mAttachedToWindow = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAttachedToWindow = false
        mThreadPool.shutdownNow()        // 关闭正在执行的所有线程
    }

    private fun startTask(task: Runnable) {
        mThreadPool.submit(task)
    }

    private fun initToc(): Boolean {
        try {
            mBookData = mBookLoader.initToc()
            for (i in 1..mBookData!!.chapCount) {              // 初始化章节状态表
                mChapStatusTable[i] = ChapStatus.Unload
                mLocksForChap[i] = Any()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "initToc: ${e.stackTrace}")
            return false
        }
    }

    /**
     * 多个线程调用该函数加载相同章节时，会触发竞态条件，因而需要对该章节的状态进行同步
     */
    private fun loadChap(@IntRange(from = 1) chapIndex: Int): Boolean {
        synchronized(mLocksForChap[chapIndex]!!) {
            if (mChapStatusTable[chapIndex] == ChapStatus.Unload) {      // 未加载
                try {
                    val chapData = mBookData!!.getChap(chapIndex)
                    mBookLoader.loadChap(chapData)
                    mReadChapterTable[chapIndex] = mContentParser.parse(chapData)    // 解析ChapData
                    mChapStatusTable[chapIndex] = ChapStatus.Nonpaged        // 更新状态
                    Log.d(TAG, "loadChap: $chapIndex")
                } catch (e: Exception) {        // 加载失败
                    Log.d(TAG, "loadChap: ${e.stackTrace}")
                    return false
                }
            }
        }
        return true
    }

    private fun splitChap(@IntRange(from = 1) chapIndex: Int) {
        synchronized(mLocksForChap[chapIndex]!!) {
            when (mChapStatusTable[chapIndex]!!) {
                ChapStatus.Unload -> {
                    throw IllegalStateException("章节[$chapIndex]未完成加载，不能进行分页!")
                }
                ChapStatus.Nonpaged -> {
                    val readChapter = mReadChapterTable[chapIndex]!!
                    mPageContentProvider.split(readChapter)
                    mChapStatusTable[chapIndex] = ChapStatus.Finished
                    Log.d(TAG, "splitChap: $chapIndex")
                }
                ChapStatus.Finished -> Unit
            }
        }
    }

    private fun preprocess(chapIndex: Int, process: (index: Int) -> Unit) {
        process(chapIndex)
        var i = chapIndex - 1
        while (i > 0 && i >= chapIndex - mPreprocessBefore) {
            process(i)
            i--
        }
        i = chapIndex + 1
        while (i <= mBookData!!.chapCount && i <= chapIndex + mPreprocessBehind) {
            process(i)
            i++
        }
    }

    private fun preload(chapIndex: Int): Boolean {
        var res = true
        preprocess(chapIndex) {
            val temp = loadChap(it)
            if (it == chapIndex) res = temp
        }
        return res
    }

    private fun preSplit(chapIndex: Int) = preprocess(chapIndex) {
        splitChap(it)
    }

    private fun loadCurChap(): Boolean = preload(mCurChapIndex)

    private fun splitCurChap() = preSplit(mCurChapIndex)


    fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    ) {
        this.mBookLoader = loader
        this.mContentParser = SimpleContentParser()
        this.mPageContentProvider = SimlpePageContentProvider(this.mReadConfig)
        this.mCurChapIndex = chapIndex
        this.curPageIndex = pageIndex

        // 进行目录初始化准备工作，如：显示“加载目录中”视图
        showMessagePage("加载目录中......")

        startTask {
            val initTocRes = initToc()                     // initToc()是一个耗时任务，不能在主线程执行
            if (initTocRes) {
                // 目录初始化已经完成，接下来要开始加载章节内容，可以先将“加载目录中”视图清除，替换为“章节xxx加载中”视图
                // 由于涉及UI更新，需要在主线程执行
                curPageIndex = chapIndex
                showAllChapLoadPage()
                val loadChapRes = loadCurChap()
                if (loadChapRes) {
                    // 等待视图宽高数据，用来分页
                    while (!mAttachedToWindow) {
                        Thread.sleep(100)
                    }
                    mReadConfig.setContentDimen(width, height)          // TODO：不应该使用ReadView的宽高，应该使用ReadContent的宽高，这里只是为了方便测试
                    splitCurChap()
                    post {
                        this@ReadView.mAdapterData.removeAt(chapIndex - 1)
                        adapter.notifyItemRemoved(chapIndex - 1)
                        mReadChapterTable[chapIndex]?.apply {
                            this@ReadView.mAdapterData.insert(
                                chapIndex - 1, PageType.READ_PAGE,
                                pages
                            )
                            adapter.notifyItemRangeInserted(chapIndex - 1, pages.size)
                        }
                        curPageIndex = chapIndex
                        adapter.notifyDataSetChanged()
                        // TODO: 如果在目录完成初始化之后，章节内容加载之前，滑动了页面，这就会造成pageIndex改变
                    }
                } else {
                    showMessagePage("章节加载失败......")
                }
            } else {
                showMessagePage("目录加载失败......")
            }
        }
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

    private fun showAllChapLoadPage() = post {
        mAdapterData.clear()
        for (i in 1..mBookData!!.chapCount) {
            val chap = mBookData!!.getChap(i)
            mAdapterData.add(PageType.CHAP_LOAD_PAGE, listOf(chap.title ?: "", i))
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
                    page.content.setContent(pageData)
                    page.content.provider = mPageContentProvider
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return mAdapterData.getPageType(position).ordinal
        }

        override fun getItemCount(): Int = mAdapterData.size
    }

    private class Pages {

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

}