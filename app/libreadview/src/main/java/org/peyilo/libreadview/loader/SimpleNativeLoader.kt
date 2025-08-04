package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.novel.BookData
import org.peyilo.libreadview.data.novel.ChapData
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.random.Random

class SimpleNativeLoader(file: File): BookLoader {

    /**
     * 一个模拟网络延迟的标记位
     */
    var networkLagFlag = false
    private val networkLagTime: Long get() = Random.nextInt(200, 2000).toLong()

    private val defaultTitleRegex by lazy { Regex("(^\\s*第)(.{1,7})[章卷](\\s*)(.*)") }

    private val reader: BufferedReader by lazy {
        BufferedReader(
            InputStreamReader(FileInputStream(file))
        )
    }

    private val titlePatternList by lazy { mutableListOf<Regex>() }

    companion object {
        private const val UTF8_BOM_PREFIX = "\uFEFF"       // ZWNBSP字符，UTF-8带BOM格式
    }

    fun addTitleRegex(regex: String) {
        titlePatternList.add(Regex(regex))
    }

    fun clearTitleRegex() {
        titlePatternList.clear()
    }

    /**
     * 判断指定字符串是否为章节标题
     * @param line 需要判断的目标字符串
     */
    private fun isTitle(line: String): Boolean {
        val line = line.trim()
        if (titlePatternList.isEmpty()) {
            if (defaultTitleRegex.matches(line))
                return true
        } else {
            titlePatternList.forEach {
                if (it.matches(line))
                    return true
            }
        }
        return false
    }

    override fun initToc(): BookData {
        val bookData = BookData()
        var chapIndex = 1
        val stringBuilder = StringBuilder()
        var chap: ChapData? = null
        var line: String?
        var firstChapInitialized = false
        do {
            line = reader.readLine()
            if (line == null) {
                // 处理剩余内容
                if (firstChapInitialized) {
                    chap?.content = stringBuilder.toString()
                } else if (stringBuilder.isNotEmpty()) {
                    bookData.addChild(ChapData(chapIndex).apply {
                        content = stringBuilder.toString()
                    })
                }
                stringBuilder.clear()
                break
            }
            // 跳过空白行
            if (line.isBlank())
                continue
            if (line.startsWith(UTF8_BOM_PREFIX)) {
                line = line.substring(1)
            }
            // 开始解析内容
            if (isTitle(line)) {
                // 在第一个标题出现之前，可能会出现部分没有章节标题所属的行，将这些作为一个无标题章节
                if (stringBuilder.isNotEmpty()) {
                    if (!firstChapInitialized) {
                        chap = ChapData(chapIndex).apply {
                            content = stringBuilder.toString()
                            stringBuilder.delete(0, stringBuilder.length)
                        }
                        bookData.addChild(chap)
                        chapIndex++
                    } else {
                        chap!!.content = stringBuilder.toString()
                        stringBuilder.delete(0, stringBuilder.length)
                    }
                }
                if (!firstChapInitialized) firstChapInitialized = true
                chap = ChapData(chapIndex).apply { title = line }
                bookData.addChild(chap)
                chapIndex++
            } else {
                stringBuilder.append(line).append('\n')
            }
        } while (true)
        if (networkLagFlag) Thread.sleep(networkLagTime)
        return bookData
    }

    override fun loadChap(chapData: ChapData) {
        if (networkLagFlag) Thread.sleep(networkLagTime)
    }
}