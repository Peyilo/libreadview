package org.peyilo.libreadview.simple.page

interface ContentMetrics {

    /**
     * 当进行一次measure之后，返回正文视图测量得到的宽度，
     * 必须返回measuredWidth， 因为调用measure之后，width可能还是0
     */
    fun getContentWidth(): Int

    /**
     * 当进行一次measure之后，返回正文视图测量得到的高度，
     * 必须返回measuredHeight， 因为调用measure之后，height可能还是0
     */
    fun getContentHeight(): Int

}