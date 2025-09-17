package org.peyilo.libreadview.load

import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter


interface BookLoader {

    /**
     * 在这里完成目录初始化，目录初始化之后，要保证Book中的每个Chapter都包含了加载章节所有信息，例如：章节的链接。
     * 以便在loadChap(chapter: Chapter)中完成章节实际内容的加载
     */
    fun initToc(): Book

    /**
     * 加载章节的内容
     */
    fun loadChap(chapter: Chapter): Chapter

}