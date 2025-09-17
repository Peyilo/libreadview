package org.peyilo.readview.demo.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.R as materialR

open class BaseBottomFragment: BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setDimAmount(0f)            // 设置阴影程度
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        processContainer()
    }

    protected open fun processContainer() {
        // 使得 BottomSheetDialog 显示的区域可以包含导航栏,避免总是被design_bottom_sheet设置一个24dp的paddingBottom
        val bottomSheet = dialog!!.findViewById<FrameLayout>(materialR.id.design_bottom_sheet)!!
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