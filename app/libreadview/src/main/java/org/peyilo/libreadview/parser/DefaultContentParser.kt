package org.peyilo.libreadview.parser

import org.peyilo.libreadview.data.novel.ChapData

class DefaultContentParser: ContentParser {

    override fun parse(chapData: ChapData): ReadChapter {
        val readChapter = ReadChapter(chapData.chapIndex)
        // 添加标题
        chapData.title?.let { readChapter.content.add(TitleContent().apply {
            text = chapData.title!!
        }) }
        // 将每一行作为一个段落内容
        var temp: String
        chapData.content.split('\n').forEach {
            temp = it.trim()
            if (temp.trim().isNotEmpty()) {
                readChapter.content.add(ParagraphContent().apply {
                    text = temp
                })
            }
        }
        return readChapter
    }

}