package org.peyilo.readview.demo.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.peyilo.libreadview.util.LogHelper
import org.peyilo.readview.R

class ControlPanelFragment(
    private val onPrevChapClick: () -> Unit,
    private val onNextChapClick: () -> Unit,
    private val onTocBtnClick: () -> Unit,
    private val onThemeBtnClick: () -> Unit,
    private val onSettingsBtnClick: () -> Unit,
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "ControlPanelFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_control_panel, container, false)
        view.findViewById<View>(R.id.menu_toc).setOnClickListener { onTocBtnClick() }
        view.findViewById<View>(R.id.menu_theme).setOnClickListener { onThemeBtnClick() }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener { onSettingsBtnClick() }

        view.findViewById<View>(R.id.menu_prev_chap).setOnClickListener { onPrevChapClick() }
        view.findViewById<View>(R.id.menu_next_chap).setOnClickListener {
            onNextChapClick()
        }
        LogHelper.d(TAG, "onCreateView")
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)              // 设置阴影程度
        LogHelper.d(TAG, "onCreateDialog")
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
        LogHelper.d(TAG, "onViewCreated")
    }

}