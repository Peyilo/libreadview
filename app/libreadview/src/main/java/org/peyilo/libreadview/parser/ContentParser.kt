package org.peyilo.libreadview.parser

import org.peyilo.libreadview.data.Chapter

interface ContentParser {

    /**
     * 解析Chapter的内容，生成一个由多个Content以及基本章节信息构成的ReadChapter
     */
    fun parse(chapter: Chapter): ReadChapter

}