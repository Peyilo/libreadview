package org.peyilo.libreadview.data.novel

import org.peyilo.libreadview.data.AdditionalData

/**
 * 从from到to的左闭右开区间
 */
class RangeData: AdditionalData() {
    var from = 1
    var to = 1

    val size get() = to - from

}