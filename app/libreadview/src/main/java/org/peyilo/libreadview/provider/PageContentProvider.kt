package org.peyilo.libreadview.provider

import android.graphics.Canvas
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.parser.ReadChapter

interface PageContentProvider {

    /**
     * 将章节切分为多个Page
     */
    fun split(chap: ReadChapter)

    fun drawPage(page: PageData, canvas: Canvas)

}