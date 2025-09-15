package org.peyilo.readview.demo.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.libreadview.util.LogHelper
import org.peyilo.readview.R
import org.peyilo.readview.demo.ReadActivity
import org.peyilo.readview.demo.view.DualThumbProgressBar

class ControlPanelFragment(
    private val readview: SimpleReadView,
    private val readActivity: ReadActivity,
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "ControlPanelFragment"
    }

    private var progressBar: DualThumbProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_control_panel, container, false)
        initView(view)
        LogHelper.d(TAG, "onCreateView")
        return view
    }

    private fun initView(view: View) {
        progressBar = view.findViewById(R.id.menu_progress_bar)

        view.findViewById<View>(R.id.menu_toc).setOnClickListener {
            readActivity.showControlPanel(false)
            readActivity.showChapList { chapIndex, res ->
                if (res) progressBar!!.setProgress(chapIndex - 1)
            }
        }
        view.findViewById<View>(R.id.menu_theme).setOnClickListener {
            readActivity.showControlPanel(false)
            readActivity.showUiPanel()
        }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener {
            readActivity.showControlPanel(false)
            readActivity.showSettings()
        }
        view.findViewById<View>(R.id.menu_prev_chap).setOnClickListener {
            readview.navigateToPrevChapter().let {
                if (it) progressBar!!.setProgress(progressBar!!.getProgress() - 1)
            }
        }
        view.findViewById<View>(R.id.menu_next_chap).setOnClickListener {
            readview.navigateToNextChapter().let {
                if (it) progressBar!!.setProgress(progressBar!!.getProgress() + 1)
            }
        }

        progressBar!!.apply {
            max = readview.getChapCount() - 1
            setProgress(readview.getCurChapIndex() - 1, needJoin = true)
            onProgressChanged = { progress, fromUser ->
                if (fromUser) {
                    val chapIndex = progress + 1
                    readview.navigateToChapter(chapIndex)
                }
            }
        }
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