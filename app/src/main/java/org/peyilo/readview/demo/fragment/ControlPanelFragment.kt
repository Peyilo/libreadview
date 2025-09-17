package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.util.LogHelper
import org.peyilo.readview.R
import org.peyilo.readview.demo.ReadActivity
import org.peyilo.readview.demo.view.DualThumbProgressBar

class ControlPanelFragment(
    private val readview: BasicReadView,
    private val readActivity: ReadActivity,
) : BaseBottomFragment() {

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
        view.findViewById<View>(R.id.menu_typesetting).setOnClickListener {
            readActivity.showControlPanel(false)
            readActivity.showTypesettingPanel()
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

}