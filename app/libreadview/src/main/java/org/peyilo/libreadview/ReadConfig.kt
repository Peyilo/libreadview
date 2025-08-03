package org.peyilo.libreadview

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.toColorInt

class ReadConfig {

    private val lock = Object()
    private var _contentDimenIsInitialized = false
    val contentDimenIsInitialized: Boolean
        get() = synchronized(lock) { _contentDimenIsInitialized }

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

    var paddingLeft = 60F
    var paddingRight = 60F
    var paddingTop = 20F
    var paddingBottom = 20F

    val titlePaint: Paint by lazy { Paint().apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = 72F
        color = Color.BLACK
        flags = Paint.ANTI_ALIAS_FLAG
    } }

    val contentPaint: Paint by lazy { Paint().apply {
        textSize = 56F
        color = "#2B2B2B".toColorInt()
        flags = Paint.ANTI_ALIAS_FLAG
    } }

    val titleTextColor get() = titlePaint.color
    val titleTextSize get() = titlePaint.textSize
    val contentTextColor get() = contentPaint.color
    val contentTextSize get() = contentPaint.textSize

    val paraOffset get() = contentPaint.measureText("测试") // 段落首行的偏移
    var titleMargin = 160F                                      // 章节标题与章节正文的间距
    var textMargin = 0F                                         // 字符间隔
    var lineMargin = 30F                                        // 行间隔
    var paraMargin = 50F                                        // 段落间隔

}