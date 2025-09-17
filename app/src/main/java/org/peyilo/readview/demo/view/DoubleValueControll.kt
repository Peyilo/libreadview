package org.peyilo.readview.demo.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.peyilo.readview.R

class DoubleValueControll(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_double_value_controll, this, true)
    }

    val label: TextView = findViewById(R.id.double_value_controll_label)

    val firstProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.double_value_controll_title).apply {
        useDual = false
    }
    val secondProgressBar: DualThumbProgressBar = findViewById<DualThumbProgressBar>(R.id.double_value_controll_content).apply {
        useDual = false
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