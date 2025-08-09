package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter


/**
 * 一个简单的文字加载器，将指定text作为一个无标题章节
 */
class SimpleTextLoader(private val text: String): BookLoader {

    override fun initToc(): Book {
        val book = Book("")
        book.addBookNode(Chapter("").apply {
            val lines = text.split("\n")
            for (line in lines) {
                // 跳过空白行
                if (line.isBlank()) {
                    continue
                }
                addParagraph(line.trim())
            }
        })
        return book
    }

    override fun loadChap(chapter: Chapter): Chapter = chapter

}