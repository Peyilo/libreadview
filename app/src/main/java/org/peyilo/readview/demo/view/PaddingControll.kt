package org.peyilo.readview.demo.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.peyilo.readview.R

class PaddingControll(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_padding_controll, this, true)
    }

    val label: TextView = findViewById(R.id.padding_controll_label)
    val segmented: SegmentedControl = findViewById(R.id.padding_controll_segmented)
    val topProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.padding_controll_top).apply {
        setPrimaryThumbText("上")
    }
    val bottomProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.padding_controll_bottom).apply {
        setPrimaryThumbText("下")
    }
    val leftProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.padding_controll_left).apply {
        setPrimaryThumbText("左")
    }
    val rightProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.padding_controll_right).apply {
        setPrimaryThumbText("右")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxChildWidth = 0
        var maxChildHeight = 0

        // 测量子 View
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0,
                    heightMeasureSpec, 0)
                maxChildWidth = maxOf(maxChildWidth, child.measuredWidth)
                maxChildHeight = maxOf(maxChildHeight, child.measuredHeight)
            }
        }

        // 加上 padding
        val desiredWidth = maxChildWidth + paddingLeft + paddingRight
        val desiredHeight = maxChildHeight + paddingTop + paddingBottom

        // 结合父容器给的 MeasureSpec 决定最终尺寸
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return MarginLayoutParams(p)
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = paddingLeft
        val top = paddingTop
        val right = r - l - paddingRight
        val bottom = b - t - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                child.layout(left, top, right, bottom)
            }
        }
    }
}