package org.peyilo.libreadview.simple

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import org.peyilo.libreadview.util.DisplayUtil

class ReadStyle(
    private val context: Context
) {

    private val lock = Object()
    private var _contentDimenIsInitialized = false

    var contentWidth = 0f
    var contentHeight = 0f

    /**
     * 初始化ReadContent要显示的宽高
     */
    fun initContentDimen(w: Int, h: Int) {
        synchronized(lock) {
            contentWidth = w.toFloat()
            contentHeight = h.toFloat()
            _contentDimenIsInitialized = true
            lock.notifyAll()  // 唤醒等待者
        }
    }

    /**
     * 挂起当前线程，直到initContentDimen被调用，非忙等待
     */
    fun waitForInitialized() {
        synchronized(lock) {
            while (!_contentDimenIsInitialized) {
                lock.wait()             // 挂起等待，不消耗 CPU
            }
        }
    }

    var paddingLeft = DisplayUtil.dpToPx(context, 18F)
    var paddingRight = DisplayUtil.dpToPx(context, 18F)
    var paddingTop = DisplayUtil.dpToPx(context, 18F)
    var paddingBottom = DisplayUtil.dpToPx(context, 18F)

    val titlePaint: Paint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = DisplayUtil.spToPx(context, 28F)
        color = Color.BLACK
        isAntiAlias = true
    }

    val contentPaint: Paint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = DisplayUtil.spToPx(context, 20F)
        color = "#1E1A1A".toColorInt()
        isAntiAlias = true
    }

    val titleTextColor get() = titlePaint.color
    val titleTextSize get() = titlePaint.textSize
    val contentTextColor get() = contentPaint.color
    val contentTextSize get() = contentPaint.textSize

    var firstParaIndent = contentPaint.measureText("测试") // 段落首行的偏移
    var titleMargin = DisplayUtil.dpToPx(context, 50F)                                    // 章节标题与章节正文的间距
    var textMargin = DisplayUtil.dpToPx(context, 0F)                                     // 字符间隔
    var lineMargin = DisplayUtil.dpToPx(context, 16F)                                     // 行间隔
    var paraMargin = DisplayUtil.dpToPx(context, 30F)                                    // 段落间隔

    var mPageBackground: Drawable = Color.WHITE.toDrawable()
    var mHeaderAndFooterTextColor = Color.BLACK
}
