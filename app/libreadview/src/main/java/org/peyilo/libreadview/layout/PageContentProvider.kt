package org.peyilo.libreadview.layout

import android.graphics.Canvas
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.content.ReadChapter

interface PageContentProvider {

    /**
     * 将章节切分为多个Page
     */
    fun paginate(chap: ReadChapter)

    fun drawPage(page: PageData, canvas: Canvas)

}