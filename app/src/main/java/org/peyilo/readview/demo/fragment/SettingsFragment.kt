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
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookCurlLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.readview.R

class SettingsFragment(
    private val pageContainer: AbstractPageContainer
): BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        view.findViewById<View>(R.id.pageturn_cover).setOnClickListener {
            if (pageContainer.layoutManager is CoverLayoutManager) return@setOnClickListener
            pageContainer.layoutManager = CoverLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener {
            if (pageContainer.layoutManager is SlideLayoutManager) return@setOnClickListener
            pageContainer.layoutManager = SlideLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener {
            if (pageContainer.layoutManager is IBookCurlLayoutManager) return@setOnClickListener
            pageContainer.layoutManager = IBookCurlLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener {
            if (pageContainer.layoutManager is ScrollLayoutManager) return@setOnClickListener
            pageContainer.layoutManager = ScrollLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener {
            if (pageContainer.layoutManager is IBookSlideLayoutManager) return@setOnClickListener
            pageContainer.layoutManager = IBookSlideLayoutManager()
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