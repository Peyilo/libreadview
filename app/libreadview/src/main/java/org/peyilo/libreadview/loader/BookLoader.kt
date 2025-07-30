package org.peyilo.libreadview.loader

import org.peyilo.libreadview.data.novel.BookData
import org.peyilo.libreadview.data.novel.ChapData


interface BookLoader {

    fun initToc(): BookData

    fun loadChap(chapData: ChapData)

}