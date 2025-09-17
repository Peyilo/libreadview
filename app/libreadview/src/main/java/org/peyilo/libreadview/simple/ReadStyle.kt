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
import org.peyilo.libreadview.provider.Alignment
import org.peyilo.libreadview.simple.page.ReadPage
import org.peyilo.libreadview.util.DisplayUtil

class ReadStyle(
    private val context: Context
) {

    private val lock = Object()
    private var _bodyDimenIsInitialized = false

    // ReadBody的显示宽高
    // 需要在ReadPage测量完成后，调用initBodyDimen进行初始化
    var bodyWidth = 0f
    var bodyHeight = 0f

    /**
     * 初始化ReadBody要显示的宽高
     */
    fun initBodyDimen(w: Int, h: Int) {
        synchronized(lock) {
            bodyWidth = w.toFloat()
            bodyHeight = h.toFloat()
            _bodyDimenIsInitialized = true
            lock.notifyAll()  // 唤醒等待者
        }
    }

    /**
     * 挂起当前线程，直到initBodyDimen被调用，非忙等待
     */
    fun waitForInitialized() {
        synchronized(lock) {
            while (!_bodyDimenIsInitialized) {
                lock.wait()             // 挂起等待，不消耗 CPU
            }
        }
    }

    fun isBodyDimenInitialized() = _bodyDimenIsInitialized

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
    var bodyPaddingTop = DisplayUtil.dpToPx(context, 10)
    var bodyPaddingBottom = DisplayUtil.dpToPx(context, 10)
    var bodyPaddingLeft = DisplayUtil.dpToPx(context, 0)
    var bodyPaddingRight = DisplayUtil.dpToPx(context, 0)
    // 标题内边距
    var titlePaddingTop = DisplayUtil.dpToPx(context, 0)
    var titlePaddingBottom = DisplayUtil.dpToPx(context, 72)
    var titlePaddingLeft = DisplayUtil.dpToPx(context, 0)
    var titlePaddingRight = DisplayUtil.dpToPx(context, 0)
    // 正文内边距
    var contentPaddingTop = DisplayUtil.dpToPx(context, 0)
    var contentPaddingBottom = DisplayUtil.dpToPx(context, 0)
    var contentPaddingLeft = DisplayUtil.dpToPx(context, 0)
    var contentPaddingRight = DisplayUtil.dpToPx(context, 0)

    // 标题相关参数
    val titlePaint: Paint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = DisplayUtil.spToPx(context, 28F)
        color = Color.BLACK
        isAntiAlias = true
    }

    val titleTextColor get() = titlePaint.color
    val titleTextSize get() = titlePaint.textSize

    var titleTextMargin = DisplayUtil.dpToPx(context, 0F)                                 // 章节标题字符间隔
    var titleLineMargin = DisplayUtil.dpToPx(context, 16F)                                // 章节标题行间隔
    var titleAlignment = Alignment.LEFT                                                  // 章节标题对齐方式

    // 正文相关参数
    val contentPaint: Paint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = DisplayUtil.spToPx(context, 20F)
        color = "#1E1A1A".toColorInt()
        isAntiAlias = true
    }

    val contentTextColor get() = contentPaint.color
    val contentTextSize get() = contentPaint.textSize

    var firstParaIndent = contentPaint.measureText("测试") // 段落首行的偏移
    var contentTextMargin = DisplayUtil.dpToPx(context, 0F)                               // 正文字符间隔
    var contentLineMargin = DisplayUtil.dpToPx(context, 16F)                                     // 行间隔
    var contentParaMargin = DisplayUtil.dpToPx(context, 30F)                                    // 段落间隔

    // Page相关参数
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
        // body的padding并不是通过View的测量和布局实现的，而是通过在onDraw进行偏移实现的
        // 因此，这里无需调用page.body.setPadding
    }

}
