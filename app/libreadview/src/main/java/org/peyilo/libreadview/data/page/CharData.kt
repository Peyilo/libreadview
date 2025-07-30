package org.peyilo.libreadview.data.page

import android.graphics.Color
import org.peyilo.libreadview.data.AdditionalData

class CharData(val char: Char): AdditionalData() {
    var color: Int = Color.BLACK
    var width = 0F
}