package org.peyilo.libreadview.data.page

class StringLineData: LineData() {

    var isTitleLine = false
    val text = mutableListOf<CharData>()
    var textSize = 0F
    var base = 0F
    var left = 0F

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

    override fun toString(): String {
        val builder = StringBuilder()
        text.forEach {
            builder.append(it.char)
        }
        return "{base = $base, left = $left} $builder"
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