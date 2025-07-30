package org.peyilo.libreadview

import android.util.Log
import androidx.annotation.IntRange
import org.peyilo.libreadview.data.novel.BookData
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.loader.BookLoader
import org.peyilo.libreadview.parser.ContentParser
import org.peyilo.libreadview.parser.ReadChapter
import org.peyilo.libreadview.provider.PageContentProvider

class ReadBook {

    @IntRange(from = 1) var curChapIndex = 1
    @IntRange(from = 1) var curPageIndex = 1

    private var preprocessBefore = 0
    private var preprocessBehind = 0

    private lateinit var bookData: BookData
    private var bookDataInitialized = false

    val config = ReadConfig()
    lateinit var loader: BookLoader
    lateinit var parser: ContentParser
    lateinit var provider: PageContentProvider

    private val chapStatusTable = mutableMapOf<Int, ChapStatus>()
    private val readChapterTable = mutableMapOf<Int, ReadChapter>()
    private val locksForChap = mutableMapOf<Int, Any>()

    private enum class ChapStatus {
        Unload,                 // 未加载
        Nonpaged,               // 未分页
        Finished                // 全部完成
    }

    companion object {
        private const val TAG = "ReadBook"
    }

    fun initToc(): Boolean {
        try {
            bookData = loader.initToc()
            for (i in 1..bookData.chapCount) {              // 初始化章节状态表
                chapStatusTable[i] = ChapStatus.Unload
                locksForChap[i] = Any()
            }
            bookDataInitialized = true
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
        synchronized(locksForChap[chapIndex]!!) {
            if (chapStatusTable[chapIndex] == ChapStatus.Unload) {      // 未加载
                try {
                    val chapData = bookData.getChap(chapIndex)
                    loader.loadChap(chapData)
                    readChapterTable[chapIndex] = parser.parse(chapData)    // 解析ChapData
                    chapStatusTable[chapIndex] = ChapStatus.Nonpaged        // 更新状态
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
        synchronized(locksForChap[chapIndex]!!) {
            when (chapStatusTable[chapIndex]!!) {
                ChapStatus.Unload -> {
                    throw IllegalStateException("章节[$chapIndex]未完成加载，不能进行分页!")
                }
                ChapStatus.Nonpaged -> {
                    val readChapter = readChapterTable[chapIndex]!!
                    provider.split(readChapter)
                    chapStatusTable[chapIndex] = ChapStatus.Finished
                    Log.d(TAG, "splitChap: $chapIndex")
                }
                ChapStatus.Finished -> Unit
            }
        }
    }

    private fun preprocess(chapIndex: Int, process: (index: Int) -> Unit) {
        process(chapIndex)
        var i = chapIndex - 1
        while (i > 0 && i >= chapIndex - preprocessBefore) {
            process(i)
            i--
        }
        i = chapIndex + 1
        while (i <= bookData.chapCount && i <= chapIndex + preprocessBehind) {
            process(i)
            i++
        }
    }

    fun preload(chapIndex: Int): Boolean {
        var res = true
        preprocess(chapIndex) {
            val temp = loadChap(it)
            if (it == chapIndex) res = temp
        }
        return res
    }

    fun preSplit(chapIndex: Int) = preprocess(chapIndex) {
        splitChap(it)
    }

    fun loadCurChap(): Boolean = preload(curChapIndex)

    fun splitCurChap() = preSplit(curChapIndex)

    private fun getNextPageIndexPair(): Pair<Int, Int> {
        val curChapStatus = chapStatusTable[curChapIndex]!!
        return if (curChapStatus != ChapStatus.Finished) {     // 当前章节未完成分页
            Pair(curChapIndex + 1, 1)
        } else {
            val curChap = readChapterTable[curChapIndex]!!
            if (curPageIndex < curChap.pages.size) {        // 不是当前章节的最后一页
                Pair(curChapIndex, curPageIndex + 1)
            } else {        // 当前页为当前章节的最后一页
                Pair(curChapIndex + 1, 1)
            }
        }
    }

    private fun getPrevPageIndexPair(): Pair<Int, Int> {
        val curChapStatus = chapStatusTable[curChapIndex]!!
        return if (curChapStatus != ChapStatus.Finished) {     // 当前章节未完成分页
            val prevChapStatus = chapStatusTable[curChapIndex - 1]!!
            if (prevChapStatus == ChapStatus.Finished) {
                Pair(curChapIndex - 1, readChapterTable[curChapIndex - 1]!!.pages.size)
            } else {
                Pair(curChapIndex - 1, 1)
            }
        } else {
            if (curPageIndex > 1) {        // 不是当前章节的第一页
                Pair(curChapIndex, curPageIndex - 1)
            } else {        // 当前页为当前章节的第一页
                val prevChapStatus = chapStatusTable[curChapIndex - 1]!!
                if (prevChapStatus == ChapStatus.Finished) {
                    Pair(curChapIndex - 1, readChapterTable[curChapIndex - 1]!!.pages.size)
                } else {
                    Pair(curChapIndex - 1, 1)
                }
            }
        }
    }

    /**
     * 调用该函数之前需要保证chapIndex、pageIndex的有效性
     * 同时需要保证章节已经完成了加载和分页
     */
    private fun getPage(chapIndex: Int, pageIndex: Int): PageData {
        Log.d(TAG, "getPage: chapIndex=$chapIndex, pageIndex=$pageIndex")
        val chapStatus = chapStatusTable[chapIndex]!!
        return if (chapStatus == ChapStatus.Finished) {
            readChapterTable[chapIndex]!!.pages[pageIndex - 1]
        } else {
            TODO()
        }
    }

    fun getCurPage(): PageData {
        return getPage(curChapIndex, curPageIndex)
    }

    fun getNextPage(): PageData {
        val nextPageIndexPair = getNextPageIndexPair()
        return getPage(nextPageIndexPair.first, nextPageIndexPair.second)
    }

    fun getPrevPage(): PageData {
        val prevPageIndexPair = getPrevPageIndexPair()
        return getPage(prevPageIndexPair.first, prevPageIndexPair.second)
    }

    fun hasNextChap(): Boolean {
        return if (!bookDataInitialized) {
            false
        } else {
            curChapIndex < bookData.chapCount
        }
    }

    fun hasPrevChap(): Boolean {
        return if (!bookDataInitialized) {
            false
        } else {
            curChapIndex > 1
        }
    }

    fun hasNextPage(): Boolean {
        return if (!bookDataInitialized) {                  // BookData还未完成目录初始化
            false
        } else if (curChapIndex < bookData.chapCount) {     // 不是最后一章节
            true
        } else {        // 最后一章节
            val curChapStatus = chapStatusTable[curChapIndex]!!
            if (curChapStatus != ChapStatus.Finished) {     // 最后一章节未完成分页
                false
            } else {        // 完成了分页
                curPageIndex < readChapterTable[curChapIndex]!!.pages.size
            }
        }
    }

    fun hasPrevPage(): Boolean {
        return if (!bookDataInitialized) {
            false
        } else if (curChapIndex > 1) {      // 不是第一章节
            true
        } else {        // 第一章节
            val curChapStatus = chapStatusTable[curChapIndex]!!
            if (curChapStatus != ChapStatus.Finished) {
                false
            } else {
                curPageIndex != 1
            }
        }
    }

    fun getReadChapter(chapIndex: Int) = readChapterTable[chapIndex]

}