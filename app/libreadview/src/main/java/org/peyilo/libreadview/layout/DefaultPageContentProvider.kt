package org.peyilo.libreadview.layout

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.libreadview.basic.ReadStyle
import org.peyilo.libreadview.content.ParagraphContent
import org.peyilo.libreadview.content.ReadChapter
import org.peyilo.libreadview.content.TitleContent
import org.peyilo.libreadview.data.page.CharData
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.data.page.StringLineData

/**
 * 在给定ReadConfig下，负责完成ReadChap的分页，并且负载将PageData绘制到Canvas上
 */
class DefaultPageContentProvider(config: ReadStyle): PageContentProvider {

    private var _config: ReadStyle? = config
    private val config get() = _config!!

    private val measuredPaint = Paint()

    private val hitghlightPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    private val remainedBodyWidth get() = config.bodyWidth - config.bodyPaddingRight - config.bodyPaddingLeft
    private val remainedBodyHeight get() = config.bodyHeight - config.bodyPaddingTop - config.bodyPaddingBottom
    private val bodyLeft get() = config.bodyPaddingLeft.toFloat()
    private val bodyTop get() = config.bodyPaddingTop.toFloat()
    private val bodyRight get() = config.bodyWidth - config.bodyPaddingRight
    private val bodyBottom get()  = config.bodyHeight - config.bodyPaddingBottom

    private val remainedTitleWidth get() = remainedBodyWidth - config.titlePaddingLeft - config.titlePaddingRight
    private val remainedTitleHeight get() = remainedBodyHeight - config.titlePaddingTop - config.titlePaddingBottom

