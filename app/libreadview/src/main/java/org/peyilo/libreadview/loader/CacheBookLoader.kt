package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter

// TODO: 具有缓存功能的BookLoader
class CacheBookLoader(
    val bookLoader: BookLoader
): BookLoader {

    override fun initToc(): Book {
        val book = bookLoader.initToc()

        return book
    }

    override fun loadChap(chapter: Chapter): Chapter {
        val chap = bookLoader.loadChap(chapter)

        return chap
    }

}