package org.peyilo.libreadview.data.page

import org.peyilo.libreadview.data.AdditionalData

class PageData(val pageIndex: Int): AdditionalData() {

    val lines = mutableListOf<LineData>()

}