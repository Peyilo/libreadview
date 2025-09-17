package org.peyilo.libreadview.content

import org.peyilo.libreadview.data.Chapter

class DefaultContentParser: ContentParser {

    override fun parse(chapter: Chapter): ReadChapter {
        val readChapter = ReadChapter()
        // 添加标题
        chapter.title.let {
            readChapter.content.add(TitleContent().apply {
                text = chapter.title
            })
        }
        for (i in 0 until chapter.paragraphCount) {
            val para = chapter.getParagraph(i)
            readChapter.content.add(ParagraphContent().apply {
                text = para
            })
        }
        return readChapter
    }

}