package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSeekBar
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.R

class TypesettingFragment(
    private val readview: SimpleReadView
): BaseBottomFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_typesetting, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        // 调节字体大小
        val uiFontSizeNum = view.findViewById<TextView>(R.id.typesetting_font_size_num)
        val uiFontSizeProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_font_size_progress_bar)
        uiFontSizeNum.text = readview.getContentTextSize().toInt().toString()
        view.findViewById<View>(R.id.typesetting_font_size_plus).setOnClickListener {
            val curSize = readview.getContentTextSize()
            val newSize = (curSize + 2F).coerceIn(0F, 100F)
            readview.setContentTextSize(newSize)
            uiFontSizeNum.text = newSize.toInt().toString()
            uiFontSizeProgress.progress = newSize.toInt()
        }
        view.findViewById<View>(R.id.typesetting_font_size_minus).setOnClickListener {
            val curSize = readview.getContentTextSize()
            val newSize = (curSize - 2F).coerceIn(0F, 100F)
            readview.setContentTextSize(newSize)
            uiFontSizeNum.text = newSize.toInt().toString()
            uiFontSizeProgress.progress = newSize.toInt()
        }
        uiFontSizeProgress.apply {
            max = 100
            progress = readview.getContentTextSize().toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        uiFontSizeNum.text = progress.toString()
                        readview.setContentTextSize(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 调整字体间距
        val uiFontSpaceNum = view.findViewById<TextView>(R.id.typesetting_font_space_num)
        val uiFontSpaceProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_font_space_progress_bar)
        uiFontSpaceNum.text = readview.getTextMargin().toInt().toString()
        view.findViewById<View>(R.id.typesetting_font_space_plus).setOnClickListener {
            val curSpace = readview.getTextMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setContentTextMargin(newSpace)
            uiFontSpaceNum.text = newSpace.toInt().toString()
            uiFontSpaceProgress.progress = newSpace.toInt()
        }
        view.findViewById<View>(R.id.typesetting_font_space_minus).setOnClickListener {
            val curSpace = readview.getTextMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setContentTextMargin(newSpace)
            uiFontSpaceNum.text = newSpace.toInt().toString()
            uiFontSpaceProgress.progress = newSpace.toInt()
        }
        uiFontSpaceProgress.apply {
            max = 100
            progress = readview.getTextMargin().toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        // 仅在用户拖动时才更新，避免和加减按钮的点击事件冲突
                        uiFontSpaceNum.text = progress.toString()
                        readview.setContentTextMargin(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 调整行间距
        val uiLineSpaceNum = view.findViewById<TextView>(R.id.typesetting_line_space_num)
        val uiLineSpaceProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_line_space_progress_bar)
        uiLineSpaceNum.text = readview.getLineMargin().toInt().toString()
        view.findViewById<View>(R.id.typesetting_line_space_plus).setOnClickListener {
            val curSpace = readview.getLineMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setLineMargin(newSpace)
            uiLineSpaceNum.text = newSpace.toInt().toString()
            uiLineSpaceProgress.progress = newSpace.toInt()
        }
        view.findViewById<View>(R.id.typesetting_line_space_minus).setOnClickListener {
            val curSpace = readview.getLineMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setLineMargin(newSpace)
            uiLineSpaceNum.text = newSpace.toInt().toString()
            uiLineSpaceProgress.progress = newSpace.toInt()
        }
        uiLineSpaceProgress.apply {
            max = 100
            progress = readview.getLineMargin().toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        uiLineSpaceNum.text = progress.toString()
                        readview.setLineMargin(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 调节段间距
        val uiParaSpaceNum = view.findViewById<TextView>(R.id.typesetting_para_space_num)
        val uiParaSpaceProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_para_space_progress_bar)
        uiParaSpaceNum.text = readview.getParaMargin().toInt().toString()
        view.findViewById<View>(R.id.typesetting_para_space_plus).setOnClickListener {
            val curSpace = readview.getParaMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setParaMargin(newSpace)
            uiParaSpaceNum.text = newSpace.toInt().toString()
            uiParaSpaceProgress.progress = newSpace.toInt()
        }
        view.findViewById<View>(R.id.typesetting_para_space_minus).setOnClickListener {
            val curSpace = readview.getParaMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setParaMargin(newSpace)
            uiParaSpaceNum.text = newSpace.toInt().toString()
            uiParaSpaceProgress.progress = newSpace.toInt()
        }
        uiParaSpaceProgress.apply {
            max = 100
            progress = readview.getParaMargin().toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        uiParaSpaceNum.text = progress.toString()
                        readview.setParaMargin(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 调整页面左间距
        val uiPagePaddingLeftNum = view.findViewById<TextView>(R.id.typesetting_page_padding_left_num)
        val uiPagePaddingLeftProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_page_padding_left_progress_bar)
        uiPagePaddingLeftNum.text = readview.getPagePaddingLeft().toString()
        view.findViewById<View>(R.id.typesetting_page_padding_left_plus).setOnClickListener {
            val curPadding = readview.getPagePaddingLeft()
            val newPadding = (curPadding + 2).coerceIn(0, 200)
            readview.setPagePaddingLeft(newPadding)
            uiPagePaddingLeftNum.text = newPadding.toString()
            uiPagePaddingLeftProgress.progress = newPadding
        }
        view.findViewById<View>(R.id.typesetting_page_padding_left_minus).setOnClickListener {
            val curPadding = readview.getPagePaddingLeft()
            val newPadding = (curPadding - 2).coerceIn(0, 200)
            readview.setPagePaddingLeft(newPadding)
            uiPagePaddingLeftNum.text = newPadding.toString()
            uiPagePaddingLeftProgress.progress = newPadding
        }
        uiPagePaddingLeftProgress.apply {
            max = 200
            progress = readview.getPagePaddingLeft()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        uiPagePaddingLeftNum.text = progress.toString()
                        readview.setPagePaddingLeft(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 调整页面右间距
        val uiPagePaddingRightNum = view.findViewById<TextView>(R.id.typesetting_page_padding_right_num)
        val uiPagePaddingRightProgress = view.findViewById<AppCompatSeekBar>(R.id.typesetting_page_padding_right_progress_bar)
        uiPagePaddingRightNum.text = readview.getPagePaddingRight().toString()
        view.findViewById<View>(R.id.typesetting_page_padding_right_plus).setOnClickListener {
            val curPadding = readview.getPagePaddingRight()
            val newPadding = (curPadding + 2).coerceIn(0, 200)
            readview.setPagePaddingRight(newPadding)
            uiPagePaddingRightNum.text = newPadding.toString()
            uiPagePaddingRightProgress.progress = newPadding
        }
        view.findViewById<View>(R.id.typesetting_page_padding_right_minus).setOnClickListener {
            val curPadding = readview.getPagePaddingRight()
            val newPadding = (curPadding - 2).coerceIn(0, 200)
            readview.setPagePaddingRight(newPadding)
            uiPagePaddingRightNum.text = newPadding.toString()
            uiPagePaddingRightProgress.progress = newPadding
        }
        uiPagePaddingRightProgress.apply {
            max = 200
            progress = readview.getPagePaddingRight()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        uiPagePaddingRightNum.text = progress.toString()
                        readview.setPagePaddingRight(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

}