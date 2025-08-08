package org.peyilo.libreadview.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.peyilo.libreadview.R

/**
 * 一个用展示小说内容的页面
 * 页面中需要支持各种各样的视图，如video、picture、text、button
 */
class ReadPage (
    context: Context, attrs: AttributeSet? = null
): ViewGroup(context, attrs), ContentMetrics {

    init {
        LayoutInflater.from(context).inflate(R.layout.item_read_page, this)
        setBackgroundColor(Color.WHITE)
    }

    val root: View = findViewById(R.id.page_root)
    val header: TextView = root.findViewById(R.id.page_header)
    val footer: View = root.findViewById(R.id.page_footer)

    val content: ReadContent = root.findViewById(R.id.page_content)
    val progress: TextView = root.findViewById(R.id.page_footer_progress)
    val clock: TextView = root.findViewById(R.id.page_footer_clock)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount != 1) {
            throw IllegalStateException("only support childCount == 1")
        }

        // Step 1: 测量自身，尽可能的大
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)

        // Step 2: 测量子View，子View都和其父View一样大
        root.measure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    override fun getContentWidth(): Int = content.measuredWidth

    override fun getContentHeight(): Int = content.measuredHeight

}