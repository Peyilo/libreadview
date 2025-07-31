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
import org.peyilo.libreadview.parser.SimpleContentParser
import org.peyilo.libreadview.provider.SimlpePageContentProvider
import org.peyilo.libreadview.ui.PlaceholderPage
import org.peyilo.libreadview.ui.ReadPage
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
        adapter = PageAdapter()
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

    private fun setPlaceholderPage(text: String) {
        post {
            val size = pages.size
            pages.clear()       // 先将“加载目录中”视图清除
            pages.add(Pair(PageType.PLACEHOLDER_PAGE, text))
            when (size) {
                0 -> {
                    adapter.notifyItemInserted(0)
                }
                1 -> {
                    adapter.notifyItemChanged(0)
                }
                else -> adapter.notifyDataSetChanged()
            }
        }
    }

    fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    ) {
        readBook.loader = loader
        readBook.parser = SimpleContentParser()
        readBook.provider = SimlpePageContentProvider(readBook.config)
        readBook.curChapIndex = chapIndex
        readBook.curPageIndex = pageIndex

        // 进行目录初始化准备工作，如：显示“加载目录中”视图
        setPlaceholderPage("加载目录中......")

        startTask {
            val initTocRes = readBook.initToc()                     // readBook.initToc()是一个耗时任务，不能在主线程执行
            if (initTocRes) {
                // 目录初始化已经完成，接下来要开始加载章节内容，可以先将“加载目录中”视图清除，替换为“章节xxx加载中”视图
                // 由于涉及UI更新，需要在主线程执行
                setPlaceholderPage("章节xxx加载中......")

                val loadChapRes = readBook.loadCurChap()
                if (loadChapRes) {
                    // 等待视图宽高数据，用来分页
                    while (!attached) {
                        Thread.sleep(100)
                    }
                    readBook.config.setContentDimen(width, height)          // TODO：不应该使用ReadView的宽高，应该使用ReadContent的宽高，这里只是为了方便测试
                    readBook.splitCurChap()
                    post {
                        pages.clear()
                        readBook.getReadChapter(chapIndex)?.let {
                            it.pages.forEach { page ->
                                pages.add(Pair(PageType.READ_PAGE, page))
                            }
                        }
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    setPlaceholderPage("章节加载失败......")
                }
            } else {
                setPlaceholderPage("目录加载失败......")
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
        PLACEHOLDER_PAGE, READ_PAGE
    }

    private inner class PageAdapter: Adapter<PageAdapter.PageViewHolder>() {

        inner class PageViewHolder(page: View) : ViewHolder(page)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page: View = when (viewType) {
                PageType.PLACEHOLDER_PAGE.ordinal -> {
                    PlaceholderPage(parent.context)
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
                PageType.PLACEHOLDER_PAGE -> {
                    val page = holder.itemView as PlaceholderPage
                    val text = pages[position].second as String
                    page.text = text
                }
                PageType.READ_PAGE -> {
                    val page = holder.itemView as ReadPage
                    val pageData = pages[position].second as PageData
                    page.content.setContent(pageData)
                    page.content.provider = readBook.provider
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return pages[position].first.ordinal
        }

        override fun getItemCount(): Int = pages.size
    }

}