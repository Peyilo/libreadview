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
        return chapter.apply {
            addParagraph(String(resource.data))
        }
    }

}