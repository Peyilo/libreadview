package org.peyilo.libreadview.data

/**
 * 从from到to的左闭右开区间
 */
class RangeData: AdditionalData() {
    var from = 1
    var to = 1

    val size get() = to - from

    override fun toString(): String {
        return "(from=$from, to=$to)"
    }
}