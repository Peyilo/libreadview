package org.peyilo.libreadview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

abstract class BaseReadPage(
    context: Context, attrs: AttributeSet? = null
): ViewGroup(context, attrs) {

    /**
     * 当进行一次measure之后，返回正文视图测量得到的宽度，
     * 必须返回measuredWidth， 因为调用measure之后，width可能还是0
     */
    abstract fun getContentWidth(): Int

    /**
     * 当进行一次measure之后，返回正文视图测量得到的高度，
     * 必须返回measuredHeight， 因为调用measure之后，height可能还是0
     */
    abstract fun getContentHeight(): Int

}