package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import org.peyilo.libreadview.data.page.PageData

/**
 * 正文显示视图
 */
class ReadContent(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    private var content: PageData? = null

    fun setContent(content: PageData) {
        this.content = content
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


    }

}