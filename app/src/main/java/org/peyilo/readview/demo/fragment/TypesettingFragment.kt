package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.layout.Alignment
import org.peyilo.readview.R
import org.peyilo.readview.demo.view.DoubleValueControll
import org.peyilo.readview.demo.view.DualThumbProgressBar
import org.peyilo.readview.demo.view.PaddingControll
import org.peyilo.readview.demo.view.SegmentedLabelControll

class TypesettingFragment(
    private val readview: BasicReadView
): BaseBottomFragment() {

    private val pagePaddingRange = listOf(
        0..100, // 上边距
        0..100, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    private val headerPaddingRange = listOf(
        0..100, // 上边距
        0..100, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    private val footerPaddingRange = listOf(
        0..100, // 上边距
        0..100, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    private val bodyPaddingRange = listOf(
        0..100, // 上边距
        0..100, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    private val titlePaddingRange = listOf(
        0..100, // 上边距
        0..300, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    private val contentPaddingRange = listOf(
        0..100, // 上边距
        0..100, // 下边距
        0..100, // 左边距
        0..100, // 右边距
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_typesetting, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        val paddingControll = view.findViewById<PaddingControll>(R.id.typesetting_padding_controll)
        paddingControll.forPage()
        paddingControll.segmented.setOnOptionSelectedListener { index ->
            when (index) {
                0 -> paddingControll.forPage()
                1 -> paddingControll.forHeader()
                2 -> paddingControll.forFooter()
                3 -> paddingControll.forBody()
                4 -> paddingControll.forTitle()
                5 -> paddingControll.forContent()
                else -> throw IllegalStateException()
            }
        }
        val textsizeControll = view.findViewById<DoubleValueControll>(R.id.typesetting_textsize_controll)
        textsizeControll.label.text = "文字大小"
        textsizeControll.firstProgressBar.apply {
            setPrimaryThumbText("标题")
        }
        textsizeControll.firstProgressBar.configDualThumbProgressBar(
            10, 60,
            { readview.getTitleTextSize().toInt() },
            { readview.setTitleTextSize(it.toFloat()) }
        )
        textsizeControll.secondProgressBar.apply {
            setPrimaryThumbText("正文")
        }
        textsizeControll.secondProgressBar.configDualThumbProgressBar(
            10, 60,
            { readview.getContentTextSize().toInt() },
            { readview.setContentTextSize(it.toFloat()) }
        )

        val lineParaControll = view.findViewById<DoubleValueControll>(R.id.typesetting_linepara_controll)
        lineParaControll.label.text = "行间距"
        lineParaControll.firstProgressBar.apply {
            setPrimaryThumbText("行距")
        }
        lineParaControll.firstProgressBar.configDualThumbProgressBar(
            0, 100,
            { readview.getContentLineMargin().toInt() },
            { readview.setContentLineMargin(it.toFloat())}
        )
        lineParaControll.secondProgressBar.apply {
            setPrimaryThumbText("段距")
        }
        lineParaControll.secondProgressBar.configDualThumbProgressBar(
            0, 100,
            { readview.getContentParaMargin().toInt() },
            { readview.setContentParaMargin(it.toFloat()) }
        )

        val textMarginControll = view.findViewById<DoubleValueControll>(R.id.typesetting_textmargin_controll)
        textMarginControll.label.text = "文字间距"
        textMarginControll.firstProgressBar.apply {
            setPrimaryThumbText("标题")
        }
        textMarginControll.firstProgressBar.configDualThumbProgressBar(
            0, 20,
            { readview.getTitleTextMargin().toInt() },
            { readview.setTitleTextMargin(it.toFloat())}
        )
        textMarginControll.secondProgressBar.apply {
            setPrimaryThumbText("正文")
        }
        textMarginControll.secondProgressBar.configDualThumbProgressBar(
            0, 20,
            { readview.getContentTextMargin().toInt() },
            { readview.setContentTextMargin(it.toFloat()) }
        )

        val titleAlignControll = view.findViewById<SegmentedLabelControll>(R.id.typesetting_title_align_controll)
        titleAlignControll.label.text = "标题对齐"
        titleAlignControll.segmented.setOptions(listOf("左对齐", "居中", "右对齐"))
        titleAlignControll.segmented.setSelectedIndex(
            when (readview.getTitleAlignment()) {
                Alignment.LEFT -> 0
                Alignment.CENTER -> 1
                Alignment.RIGHT -> 2
            }
        )
        titleAlignControll.segmented.setOnOptionSelectedListener { index ->
            when (index) {
                0 -> readview.setTitleAlignment(Alignment.LEFT)
                1 -> readview.setTitleAlignment(Alignment.CENTER)
                2 -> readview.setTitleAlignment(Alignment.RIGHT)
                else -> throw IllegalStateException()
            }
        }
    }

    private fun DualThumbProgressBar.configDualThumbProgressBar(
        start: Int, end: Int,
        get: () -> Int, set: (Int) -> Unit
        ) {
        max = 100
        val value = get()
        val progress = ((value - start).toFloat() / (end - start) * max).toInt()
        setProgress(progress, needJoin = true)
        onProgressChanged = { progress, fromUser ->
            if (fromUser) {
                val newValue = start + (end - start) * progress / max
                set(newValue)
            }
        }
    }

    private fun PaddingControll.forPage() {
        topProgressBar.configDualThumbProgressBar(
            pagePaddingRange[0].first, pagePaddingRange[0].last,
            { readview.getPagePaddingTop() },
            { readview.setPagePaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            pagePaddingRange[1].first, pagePaddingRange[1].last,
            { readview.getPagePaddingBottom() },
            { readview.setPagePaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            pagePaddingRange[2].first, pagePaddingRange[2].last,
            { readview.getPagePaddingLeft() },
            { readview.setPagePaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            pagePaddingRange[3].first, pagePaddingRange[3].last,
            { readview.getPagePaddingRight() },
            { readview.setPagePaddingRight(it) }
        )
    }

    private fun PaddingControll.forHeader() {
        topProgressBar.configDualThumbProgressBar(
            headerPaddingRange[0].first, headerPaddingRange[0].last,
            { readview.getHeaderPaddingTop() },
            { readview.setHeaderPaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            headerPaddingRange[1].first, headerPaddingRange[1].last,
            { readview.getHeaderPaddingBottom() },
            { readview.setHeaderPaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            headerPaddingRange[2].first, headerPaddingRange[2].last,
            { readview.getHeaderPaddingLeft() },
            { readview.setHeaderPaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            headerPaddingRange[3].first, headerPaddingRange[3].last,
            { readview.getHeaderPaddingRight() },
            { readview.setHeaderPaddingRight(it) }
        )
    }

    private fun PaddingControll.forFooter() {
        topProgressBar.configDualThumbProgressBar(
            footerPaddingRange[0].first, footerPaddingRange[0].last,
            { readview.getFooterPaddingTop() },
            { readview.setFooterPaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            footerPaddingRange[1].first, footerPaddingRange[1].last,
            { readview.getFooterPaddingBottom() },
            { readview.setFooterPaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            footerPaddingRange[2].first, footerPaddingRange[2].last,
            { readview.getFooterPaddingLeft() },
            { readview.setFooterPaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            footerPaddingRange[3].first, footerPaddingRange[3].last,
            { readview.getFooterPaddingRight() },
            { readview.setFooterPaddingRight(it) }
        )
    }

    private fun PaddingControll.forBody() {
        topProgressBar.configDualThumbProgressBar(
            bodyPaddingRange[0].first, bodyPaddingRange[0].last,
            { readview.getBodyPaddingTop() },
            { readview.setBodyPaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            bodyPaddingRange[1].first, bodyPaddingRange[1].last,
            { readview.getBodyPaddingBottom() },
            { readview.setBodyPaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            bodyPaddingRange[2].first, bodyPaddingRange[2].last,
            { readview.getBodyPaddingLeft() },
            { readview.setBodyPaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            bodyPaddingRange[3].first, bodyPaddingRange[3].last,
            { readview.getBodyPaddingRight() },
            { readview.setBodyPaddingRight(it) }
        )
    }

    private fun PaddingControll.forTitle() {
        topProgressBar.configDualThumbProgressBar(
            titlePaddingRange[0].first, titlePaddingRange[0].last,
            { readview.getTitlePaddingTop() },
            { readview.setTitlePaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            titlePaddingRange[1].first, titlePaddingRange[1].last,
            { readview.getTitlePaddingBottom() },
            { readview.setTitlePaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            titlePaddingRange[2].first, titlePaddingRange[2].last,
            { readview.getTitlePaddingLeft() },
            { readview.setTitlePaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            titlePaddingRange[3].first, titlePaddingRange[3].last,
            { readview.getTitlePaddingRight() },
            { readview.setTitlePaddingRight(it) }
        )
    }

    private fun PaddingControll.forContent() {
        topProgressBar.configDualThumbProgressBar(
            contentPaddingRange[0].first, contentPaddingRange[0].last,
            { readview.getContentPaddingTop() },
            { readview.setContentPaddingTop(it) }
        )
        bottomProgressBar.configDualThumbProgressBar(
            contentPaddingRange[1].first, contentPaddingRange[1].last,
            { readview.getContentPaddingBottom() },
            { readview.setContentPaddingBottom(it) }
        )
        leftProgressBar.configDualThumbProgressBar(
            contentPaddingRange[2].first, contentPaddingRange[2].last,
            { readview.getContentPaddingLeft() },
            { readview.setContentPaddingLeft(it) }
        )
        rightProgressBar.configDualThumbProgressBar(
            contentPaddingRange[3].first, contentPaddingRange[3].last,
            { readview.getContentPaddingRight() },
            { readview.setContentPaddingRight(it) }
        )
    }

}