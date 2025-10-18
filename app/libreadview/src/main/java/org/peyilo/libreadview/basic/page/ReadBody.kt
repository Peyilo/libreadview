package org.peyilo.libreadview.basic.page

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.layout.PageContentProvider
import org.peyilo.libreadview.turning.util.computeTime

/**
 * 正文显示视图
 */
class ReadBody(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    var content: PageData? = null

    var provider: PageContentProvider? = null

    companion object {
        private const val TAG = "ReadBody"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeTime(TAG, "onDraw") {
            provider?.apply {
                content?.let {
                    drawPage(content!!, canvas)
                }
            }
        }
    }

}