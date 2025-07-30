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
    val config = ReadConfig()
    lateinit var loader: BookLoader
    lateinit var parser: ContentParser
    lateinit var provider: PageContentProvider

    private val chapStatusTable = mutableMapOf<Int, ChapStatus>()
    private val readChapterTable = mutableMapOf<Int, ReadChapter>()

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
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "initToc: ${e.stackTrace}")
            return false
        }
    }

    /**
     * 多个线程调用该函数加载相同章节时，会触发竞态条件，因而需要对该章节的状态进行同步
     * TODO：错误设计的锁，假设当前chapStatusTable[chapIndex]!!为ChapStatus.Upload，那么会阻塞全部当前chapStatusTable[chapIndex]!!为ChapStatus.Upload的函数调用
     */
    private fun loadChap(@IntRange(from = 1) chapIndex: Int): Boolean {
        synchronized(chapStatusTable[chapIndex]!!) {              
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
        synchronized(chapStatusTable[chapIndex]!!) {
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

    fun getChap(@IntRange(from = 1) chapIndex: Int): ReadChapter {
        return readChapterTable[chapIndex]!!
    }


    class IndexPairs {
        val cur = IndexPair()
        val prev = IndexPair()
        val next = IndexPair()
    }

    class IndexPair(var chapIndex: Int = 0, var pageIndex: Int = 0) {
        override fun toString(): String {
            return "chapIndex = $chapIndex, pageIndex = $pageIndex"
        }
    }
}