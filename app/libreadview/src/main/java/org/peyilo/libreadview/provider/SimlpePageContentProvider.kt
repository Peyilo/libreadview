package org.peyilo.libreadview.provider

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.parser.ReadChapter

class SimlpePageContentProvider: PageContentProvider {

    private val paint = Paint()

    override fun split(chap: ReadChapter): List<PageData> {
        TODO("Not yet implemented")
    }

    override fun drawPage(page: PageData, canvas: Canvas, paint: Paint) {
        TODO("Not yet implemented")
    }

}