package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * 加载章节时，要显示的视图
 */
class ChapLoadPage(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var chapTitle: String = ""

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

}