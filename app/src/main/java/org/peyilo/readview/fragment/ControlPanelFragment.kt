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
    private val onPrevChapClick: () -> Unit,
    private val onNextChapClick: () -> Unit,
    private val onTocBtnClick: () -> Unit,
    private val onThemeBtnClick: () -> Unit,
    private val onSettingsBtnClick: () -> Unit,
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_control_panel, container, false)
        view.findViewById<View>(R.id.menu_toc).setOnClickListener { onTocBtnClick() }
        view.findViewById<View>(R.id.menu_theme).setOnClickListener { onThemeBtnClick() }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener { onSettingsBtnClick() }

        view.findViewById<View>(R.id.menu_prev_chap).setOnClickListener { onPrevChapClick() }
        view.findViewById<View>(R.id.menu_next_chap).setOnClickListener { onNextChapClick() }
        return view
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)              // 设置阴影程度
        return dialog
    }

}