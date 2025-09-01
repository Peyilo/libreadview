package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import org.peyilo.libreadview.util.LogHelper

@RequiresApi(Build.VERSION_CODES.M)
class StaticLayoutView(context: Context?, attrs: AttributeSet?): View(context, attrs) {

    fun paginateText(
        text: CharSequence,
        textPaint: TextPaint,
        pageWidth: Int,
        pageHeight: Int
    ): List<StaticLayout> {
        val fullLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, pageWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.5f)
            .setIncludePad(false)
            .build()

        val pages = mutableListOf<StaticLayout>()
        val totalLines = fullLayout.lineCount

        var startLine = 0

        while (startLine < totalLines) {
            var endLine = startLine
            var height = 0

            while (endLine < totalLines) {
                val lineTop = fullLayout.getLineTop(startLine)
                val lineBottom = fullLayout.getLineBottom(endLine)
                height = lineBottom - lineTop

                if (height > pageHeight) break
                endLine++
            }

            if (endLine == startLine) {
                // 避免死循环（单行超高）
                endLine++
            }

            val startOffset = fullLayout.getLineStart(startLine)
            val endOffset = fullLayout.getLineEnd(endLine - 1)

            val pageText = text.subSequence(startOffset, endOffset)

            val pageLayout = StaticLayout.Builder
                .obtain(pageText, 0, pageText.length, textPaint, pageWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.5f)
                .setIncludePad(false)
                .build()

            pages.add(pageLayout)

            startLine = endLine
        }

        return pages
    }

    val textPaint = TextPaint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
    }

    var pages: List<StaticLayout>? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LogHelper.d(TAG, "onMeasure: ")
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        LogHelper.d(TAG, "onLayout: ")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        LogHelper.d(TAG, "onSizeChanged: w = $w, h = $h, oldw = $oldw, oldh = $oldh")
        pages = paginateText(
            text = """
            TODO: 这是一个长文本
            """.trimIndent(),
            textPaint = textPaint,
            pageWidth = w,
            pageHeight = h
        )
    }

    companion object {
        private const val TAG = "StaticLayoutView"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pages?.let {
            it[0].draw(canvas)
        }
    }




}