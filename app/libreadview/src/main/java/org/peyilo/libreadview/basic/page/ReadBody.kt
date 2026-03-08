package org.peyilo.libreadview.basic.page

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.layout.PageContentProvider

/**
 * 正文显示视图
 * TODO: 如果要实现长按选择功能，应该在这里实现，而不是再ReadView这个Page容器中实现
 */
class ReadBody(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var content: PageData? = null
    var provider: PageContentProvider? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        provider?.apply {
            content?.let {
                drawPage(content!!, canvas)
            }
        }
    }

}