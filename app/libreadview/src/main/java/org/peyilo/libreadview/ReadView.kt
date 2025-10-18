package org.peyilo.libreadview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import org.peyilo.libreadview.basic.page.ReadPage
import org.peyilo.libreadview.data.page.ContentElement
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.data.page.StringLineData

/**
 * ReadView和PageContainer的唯一的一个区别是：一个对内的adapter，一个对外的adapter
 */
abstract class ReadView(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): AbstractPageContainer(context, attrs, defStyleAttr, defStyleRes), BookNavigator {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    companion object {
        private const val TAG = "ReadView"
    }

    /**
     * ReadView对内提供了adapter的getter和setter
     */
    protected var adapter: Adapter<out ViewHolder>
        get() = getInnerAdapter() ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) = setInnerAdapter(value)

    /**
     * 设置页面背景颜色
     */
    fun setPageBackgroundColor(@ColorInt color: Int) {
        setPageBackground(color.toDrawable())
    }

    /**
     * 设置页面背景为资源图片
     */
    fun setPageBackgroundResource(@DrawableRes resId: Int) {
        val drawable = AppCompatResources.getDrawable(context, resId)
            ?: throw IllegalArgumentException("Resource ID $resId is not valid.")
        setPageBackground(drawable)
    }

    /**
     * 设置页面背景为Drawable
     */
    abstract fun setPageBackground(drawable: Drawable)

    /**
     * 设置页面背景为Bitmap
     */
    fun setPageBackground(bitmap: Bitmap) {
        val drawable = bitmap.toDrawable(resources)
        setPageBackground(drawable)
    }

    private var isSelectedMode = false // 是否处于文字选择模式
    private var longPressTriggered = false
    private val downPos = PointF()

    private var handler = Handler(Looper.getMainLooper())

    // 系统推荐的长按时长（一般为500ms）
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    // 长按检测任务
    private val longPressRunnable = Runnable {
        if (!longPressTriggered) {
            longPressTriggered = true
            isSelectedMode = true
            onLongPress() // 回调逻辑
        }
    }

    private fun onLongPress() {
        getCurPage()?.let {
            if (it is ReadPage) {
                it.body.content?.apply {
                    highlighPara(this, downPos, it)
                }
                it.body.invalidate()
            }
        }
    }

    /**
     * 在给定的行列表中，找到最接近y位置的行，或者包含y位置的行
     */
    private fun findClosestOrContainingLine(lines: List<ContentElement>, y: Float): ContentElement? {
        if (lines.isEmpty()) return null

        if (y < lines.first().top) return lines.first()
        if (y > lines.last().bottom) return lines.last()

        for (i in lines.indices) {
            val line = lines[i]
            if (y >= line.top && y <= line.bottom) {
                return line                 // 命中
            }
            // 如果在两行之间（y < next.top）
            if (i < lines.lastIndex) {
                val next = lines[i + 1]
                if (y > line.bottom && y < next.top) {
                    // 比较离哪一行更近
                    val distToCurrent = y - line.bottom
                    val distToNext = next.top - y
                    return if (distToCurrent < distToNext) line else next
                }
            }
        }
        return null
    }


    /**
     * 给定行的索引，找到该行所属的段落的所有行
     */
    private fun findParagraphLines(lines: List<ContentElement>, targetIndex: Int): List<ContentElement> {
        if (targetIndex !in lines.indices) return emptyList()
        // 向上找到段落的起始行
        var startIndex = targetIndex
        for (i in targetIndex downTo 0) {
            val line = lines[i]
            if (line is StringLineData) {
                assert(!line.isTitleLine)           // 标题行不可能是段落的一部分
                if (line.isFirstLineOfParagraph) {
                    startIndex = i
                    break
                }
            }
        }
        // 向下找到段落的结束行
        var endIndex = lines.size - 1
        for (i in targetIndex + 1 until lines.size) {
            val line = lines[i]
            if (line is StringLineData && line.isFirstLineOfParagraph) {
                endIndex = i - 1
                break
            }
        }
        // 返回整个段落的行
        return lines.subList(startIndex, endIndex + 1)
    }

    /**
     * 根据给定的页面数据和按下位置，高亮对应的段落
     */
    private fun highlighPara(pageData: PageData, downPos: PointF, readPage: ReadPage) {
        // 坐标转换：由于ReadPage的大小实际上和ReadView大小一致，因此downPos可以视作相对于ReadPage左上角的坐标
        // 只需减去body的偏移即可得到相对于body的坐标
        downPos.x = downPos.x - readPage.body.left
        downPos.y = downPos.y - readPage.body.top
        // 根据长按位置的y确定哪一行被高亮
        val touchedLine = findClosestOrContainingLine(pageData.elements, downPos.y)
        if (touchedLine != null && touchedLine is StringLineData) {
            // 如果是标题行，则需要将所有标题行都高亮
            if (touchedLine.isTitleLine) {
                pageData.elements.forEach { lineData ->
                    if (lineData is StringLineData && lineData.isTitleLine) {
                        lineData.text.forEach { charData ->
                            charData.isHighlighted = !charData.isHighlighted
                        }
                    }
                }
            } else {
                // 如果不是标题行，则需要高亮line对应的段落中的全部行
                val targetIndex = pageData.elements.indexOf(touchedLine)
                findParagraphLines(pageData.elements, targetIndex).forEach { lineData ->
                    if (lineData is StringLineData) {
                        lineData.text.forEach { charData ->
                            charData.isHighlighted = !charData.isHighlighted
                        }
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
                longPressTriggered = false
                // 启动延迟检测任务
                handler.postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressDetection()
            }
        }

        // 一旦进入选择模式，接下来的事件不再交给AbstractPageContainer处理
        // 这里处理的逻辑是：一旦触发长按事件，本轮事件的后续事件都有长按进行处理
        if (isSelectedMode) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isSelectedMode = false
            }
            return true // 消费事件
        }

        return super.onTouchEvent(event)
    }

    private fun cancelLongPressDetection() {
        handler.removeCallbacks(longPressRunnable)
    }

}