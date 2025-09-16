package org.peyilo.libreadview.simple

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.View.OnClickListener
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import org.peyilo.libreadview.simple.page.ReadPage
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

    fun isContentDimenInitialized() = _contentDimenIsInitialized

    // 页面内边距
    var pagePaddingTop = DisplayUtil.dpToPx(context, 0)
    var pagePaddingBottom = DisplayUtil.dpToPx(context, 0)
    var pagePaddingLeft = DisplayUtil.dpToPx(context, 20)
    var pagePaddingRight = DisplayUtil.dpToPx(context, 20)
    // 页眉内边距
    var headerPaddingTop = DisplayUtil.dpToPx(context, 64)
    var headerPaddingBottom = DisplayUtil.dpToPx(context, 20)
    var headerPaddingLeft = DisplayUtil.dpToPx(context, 0)
    var headerPaddingRight = DisplayUtil.dpToPx(context, 0)
    // 页脚内边距
    var footerPaddingTop = DisplayUtil.dpToPx(context, 20)
    var footerPaddingBottom = DisplayUtil.dpToPx(context, 20)
    var footerPaddingLeft = DisplayUtil.dpToPx(context, 0)
    var footerPaddingRight = DisplayUtil.dpToPx(context, 0)
    // 文字内容内边距
    var contentPaddingTop = DisplayUtil.dpToPx(context, 10)
    var contentPaddingBottom = DisplayUtil.dpToPx(context, 10)
    var contentPaddingLeft = DisplayUtil.dpToPx(context, 0)
    var contentPaddingRight = DisplayUtil.dpToPx(context, 0)


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

    var titleTextMargin = DisplayUtil.dpToPx(context, 0F)                                 // 章节标题字符间隔

    var contentTextMargin = DisplayUtil.dpToPx(context, 0F)                               // 正文字符间隔
    var lineMargin = DisplayUtil.dpToPx(context, 16F)                                     // 行间隔
    var paraMargin = DisplayUtil.dpToPx(context, 30F)                                    // 段落间隔

    var mPageBackground: Drawable = Color.WHITE.toDrawable()
    var mHeaderAndFooterTextColor = Color.BLACK

    var quitBtnOnClickListener: OnClickListener? = null

    fun initPage(page: View) {
        page.background = mPageBackground
        when (page) {
            is ReadPage -> {
                initReadPage(page)
            }
        }
    }

    private fun initReadPage(page: ReadPage) {
        page.chapTitle.setTextColor(mHeaderAndFooterTextColor)
        page.progress.setTextColor(mHeaderAndFooterTextColor)
        page.clock.setTextColor(mHeaderAndFooterTextColor)
        page.quitImgView.setOnClickListener(quitBtnOnClickListener)
        page.setPadding(pagePaddingLeft, pagePaddingTop, pagePaddingRight, pagePaddingBottom)
        page.header.setPadding(headerPaddingLeft, headerPaddingTop, headerPaddingRight, headerPaddingBottom)
        page.footer.setPadding(footerPaddingLeft, footerPaddingTop, footerPaddingRight, footerPaddingBottom)
        // content的padding并不是通过View的测量和布局实现的，而是通过在onDraw进行偏移实现的
        // 因此，这里无需调用page.content.setPadding
    }
}
