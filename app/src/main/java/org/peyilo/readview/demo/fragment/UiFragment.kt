package org.peyilo.readview.demo.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookCurlLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.R
import org.peyilo.readview.demo.setReadViewTheme

class UiFragment(
    private val readview: SimpleReadView
): BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ui, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        view.findViewById<View>(R.id.pageturn_cover).setOnClickListener {
            if (readview.layoutManager is CoverLayoutManager) return@setOnClickListener
            readview.layoutManager = CoverLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener {
            if (readview.layoutManager is SlideLayoutManager) return@setOnClickListener
            readview.layoutManager = SlideLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener {
            if (readview.layoutManager is IBookCurlLayoutManager) return@setOnClickListener
            readview.layoutManager = IBookCurlLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener {
            if (readview.layoutManager is ScrollLayoutManager) return@setOnClickListener
            readview.layoutManager = ScrollLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener {
            if (readview.layoutManager is IBookSlideLayoutManager) return@setOnClickListener
            readview.layoutManager = IBookSlideLayoutManager()
        }

        view.findViewById<View>(R.id.theme_1).setOnClickListener {
            readview.setReadViewTheme(0)
        }
        view.findViewById<View>(R.id.theme_2).setOnClickListener {
            readview.setReadViewTheme(1)
        }
        view.findViewById<View>(R.id.theme_3).setOnClickListener {
            readview.setReadViewTheme(2)
        }
        view.findViewById<View>(R.id.theme_4).setOnClickListener {
            readview.setReadViewTheme(3)
        }
        view.findViewById<View>(R.id.theme_5).setOnClickListener {
            readview.setReadViewTheme(4)
        }

        // 调节字体大小
        view.findViewById<TextView>(R.id.ui_font_size_num).text =
            readview.getContentTextSize().toInt().toString()
        view.findViewById<View>(R.id.ui_font_size_plus).setOnClickListener {
            val curSize = readview.getContentTextSize()
            val newSize = (curSize + 2F).coerceIn(0F, 100F)
            readview.setContentTextSize(newSize)
            view.findViewById<TextView>(R.id.ui_font_size_num).text = newSize.toInt().toString()
        }
        view.findViewById<View>(R.id.ui_font_size_minus).setOnClickListener {
            val curSize = readview.getContentTextSize()
            val newSize = (curSize - 2F).coerceIn(0F, 100F)
            readview.setContentTextSize(newSize)
            view.findViewById<TextView>(R.id.ui_font_size_num).text = newSize.toInt().toString()
        }

        // 调整字体间距
        view.findViewById<TextView>(R.id.ui_font_space_num).text =
            readview.getTextMargin().toInt().toString()
        view.findViewById<View>(R.id.ui_font_space_plus).setOnClickListener {
            val curSpace = readview.getTextMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setTextMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_font_space_num).text =
                newSpace.toInt().toString()
        }
        view.findViewById<View>(R.id.ui_font_space_minus).setOnClickListener {
            val curSpace = readview.getTextMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setTextMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_font_space_num).text =
                newSpace.toInt().toString()
        }

        // 调整行间距
        view.findViewById<TextView>(R.id.ui_line_space_num).text =
            readview.getLineMargin().toInt().toString()
        view.findViewById<View>(R.id.ui_line_space_plus).setOnClickListener {
            val curSpace = readview.getLineMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setLineMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_line_space_num).text =
                newSpace.toInt().toString()
        }
        view.findViewById<View>(R.id.ui_line_space_minus).setOnClickListener {
            val curSpace = readview.getLineMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setLineMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_line_space_num).text =
                newSpace.toInt().toString()
        }

        // 调节段间距
        view.findViewById<TextView>(R.id.ui_para_space_num).text =
            readview.getParaMargin().toInt().toString()
        view.findViewById<View>(R.id.ui_para_space_plus).setOnClickListener {
            val curSpace = readview.getParaMargin()
            val newSpace = (curSpace + 2F).coerceIn(0F, 100F)
            readview.setParaMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_para_space_num).text =
                newSpace.toInt().toString()
        }
        view.findViewById<View>(R.id.ui_para_space_minus).setOnClickListener {
            val curSpace = readview.getParaMargin()
            val newSpace = (curSpace - 2F).coerceIn(0F, 100F)
            readview.setParaMargin(newSpace)
            view.findViewById<TextView>(R.id.ui_para_space_num).text =
                newSpace.toInt().toString()
        }
    }



    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)              // 设置阴影程度
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 使得 BottomSheetDialog 显示的区域可以包含导航栏,避免总是被design_bottom_sheet设置一个24dp的paddingBottom
        val bottomSheet = dialog!!.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!
        bottomSheet.post {
            bottomSheet.let {
                ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                    // 不调用 super，不加 padding
                    insets
                }
            }
        }
    }

}