package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter


interface BookLoader {

    fun initToc(): Book

    fun loadChap(chapter: Chapter): Chapter

}