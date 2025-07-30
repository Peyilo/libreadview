package org.peyilo.libreadview.data.novel

import androidx.annotation.IntRange
import org.peyilo.libreadview.data.AdditionalData

/**
 * 保存章节数据
 */
class ChapData(
    @IntRange(from=1) val chapIndex: Int
): AdditionalData() {
    var title: String? = null
    lateinit var content: String
}