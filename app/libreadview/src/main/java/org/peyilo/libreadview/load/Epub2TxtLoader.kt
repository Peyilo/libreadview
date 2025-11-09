package org.peyilo.libreadview.load

import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import nl.siegmann.epublib.domain.Book as EpubBook

/**
 * 将EPUB的内容转化为纯文本格式进行加载的Loader
 * 注意：这只是一个简单的提取，并不能完整还原EPUB的格式和结构
 */
class Epub2TxtLoader: BookLoader {

    private var inputStream: InputStream
    private var _epubBook: EpubBook? = null
    private val epubBook get() = _epubBook!!

    constructor(
        file: File,
    ): this(
        FileInputStream(file),
    )

    constructor(
        inputStream: InputStream,
    ) {
        this.inputStream = inputStream
    }

    override fun initToc(): Book {
        _epubBook = EpubReader().readEpub(inputStream)
        val book = Book(epubBook.title)

        // 读取目录信息
        epubBook.tableOfContents.tocReferences.forEach { tocRef ->
            val chapterTitle = tocRef.title
            val chapter = Chapter(chapterTitle).apply {
                obj = tocRef
            }
            book.addBookNode(chapter)
        }

        return book
    }

    override fun loadChap(chapter: Chapter): Chapter {
        val tocRef = chapter.obj as TOCReference
        val resource = tocRef.resource
        val html = String(resource.data)
        // 统一换行、去除多余空白
        val cleaned = html
            .replace("\r", "")
            .replace("\n", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // 提取段落，这里只提取<p>标签内的内容
        val paragraphRegex = Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)
        paragraphRegex.findAll(cleaned)
            .map {
                it.groupValues[1]
                    .replace(Regex("<[^>]+>"), "") // 去掉HTML标签
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .forEach { para ->
                chapter.addParagraph(para)
            }
        return chapter
    }

}