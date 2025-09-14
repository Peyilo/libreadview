package org.peyilo.libreadview.provider

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.libreadview.data.page.CharData
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.data.page.StringLineData
import org.peyilo.libreadview.parser.ParagraphContent
import org.peyilo.libreadview.parser.ReadChapter
import org.peyilo.libreadview.parser.TitleContent
import org.peyilo.libreadview.simple.ReadConfig

/**
 * 在给定ReadConfig下，负责完成ReadChap的分页，并且负载将PageData绘制到Canvas上
 */
class SimlpePageContentProvider(config: ReadConfig): PageContentProvider {

    private var _config: ReadConfig? = config
    private val config get() = _config!!

    private val measuredPaint = Paint()

    private val remainedWidth get() = config.contentWidth - config.paddingLeft - config.paddingRight
    private val remainedHeight get() = config.contentHeight - config.paddingTop - config.paddingBottom
    private val startLeft get() = config.paddingLeft
    private val startTop get() = config.paddingTop

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

    override fun split(chap: ReadChapter) {
        // 如果留给绘制内容的空间不足以绘制标题或者正文的一行，直接返回false
        if (config.contentTextSize > remainedHeight
            || config.titleTextSize > remainedHeight) {
            throw IllegalStateException()
        }
        chap.pages.clear()           // 清空pageData
        val width = remainedWidth
        var height = remainedHeight
        var base = startTop
        val left = startLeft
        var curPageIndex = 1
        var page = PageData(curPageIndex)

        val firstContent = chap.content[0]
        val hasTitle = firstContent is TitleContent
        if (hasTitle) {
            val titleLines = breakLines(firstContent.text, width, config.titleTextSize, 0F, 0F)
            titleLines.forEach {
                base += config.titleTextSize
                it.apply {                  // 设置TextLine的base、left
                    this@apply.base = base
                    this@apply.left = left
                    it.isTitleLine = true
                }
                page.lines.add(it)
                base += config.lineMargin
            }
            var offset = 0F     // 正文内容的偏移
            if (titleLines.isNotEmpty()) {
                base += config.titleMargin - config.lineMargin
                offset += config.titleMargin
                offset += config.titleTextSize * titleLines.size
                offset += config.lineMargin * (titleLines.size - 1)
            }
            height -= offset.toInt()
        }
        // 如果剩余空间已经不足以再添加一行，就换成下一页
        if (height < config.contentTextSize) {
            height = remainedHeight
            base = startTop
            chap.pages.add(page)
            curPageIndex++
            page = PageData(curPageIndex)
        }
        // 开始正文内容的处理
        val parasStartIndex = if (hasTitle) 1 else 0
        for (i in parasStartIndex until chap.content.size) {
            val para = chap.content[i] as ParagraphContent
            val paraLines = breakLines(para.text, width, config.contentTextSize,
                config.textMargin, config.firstParaIndent)
            for (j in paraLines.indices) {
                val line = paraLines[j]
                if (height < config.contentTextSize) {
                    height = remainedHeight
                    base = startTop
                    chap.pages.add(page)
                    curPageIndex++
                    page = PageData(curPageIndex)
                }
                base += config.contentTextSize
                if (j == 0) {       // 处理段落首行缩进
                    line.apply {
                        this@apply.base = base
                        this@apply.left = left + measureText("缩进", config.contentTextSize)
                    }
                } else {
                    line.apply {
                        this@apply.base = base
                        this@apply.left = left
                    }
                }
                page.lines.add(line)
                base += config.lineMargin
                height -= (config.contentTextSize + config.lineMargin).toInt()
            }
            base += config.paraMargin - config.lineMargin
            height -= config.paraMargin - config.lineMargin      // 处理段落的额外间距
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
                        left += charData.width + config.textMargin
                    }
                } else {
                    line.text.forEach { charData ->
                        canvas.drawText(charData.char.toString(), left, line.base, config.contentPaint)
                        left += charData.width + config.textMargin
                    }
                }
            }
        }
    }

    fun destroy() {
        _config = null
    }

}