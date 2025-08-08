package org.peyilo.libreadview.data.novel
import androidx.annotation.IntRange
import org.peyilo.libreadview.data.AdditionalData


open class BookData: AdditionalData() {
    var title: String? = null                  // 书名
    var author: String? = null                  // 作者

    val list: MutableList<ChapData> by lazy { mutableListOf() }

    open val chapCount get() = list.size        // 章节数

    // 获取第index章节，当isMultiVol=true时，index指的是章节在全书中所处的位置
    fun getChap(@IntRange(from = 1) index: Int): ChapData {
        return list[index - 1]
    }

    fun addChild(bookChild: ChapData) {
        list.add(bookChild)
    }

}