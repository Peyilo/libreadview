package org.peyilo.libreadview.parser

import org.peyilo.libreadview.data.page.PageData

class ReadChapter (val chapIndex: Int) {
    val content = mutableListOf<Content>()
    val pages = mutableListOf<PageData>()
}