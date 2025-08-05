package org.peyilo.readview.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.peyilo.readview.R

class ControlPanelFragment(
    private val onTocBtnClick: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_control_panel, container, false)
        val toc = view.findViewById<View>(R.id.menu_toc)
        toc.setOnClickListener { onTocBtnClick() }
        return view
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)              // 设置阴影程度
        return dialog
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme       // 自定义圆角样式
    }

}