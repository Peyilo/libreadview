package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntRange
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.loader.BookLoader
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.loader.SimpleTextLoader
import org.peyilo.libreadview.parser.DefaultContentParser
import org.peyilo.libreadview.provider.SimlpePageContentProvider
import org.peyilo.libreadview.ui.ChapLoadPage
import org.peyilo.libreadview.ui.ReadPage
import org.peyilo.libreadview.ui.TocInitPage
import java.io.File
import java.util.concurrent.Executors

class ReadView(
    context: Context, attrs: AttributeSet? = null
): PageContainer(context, attrs) {

    private val pages = mutableListOf<Pair<PageType, Any>>()

    private val readBook = ReadBook()

    private val threadPool by lazy { Executors.newFixedThreadPool(10) }

    private var attached = false

    init {
        adapter = PageAdapter(pages)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attached = false
        threadPool.shutdownNow()        // 关闭正在执行的所有线程
    }

    private fun startTask(task: Runnable) {
        threadPool.submit(task)
    }

    /**
     * 进行目录初始化准备工作，如：显示“加载目录中”视图
     */
    private fun prepareInitToc() {
        pages.add(Pair(PageType.TOC_INIT_PAGE, "加载目录中......"))
    }

    /**
     * 目录初始化已经完成，接下来要开始加载章节内容，可以先将“加载目录中”视图清除，替换为“章节xxx加载中”视图
     */
    private fun prepareLoadChap() {
        pages.clear()       // 先将“加载目录中”视图清除
        pages.add(Pair(PageType.CHAP_LOAD_PAGE, "章节xxx加载中......"))
    }

    private fun refreshAllPage() {

    }

    fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    ) {
        readBook.loader = loader
        readBook.parser = DefaultContentParser()
        readBook.provider = SimlpePageContentProvider()
        readBook.curChapIndex = chapIndex
        readBook.curPageIndex = pageIndex
        prepareInitToc()
        startTask {
            val initTocRes = readBook.initToc()                     // readBook.initToc()是一个耗时任务，不能在主线程执行
            if (initTocRes) {
                prepareLoadChap()
                val loadChapRes = readBook.loadCurChap()
                if (loadChapRes) {
                    // 等待视图宽高数据，用来分页
                    while (!attached) {
                        Thread.sleep(100)
                    }
                    post {
                        readBook.splitCurChap()
                        refreshAllPage()
                    }
                }
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

    enum class PageType {
        TOC_INIT_PAGE, CHAP_LOAD_PAGE, READ_PAGE
    }

    class PageAdapter(val pages: List<Pair<PageType, Any>>): Adapter<PageAdapter.PageViewHolder>() {

        inner class PageViewHolder(page: View) : ViewHolder(page)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page: View = when (viewType) {
                PageType.TOC_INIT_PAGE.ordinal -> {
                    TocInitPage(parent.context)
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
            val type = pages[position].first
            when (type) {
                PageType.TOC_INIT_PAGE -> {
                    val page = holder.itemView as TocInitPage
                    val text = pages[position].second as String
                    page.text = text
                }
                PageType.CHAP_LOAD_PAGE -> {
                    val page = holder.itemView as ChapLoadPage
                    val chapTitle = pages[position].second as String
                    page.chapTitle = chapTitle
                }
                PageType.READ_PAGE -> {
                    val page = holder.itemView as ReadPage
                    val pageData = pages[position].second as PageData
                    page.content.setContent(pageData)
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return pages[position].first.ordinal
        }

        override fun getItemCount(): Int = pages.size
    }

}