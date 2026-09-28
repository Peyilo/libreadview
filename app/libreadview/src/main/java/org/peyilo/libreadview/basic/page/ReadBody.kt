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

    data class ScrollPageSegment(
        val chapterIndex: Int,
        val pageData: PageData,
        val offsetY: Float,
        val contentBottom: Float
    )

    var content: PageData? = null

    var provider: PageContentProvider? = null

    /** True while this body draws a whole chapter and scrolls independently of its page shell. */
    var isScrollMode = false
        internal set

    /** Bottom coordinate of the laid out content in scroll mode. */
    var scrollContentHeight = 0
        internal set

    /** Chapter layouts currently attached to the continuous scroll document. */
    var scrollPageSegments: List<ScrollPageSegment> = emptyList()
        internal set

    var onScrollPositionChanged: ((Int) -> Unit)? = null

    companion object {
        private const val TAG = "ReadBody"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeTime(TAG, "onDraw") {
            provider?.apply {
                if (isScrollMode && scrollPageSegments.isNotEmpty()) {
                    val visibleTop = scrollY.toFloat()
                    val visibleBottom = visibleTop + height
                    scrollPageSegments.forEach { segment ->
                        val segmentTop = segment.offsetY
                        val segmentBottom = segment.offsetY + segment.contentBottom
                        if (segmentBottom <= visibleTop || segmentTop >= visibleBottom) return@forEach

                        canvas.save()
                        canvas.translate(0F, segment.offsetY)
                        drawPage(segment.pageData, canvas)
                        canvas.restore()
                    }
                } else {
                    content?.let { drawPage(it, canvas) }
                }
            }
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (isScrollMode) onScrollPositionChanged?.invoke(t)
    }

    fun getMaxScrollY(): Int = (scrollContentHeight - height).coerceAtLeast(0)

}
