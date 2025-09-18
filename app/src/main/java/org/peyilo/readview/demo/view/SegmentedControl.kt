package org.peyilo.readview.demo.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import org.peyilo.libreadview.util.DisplayUtil

class SegmentedControl @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var options: List<String> = listOf("页面", "页眉", "页脚", "内容", "标题", "正文")
    private var selectedIndex = 0
    private var onOptionSelected: ((Int) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#D3D3D3".toColorInt()          // 背景色
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = DisplayUtil.spToPx(context, 14F)
        textAlign = Paint.Align.CENTER
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FDFDFD".toColorInt()              // 选中项背景色
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private val bgRadius = 50f          // 圆角半径
    private val selectedRadius = 45f    // 选中项圆角半径
    private val offset get() = bgRadius - selectedRadius

    fun setOptions(newOptions: List<String>) {
        options = newOptions
        invalidate()
    }

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
        invalidate()
    }

    fun setOnOptionSelectedListener(listener: (Int) -> Unit) {
        onOptionSelected = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制整体背景
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, bgRadius, bgRadius, bgPaint)

        // 没有选项则不绘制
        if (options.isEmpty()) return

        val itemWidth = width / options.size.toFloat()
        val itemHeight = height.toFloat()

        // 绘制选中背景
        rect.set(
            itemWidth * selectedIndex + offset,
            offset,
            itemWidth * (selectedIndex + 1) - offset,
            itemHeight - offset
        )
        canvas.drawRoundRect(rect, bgRadius, bgRadius, selectedPaint)

        // 绘制文字
        val fm = textPaint.fontMetrics
        val textY = height / 2 - (fm.ascent + fm.descent) / 2
        for (i in options.indices) {
            val cx = itemWidth * (i + 0.5f)
            textPaint.color = if (i == selectedIndex) Color.BLACK else Color.DKGRAY
            canvas.drawText(options[i], cx, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val index = (event.x / (width / options.size)).toInt()
            if (index != selectedIndex) {
                selectedIndex = index
                onOptionSelected?.invoke(index)
                invalidate()
            }
        }
        return true
    }
}