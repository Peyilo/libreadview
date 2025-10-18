package org.peyilo.libreadview.data.page

import android.graphics.Color

/**
 * 表示屏幕上要绘制的字符。包含字符的颜色、尺寸、布局信息
 */
class CharData(val char: Char): ContentElement() {
    // 字体颜色
    var color: Int = Color.BLACK

    // 是否高亮
    var isHighlighted: Boolean = false
    var highlightColor: Int = Color.YELLOW

    // 尺寸
    var width = 0F

    // 布局
    var baseline = 0F
    var ascent = 0F
    var descent = 0F

}