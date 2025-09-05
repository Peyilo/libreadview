package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntRange
import org.peyilo.libreadview.data.RangeData
import org.peyilo.libreadview.loader.BookLoader
import org.peyilo.libreadview.util.DirectMapChapIndexer
import org.peyilo.libreadview.util.LogHelper
import java.util.concurrent.Executors

/**
 * AbstractReadView没有保存任何小说数据（除了pageview），小说数据如何表示如何处理都交给实现类；
 * ReadView必须支持以下功能：
 *  - 书籍导航
 *  - 长按选择页面中的文字
 */
abstract class AbstractReadView(
    context: Context, attrs: AttributeSet? = null
): ReadView(context, attrs), BookNavigator {

    companion object {
        private const val TAG = "AbstractReadView"
    }

    /**
     * 预处理章节数：需要预处理当前章节之前的preprocessBefore个章节
     */
    var preprocessBefore = 0

    /**
     * 预处理章节数：需要预处理当前章节之后的preprocessBehind个章节
     */
    var preprocessBehind = 0

    private var mChapPageIndexer: DirectMapChapIndexer? = null

    private val mThreadPool by lazy {
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
    }

    private val mChapStatusTable = mutableMapOf<Int, ChapStatus>()
    private val mLocksForChap = mutableMapOf<Int, Any>()


    private enum class ChapStatus {
        Unload,                 // 未加载
        Nonpaged,               // 未分页
        Uninflated,             // 未填充
        Finished                // 加载、分页完成
    }

    /**
     * 对指定章节及其前后指定数量的章节执行预处理操作。
     *
     * 此方法以 `chapIndex` 为中心，向前处理最多 [preprocessBefore] 个章节，
     * 向后处理最多 [preprocessBehind] 个章节，共处理一个连续的章节窗口区间。
     *
     * @param chapIndex 要处理的中心章节索引（从 1 开始）
     * @param process   对每个章节索引执行的处理逻辑（回调函数）
     *
     * 示例：若 chapIndex = 5，preprocessBefore = 2，preprocessBehind = 1，
     *      则会处理章节 [3, 4, 5, 6]（只要不越界）
     */
    protected fun processNearbyChapters(@IntRange(from = 1) chapIndex: Int, process: (index: Int) -> Unit) {
        val chapCount = getChapCount()
        process(chapIndex)
        var i = chapIndex - 1
        while (i > 0 && i >= chapIndex - preprocessBefore) {
            process(i)
            i--
        }
        i = chapIndex + 1
        while (i <= chapCount && i <= chapIndex + preprocessBehind) {
            process(i)
            i++
        }
    }

    protected abstract fun loadChap(@IntRange(from = 1) chapIndex: Int): Boolean

    protected abstract fun splitChap(@IntRange(from = 1) chapIndex: Int): Boolean

    protected abstract fun inflateChap(@IntRange(from = 1) chapIndex: Int): Boolean

    protected fun loadNearbyChapters(chapIndex: Int): Boolean {
        var res = true
        processNearbyChapters(chapIndex) {
            val temp = loadChap(it)
            if (it == chapIndex) res = temp
        }
        return res
    }

    protected fun splitNearbyChapters(chapIndex: Int) = processNearbyChapters(chapIndex) {
        splitChap(it)
    }

    protected fun inflateNearbyChapters(chapIndex: Int) = processNearbyChapters(chapIndex) {
        inflateChap(it)
    }

    protected fun onInitTocSuccess(chapCount: Int) {
        mChapPageIndexer = DirectMapChapIndexer(chapCount)
        for (i in 1..chapCount) {              // 初始化章节状态表
            mChapStatusTable[i] = ChapStatus.Unload
            mLocksForChap[i] = Any()
        }
    }

    protected fun loadChapWithLock(chapIndex: Int, block: (Int) -> Boolean): Boolean
    = synchronized(mLocksForChap[chapIndex]!!) {
        if (mChapStatusTable[chapIndex] == ChapStatus.Unload) {      // 未加载
            val res = block(chapIndex)
            if (res) {
                mChapStatusTable[chapIndex] = ChapStatus.Nonpaged
            }
            return res
        }
        return false
    }

    protected fun splitChapWithLock(chapIndex: Int, block: (Int) -> Boolean): Boolean
    = synchronized(mLocksForChap[chapIndex]!!) {
        when (mChapStatusTable[chapIndex]!!) {
            ChapStatus.Unload -> {
                throw IllegalStateException("章节[$chapIndex]未完成加载，不能进行分页!")
            }
            ChapStatus.Nonpaged -> {
                val res = block(chapIndex)
                if (res) {
                    mChapStatusTable[chapIndex] = ChapStatus.Uninflated
                }
                return res
            }
            ChapStatus.Uninflated, ChapStatus.Finished -> Unit
        }
        return false
    }

    protected fun inflateChapWithLock(chapIndex: Int, block: (Int) -> Boolean): Boolean
    = synchronized(mLocksForChap[chapIndex]!!) {
        when (mChapStatusTable[chapIndex]!!) {
            ChapStatus.Unload -> {
                throw IllegalStateException("章节[$chapIndex]未完成加载，不能进行填充!")
            }
            ChapStatus.Nonpaged -> {
                throw IllegalStateException("章节[$chapIndex]未完成分页，不能进行填充!")
            }
            ChapStatus.Uninflated -> {
                val res = block(chapIndex)
                if (res) {
                    mChapStatusTable[chapIndex] = ChapStatus.Finished
                }
                return res
            }
            ChapStatus.Finished -> Unit
        }
        return false
    }

    /**
     * 打开书籍并跳转到指定的章节与页码。
     *
     * 子类应实现此方法以完成具体的书籍加载逻辑，例如：
     * - 加载章节内容
     * - 初始化页面渲染数据
     * - 滚动或分页定位到指定章节和页码
     *
     * @param loader 用于加载书籍内容的加载器实例
     * @param chapIndex 要打开的章节索引，从 1 开始（默认打开第 1 章）
     * @param pageIndex 要跳转的页码索引，从 1 开始（默认跳转到第 1 页）
     *
     * 实现需注意：
     * - 若指定章节或页码不存在，应进行合理的边界检查
     * - 应通过 `processNearbyChapters` 进行相邻章节处理
     */
    abstract fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    )

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mThreadPool.shutdownNow()        // 关闭正在执行的所有线程
    }

    /**
     * 向线程池中添加一个任务
     */
    protected fun startTask(task: Runnable) {
        mThreadPool.submit(task)
    }

    override fun onPageChanged(oldPageIndex: Int, newPageIndex: Int) {
        super.onPageChanged(oldPageIndex, newPageIndex)
        val newChapIndex = findChapByPosition(newPageIndex - 1).first
        val oldChapIndex = findChapByPosition(oldPageIndex - 1).first
        if (newPageIndex != oldPageIndex) {
            onChapChanged(oldChapIndex, newChapIndex)
        }
        LogHelper.d(TAG, "onPageChanged: oldPageIndex = $oldPageIndex, newPageIndex = $newPageIndex")
    }

    /**
     * 当章节发生改变，就会回调这个函数
     */
    protected open fun onChapChanged(oldChapIndex: Int, newChapIndex: Int) {
        LogHelper.d(TAG, "onChapChanged: oldChapIndex = $oldChapIndex, newChapIndex = $newChapIndex")
        startTask {
            processNearbyChapters(newChapIndex) {
                loadChap(it)
                splitChap(it)
                post {
                    inflateChap(it)
                }
            }
        }
    }

    /**
     * 获取指定章节在adapterData中的位置, 如adapterData中position为0就对应为RangeData中的1.
     * 举个例子：第一章节有3页，位于前面三页，因此返回的是from=0, to=3, size=3. 返回的range是一个左闭右开区间.
     * @param chapIndex 从1开始
     */
    protected fun getChapPageRange(@IntRange(from = 1) chapIndex: Int): RangeData {
        if (mChapPageIndexer == null) {
            throw IllegalStateException("mChapPageIndexer is not be initialized." +
                    "It may be due to an error caused by the directory not completing initialization")
        }
        val intRange = mChapPageIndexer!!.getChapPageRange(chapIndex)
        return RangeData().apply {
            from = intRange.first - 1
            to = intRange.last
        }
    }

    /**
     * 返回一个pair，第一个表示章节索引，第二个表示position对应的item属于章节中的第几页。两个都是从1开始算
     * @param position 从0开始
     */
    protected fun findChapByPosition(@IntRange(from = 0) position: Int): Pair<Int, Int> {
        if (mChapPageIndexer == null) {
            throw IllegalStateException("mChapPageIndexer is not be initialized. " +
                    "It may be due to an error caused by the directory not completing initialization")
        }
        return mChapPageIndexer!!.findChapForPage(position + 1)
    }

    /**
     * 更新某个章节的page数量
     */
    protected fun updateChapPageCount(chapIndex: Int, chapPageCount: Int) {
        if (mChapPageIndexer == null) {
            throw IllegalStateException("mChapPageIndexer is not be initialized. " +
                    "It may be due to an error caused by the directory not completing initialization")
        }
        mChapPageIndexer!!.updateChapPageCount(chapIndex, chapPageCount)
    }

    /**
     * 获取指定章节中包含的page数量
     */
    protected fun getChapPageCount(chapIndex: Int): Int {
        if (mChapPageIndexer == null) {
            throw IllegalStateException("mChapPageIndexer is not be initialized. " +
                    "It may be due to an error caused by the directory not completing initialization")
        }
        return mChapPageIndexer!!.getChapPageCount(chapIndex)
    }

    /**
     * 获取章节数量
     */
    override fun getChapCount(): Int {
        if (mChapPageIndexer == null) {
            return 0
        }
        return mChapPageIndexer!!.chapCount
    }

    override fun getCurChapIndex(): Int = findChapByPosition(getCurContainerPageIndex() - 1).first

    override fun getCurChapPageCount(): Int = getChapPageCount(getCurChapIndex())

    override fun getCurChapPageIndex(): Int = findChapByPosition(getCurContainerPageIndex() - 1).second

    /**
     * 章节跳转
     * @param chapIndex 需要跳转到的章节
     */
    override fun navigateToChapter(@IntRange(from = 1) chapIndex: Int): Boolean = navigateBook(chapIndex, 1)


    /**
     * 跳转到下一个章节
     */
    override fun navigateToNextChapter(): Boolean = navigateToChapter(getCurChapIndex() + 1)

    /**
     * 跳转到上一个章节
     */
    override fun navigateToPrevChapter(): Boolean = navigateToChapter(getCurChapIndex() - 1)

    /**
     * 跳转到指定章节的指定页
     */
    override fun navigateBook(chapIndex: Int, chapPageIndex: Int): Boolean {
        if (chapIndex < 1 || chapIndex > getChapCount()) {      // chapIndex越界了
            return false
        }
        val chapRange = getChapPageRange(chapIndex)
        if (chapPageIndex > chapRange.size || chapPageIndex < 1) {                   // chapPageIndex越界了
            return false
        }
        return navigatePage(chapRange.from + chapPageIndex)
    }

}