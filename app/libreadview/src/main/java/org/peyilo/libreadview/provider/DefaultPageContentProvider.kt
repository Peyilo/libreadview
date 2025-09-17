package org.peyilo.libreadview.provider

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.libreadview.data.page.CharData
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.data.page.StringLineData
import org.peyilo.libreadview.parser.ParagraphContent
import org.peyilo.libreadview.parser.ReadChapter
import org.peyilo.libreadview.parser.TitleContent
import org.peyilo.libreadview.simple.ReadStyle

/**
 * 在给定ReadConfig下，负责完成ReadChap的分页，并且负载将PageData绘制到Canvas上
 */
class DefaultPageContentProvider(config: ReadStyle): PageContentProvider {

    private var _config: ReadStyle? = config
    private val config get() = _config!!

    private val measuredPaint = Paint()

    private val remainedBodyWidth get() = config.bodyWidth - config.bodyPaddingRight - config.bodyPaddingLeft
    private val remainedBodyHeight get() = config.bodyHeight - config.bodyPaddingTop - config.bodyPaddingBottom
    private val bodyStartLeft get() = config.bodyPaddingLeft.toFloat()
    private val bodyStartTop get() = config.bodyPaddingTop.toFloat()

    private val remainedTitleWidth get() = remainedBodyWidth - config.titlePaddingLeft - config.titlePaddingRight
    private val remainedTitleHeight get() = remainedBodyHeight - config.titlePaddingTop - config.titlePaddingBottom

    private val remainedContentWidth get() = remainedBodyWidth - config.contentPaddingLeft - config.contentPaddingRight
    private val remainedContentHeight get() = remainedBodyHeight - config.contentPaddingTop - config.contentPaddingBottom

    private fun measureText(char: Char, size: Float): Float {
        return measureText(char.toString(), size)
    }

    private fun measureText(string: String, size: Float): Float {
        synchronized(measuredPaint) {
            if (measuredPaint.textSize != size) {
                measuredPaint.textSize = size
            }
            return measuredPaint.measureText(string)
        }
    }

    /**
     * 对一个段落进行断行
     * @param text 待断行的字符串
     * @param offset 段落首行的偏移量
     * @param width 一行文字的最大宽度
     * @param size 文字大小
     */
    private fun breakLines(
        text: String,
        width: Float, size: Float,
        textMargin: Float, offset: Float
    ): List<StringLineData> {
        val lineList = ArrayList<StringLineData>()
        var line = StringLineData().apply { textSize = size }
        var w = width - offset
        var dimen: Float
        text.forEach {
            dimen = measureText(it, size)
            if (w < dimen) {    // 剩余宽度已经不足以留给该字符
                lineList.add(line)
                line = StringLineData().apply { textSize = size }
                w = width
            }
            w -= dimen + textMargin
            line.add(CharData(it).apply { this@apply.width = dimen })
        }
        val lastLine = line
        if (lastLine.text.isNotEmpty()) {
            lineList.add(lastLine)
        }
        return lineList
    }

    override fun paginate(chap: ReadChapter) {
        // 如果留给绘制内容的空间不足以绘制标题或者正文的一行，直接返回false
        if (config.contentTextSize > remainedBodyHeight
            || config.titleTextSize > remainedBodyHeight) {
            throw IllegalStateException()
        }
        chap.pages.clear()           // 清空pageData
        val width = remainedBodyWidth
        var height = remainedBodyHeight
        var base = bodyStartTop
        val left = bodyStartLeft
        var curPageIndex = 1
        var page = PageData(curPageIndex)

        // 开始章节标题的处理
        val firstContent = chap.content[0]
        val hasTitle = firstContent is TitleContent
        if (hasTitle) {
            // 剩余的空间为width - config.titlePaddingLeft - config.titlePaddingRight
            val titleLines = breakLines(firstContent.text,
                width - config.titlePaddingLeft - config.titlePaddingRight,
                config.titleTextSize, config.titleTextMargin, 0F)
            base += config.titlePaddingTop
            val titleLeft = left + config.titlePaddingLeft
            titleLines.forEach {
                base += config.titleTextSize
                it.apply {                  // 设置TextLine的base、left
                    this@apply.base = base
                    this@apply.left = titleLeft
                    it.isTitleLine = true
                }
                page.lines.add(it)
                base += config.titleLineMargin
            }
            var offset = 0F     // 正文内容的偏移
            if (titleLines.isNotEmpty()) {
                base += config.titlePaddingBottom - config.titleLineMargin
                offset += config.titlePaddingBottom + config.titlePaddingTop
                offset += config.titleTextSize * titleLines.size
                offset += config.titleLineMargin * (titleLines.size - 1)

                // 处理正文内容的偏移
                base += config.contentPaddingTop
                offset += config.contentPaddingTop
            }
            height -= offset.toInt()
        }
        // 如果剩余空间已经不足以再添加一行，就换成下一页
        if (height < config.contentTextSize + config.contentPaddingBottom) {
            height = remainedBodyHeight
            base = bodyStartTop
            chap.pages.add(page)
            curPageIndex++
            page = PageData(curPageIndex)
        }
        // 开始正文内容的处理
        val parasStartIndex = if (hasTitle) 1 else 0
        for (i in parasStartIndex until chap.content.size) {
            val para = chap.content[i] as ParagraphContent
            val paraLines = breakLines(para.text,
                width - config.contentPaddingLeft - config.contentPaddingRight,
                config.contentTextSize,
                config.contentTextMargin, config.firstParaIndent)
            for (j in paraLines.indices) {
                val line = paraLines[j]
                // 如果剩余空间已经不足以再添加一行，就换成下一页
                if (height < config.contentTextSize + config.contentPaddingBottom) {
                    height = remainedBodyHeight
                    base = bodyStartTop + config.contentPaddingTop
                    chap.pages.add(page)
                    curPageIndex++
                    page = PageData(curPageIndex)
                }
                base += config.contentTextSize
                line.apply {
                    this@apply.base = base
                    // 处理段落首行缩进
                    this@apply.left = left + config.contentPaddingLeft + if (j == 0) config.firstParaIndent else 0F
                }
                page.lines.add(line)
                base += config.contentLineMargin
                height -= (config.contentTextSize + config.contentLineMargin).toInt()
            }
            base += config.contentParaMargin - config.contentLineMargin
            height -= config.contentParaMargin - config.contentLineMargin      // 处理段落的额外间距
        }
        chap.pages.add(page)
    }

    override fun drawPage(page: PageData, canvas: Canvas) {
        page.lines.forEach {line ->
            if (line is StringLineData) {
                var left = line.left
                if (line.isTitleLine) {
                    line.text.forEach { charData ->
                        canvas.drawText(charData.char.toString(), left, line.base, config.titlePaint)
                        left += charData.width + config.titleTextMargin
                    }
                } else {
                    line.text.forEach { charData ->
                        canvas.drawText(charData.char.toString(), left, line.base, config.contentPaint)
                        left += charData.width + config.contentTextMargin
                    }
                }
            }
        }
    }

    fun destroy() {
        _config = null
    }

}