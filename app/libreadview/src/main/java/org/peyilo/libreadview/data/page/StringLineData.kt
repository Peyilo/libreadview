package org.peyilo.libreadview.data.page

class StringLineData: LineData() {

    var isTitleLine = false
    var isFirstLineOfParagraph = false

    val text = mutableListOf<CharData>()

    fun add(str: String) {
        str.forEach {
            add(it)
        }
    }

    fun add(char: Char): CharData {
        val charData = CharData(char)
        text.add(charData)
        return charData
    }

    fun add(charData: CharData) {
        text.add(charData)
    }

    /**
     * 给定文字间距，计算该行文字的宽度
     * @param textMargin 文字间距
     * @return 该行文字的宽度
     */
    fun computeWidth(textMargin: Float): Float {
        var w = 0F
        text.forEach {
            w += it.width + textMargin
        }
        if (w > 0) {
            w -= textMargin
        }
        return w
    }
}