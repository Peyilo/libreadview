package org.peyilo.libreadview.data.page

import org.peyilo.libreadview.data.AdditionalData

open class ContentElement: AdditionalData() {

    var left = 0F
    var top = 0F
    var right = 0F
    var bottom = 0F

    // 命中测试
    fun hitTest(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }

}