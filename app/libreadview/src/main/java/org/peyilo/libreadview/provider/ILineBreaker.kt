package org.peyilo.libreadview.provider

import org.peyilo.libreadview.data.page.StringLineData

interface ILineBreaker {

    fun breakLines(
        text: String,
        width: Float,
        size: Float,
        textMargin: Float, offset: Float,
        measureText: (String, Float) -> Float
    ): List<StringLineData>

}