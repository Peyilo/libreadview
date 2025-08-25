package org.peyilo.libreadview.simple.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.provider.PageContentProvider

/**
 * 正文显示视图
 */
class ReadContent(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    private var content: PageData? = null

    private val paint = Paint()

    var provider: PageContentProvider? = null

    fun setContent(content: PageData) {
        this.content = content
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        provider?.apply {
            content?.let {
                drawPage(content!!, canvas, paint)
            }
        }

    }

}