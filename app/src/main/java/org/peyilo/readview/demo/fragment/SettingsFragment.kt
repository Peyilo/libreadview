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
import org.peyilo.readview.R

class SettingsFragment(
    private val onCoverClick: () -> Unit,
    private val onSlideClick: () -> Unit,
    private val onSimulationClick: () -> Unit,
    private val onScrollClick: () -> Unit,
    private val onIBookClick: () -> Unit,
): BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        view.findViewById<View>(R.id.pageturn_cover).setOnClickListener { onCoverClick() }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener { onSlideClick() }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener { onSimulationClick() }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener { onScrollClick() }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener { onIBookClick() }
        return view
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