    private val titleLeft get() = bodyLeft + config.titlePaddingLeft
    private val titleTop get() = bodyTop + config.titlePaddingTop
    private val titleRight get() = bodyRight - config.titlePaddingRight
    private val titleBottom get() = bodyBottom - config.titlePaddingBottom

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
        var line = StringLineData()
        var w = width - offset
        var dimen: Float
        text.forEach {
            dimen = measureText(it, size)
            if (w < dimen) {    // 剩余宽度已经不足以留给该字符
                lineList.add(line)
                line = StringLineData()
                w = width
            }
            w -= dimen + textMargin
            line.add(CharData(it).apply {
                this@apply.width = dimen
            })
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
        var base = bodyTop
        val left = bodyLeft
        var curPageIndex = 1
        var page = PageData(curPageIndex)

        // 开始章节标题的处理
        val firstContent = chap.content[0]
        val hasTitle = firstContent is TitleContent
        if (hasTitle) {
            val titleLines = breakLines(firstContent.text,
                remainedTitleWidth, config.titleTextSize, config.titleTextMargin, 0F)
            val titleFontMetrics = config.titlePaint.fontMetrics
            base += config.titlePaddingTop
            val titleLeft = left + config.titlePaddingLeft
            titleLines.forEach {
                base += -titleFontMetrics.top
                it.apply {                  // 设置TextLine的base、left
                    val lineWidth = computeWidth(config.titleTextMargin)
                    this@apply.left = when (config.titleAlignment) {
                        Alignment.LEFT -> titleLeft
                        Alignment.CENTER -> titleLeft + (remainedTitleWidth - lineWidth) / 2
                        Alignment.RIGHT -> titleRight - lineWidth
                    }
                    it.isTitleLine = true
                    val charBase = base
                    var charLeft = this@apply.left
                    this@apply.text.forEach { charData ->
                        charData.baseline = charBase
                        charData.left = charLeft
                        charData.right = charData.width + charData.left
                        charData.bottom = charBase + titleFontMetrics.bottom
                        charData.top = charBase + titleFontMetrics.top
                        charData.ascent = charBase + titleFontMetrics.ascent
                        charData.descent = charBase + titleFontMetrics.descent
                        charLeft += charData.width + config.titleTextMargin
                    }
                }
                page.elements.add(it)
                base += titleFontMetrics.bottom
                base += config.titleLineMargin
            }
            var offset = 0F     // 正文内容的偏移
            if (titleLines.isNotEmpty()) {
                base += config.titlePaddingBottom - config.titleLineMargin
                offset += config.titlePaddingBottom + config.titlePaddingTop
                offset += (titleFontMetrics.bottom - titleFontMetrics.top) * titleLines.size
                offset += config.titleLineMargin * (titleLines.size - 1)

                // 处理正文内容的偏移
                base += config.contentPaddingTop
                offset += config.contentPaddingTop
            }
            height -= offset
        }
        // 如果剩余空间已经不足以再添加一行，就换成下一页
        if (height < config.contentTextSize + config.contentPaddingBottom) {
            height = remainedBodyHeight
            base = bodyTop
            chap.pages.add(page)
            curPageIndex++
            page = PageData(curPageIndex)
        }
        // 开始正文内容的处理
        val parasStartIndex = if (hasTitle) 1 else 0
        val contentFontMetrics = config.contentPaint.fontMetrics
        for (i in parasStartIndex until chap.content.size) {
            val para = chap.content[i] as ParagraphContent
            val paraLines = breakLines(para.text,
                remainedContentWidth,
                config.contentTextSize,
                config.contentTextMargin, config.firstParaIndent)
            for (j in paraLines.indices) {
                val line = paraLines[j]
                // 如果剩余空间已经不足以再添加一行，就换成下一页
                // 字体高度 = fontMetrics.bottom - fontMetrics.top
                if (height < contentFontMetrics.bottom - contentFontMetrics.top
                    + config.contentPaddingBottom) {
                    height = remainedBodyHeight
                    base = bodyTop + config.contentPaddingTop
                    chap.pages.add(page)
                    curPageIndex++
                    page = PageData(curPageIndex)
                }
                base += -contentFontMetrics.top
                line.apply {
                    // 处理段落首行缩进
                    val lineOffset = if (j == 0) {
                        this@apply.isFirstLineOfParagraph = true
                        config.firstParaIndent
                    } else {
                        0F
                    }
                    this@apply.left = left + config.contentPaddingLeft + lineOffset
                    this@apply.top = base + contentFontMetrics.top
                    val lineWidth = computeWidth(config.contentTextMargin)
                    this@apply.right = this@apply.left + lineWidth
                    this@apply.bottom = base + contentFontMetrics.bottom

                    val charBase = base
                    var charLeft = this@apply.left
                    this@apply.text.forEach { charData ->
                        charData.baseline = charBase
                        charData.left = charLeft
                        charData.right = charData.width + charData.left
                        charData.bottom = charBase + contentFontMetrics.bottom
                        charData.top = charBase + contentFontMetrics.top
                        charData.ascent = charBase + contentFontMetrics.ascent
                        charData.descent = charBase + contentFontMetrics.descent
                        charLeft += charData.width + config.contentTextMargin
                    }
                }
                page.elements.add(line)
                base += contentFontMetrics.bottom
                base += config.contentLineMargin
                height -= -contentFontMetrics.top + contentFontMetrics.bottom + config.contentLineMargin
            }
            base += config.contentParaMargin - config.contentLineMargin
            height -= config.contentParaMargin - config.contentLineMargin      // 处理段落的额外间距
        }
        chap.pages.add(page)
    }

    // 绘制一行文字
    private fun drawStringLine(line: StringLineData, canvas: Canvas) {
        val textMargin = if (line.isTitleLine) config.titleTextMargin else config.contentTextMargin
        val textPaint = if (line.isTitleLine) config.titlePaint else config.contentPaint
        var lastCharIsHighlighted = false
        line.text.forEach { charData ->
            // 绘制高亮背景
            if (charData.isHighlighted) {
                canvas.drawRect(
                    charData.left - if (lastCharIsHighlighted) textMargin else 0F,
                    charData.ascent,
                    charData.right,
                    charData.descent,
                    hitghlightPaint.apply {
                        color = charData.highlightColor
                    }
                )
                lastCharIsHighlighted = true
            } else {
                lastCharIsHighlighted = false
            }
            canvas.drawText(
                charData.char.toString(),
                charData.left,
                charData.baseline,
                textPaint
            )
        }
    }

    /**
     * 在文字绘制中，每行文字的高度为 fontMetrics.bottom - fontMetrics.ascent，
     * 但是在绘制高亮背景时，需要使用 fontMetrics.ascent 和 fontMetrics.descent 来确定背景的上下边界
     */
    override fun drawPage(page: PageData, canvas: Canvas) {
        page.elements.forEach { line ->
            when (line) {
                is StringLineData -> drawStringLine(line, canvas)
            }
        }
    }

    fun destroy() {
        _config = null
    }

}