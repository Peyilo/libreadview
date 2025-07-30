package org.peyilo.readview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.peyilo.readview.R

class GridPage (
    context: Context, attrs: AttributeSet? = null
): ViewGroup(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.gridpage_layout, this)
    }

    val root: View = findViewById(R.id.gridpage_root)
    val header: TextView = root.findViewById<TextView>(R.id.gridpage_header)
    val footer: View = root.findViewById(R.id.gridpage_footer)

    val content: GridNumberView = root.findViewById(R.id.gridpage_content)
    val progress: TextView = root.findViewById(R.id.gridpage_footer_progress)
    val clock: TextView = root.findViewById(R.id.gridpage_footer_clock)

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

}