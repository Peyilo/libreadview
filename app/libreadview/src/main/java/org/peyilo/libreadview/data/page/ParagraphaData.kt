package org.peyilo.libreadview.data.page

class ParagraphaData: ContentElement() {

    // 由于段落可能因为分页被拆分，所以需要记录起止位置
    // 这里使用到的区间是闭区间 [startIndex, endIndex]
    var startIndex = 0
    var endIndex = 0

    val lines = mutableListOf<StringLineData>()

}