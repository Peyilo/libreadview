package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.novel.BookData
import org.peyilo.libreadview.data.novel.ChapData
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * 一个简单的本地文件加载器,支持自定义的章节标题匹配规则以及目录初始化和章节加载的随即延迟
 */
open class SimpleNativeLoader: BookLoader {

    /**
     * 一个模拟网络延迟的标记位,开启以后相当于在目录初始化和章节加载的基础上,添加了额外的随机延迟
     */
    var networkLagFlag = false
    private val networkLagTime: Long get() = Random.nextInt(200, 2000).toLong()

    /**
     * 默认的章节标题匹配规则,只有当没有指定额外的章节标题匹配规则时,才会使用该规则
     */
    private val defaultTitleRegex by lazy { Regex("(^\\s*第)(.{1,7})[章卷](\\s*)(.*)") }

    private val reader: BufferedReader

    constructor(file: File): this(FileInputStream(file))

    constructor(inputStream: InputStream) {
        reader = BufferedReader(InputStreamReader(inputStream))
    }

    private val titlePatternList by lazy { mutableListOf<Regex>() }

    companion object {
        private const val UTF8_BOM_PREFIX = "\uFEFF"       // ZWNBSP字符，UTF-8带BOM格式
    }

    /**
     * 添加章节标题匹配规则
     */
    fun addTitleRegex(regex: String) {
        titlePatternList.add(Regex(regex))
    }

    /**
     * 清空章节标题匹配规则
     */
    fun clearTitleRegex() {
        titlePatternList.clear()
    }

    /**
     * 判断指定字符串是否为章节标题
     * @param line 需要判断的目标字符串
     */
    protected open fun isTitle(line: String): Boolean {
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

    /**
     * 对于本地文件来说,在加载目录的时候就已经完成了所有章节的解析了
     */
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