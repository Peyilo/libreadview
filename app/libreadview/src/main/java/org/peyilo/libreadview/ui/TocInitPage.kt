package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * 加载目录时，要显示的视图
 */
class TocInitPage(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var text: String = ""

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

}