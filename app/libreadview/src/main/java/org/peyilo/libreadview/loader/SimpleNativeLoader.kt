package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
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

    var bookTitle = "No book title"
    val encoding: String            // 编码方式

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

    constructor(file: File, encoding: String = "UTF-8"): this(FileInputStream(file), encoding) {
        bookTitle = file.nameWithoutExtension
    }

    constructor(inputStream: InputStream, encoding: String = "UTF-8") {
        reader = BufferedReader(InputStreamReader(inputStream, encoding))
        this.encoding = encoding
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
    override fun initToc(): Book {
        val book = Book(bookTitle)
        var chap: Chapter? = null
        var line: String? = reader.readLine()
        while(line != null) {
            // 跳过空白行
            if (line.isBlank()) {
                line = reader.readLine()
                continue
            }
            if (line.startsWith(UTF8_BOM_PREFIX)) {
                line = line.substring(1)
            }
            // 开始解析内容
            if (isTitle(line)) {
                chap?.let {
                    book.addBookNode(chap)
                }
                chap = Chapter(line.trim())
            } else {
                if (chap == null) {                     // 如果第一行并不是标题，也就意味着第一章是没有标题的，那就使用第一行作为第一章的标题
                    chap = Chapter(line.trim())
                } else {
                    chap.addParagraph(line.trim())
                }
            }
            line = reader.readLine()
        }
        chap?.let {
            book.addBookNode(chap)
        }
        if (networkLagFlag) Thread.sleep(networkLagTime)
        return book
    }

    override fun loadChap(chapter: Chapter): Chapter {
        if (networkLagFlag) Thread.sleep(networkLagTime)
        return chapter
    }
}