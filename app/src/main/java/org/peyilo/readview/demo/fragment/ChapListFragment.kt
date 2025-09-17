package org.peyilo.readview.demo.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as materialR
import org.peyilo.readview.R
import org.peyilo.readview.demo.adapter.TocListAdapter

class ChapListFragment(
    private val chapters: List<String>,
    private val onChapterSelected: (Int) -> Unit
) : BaseBottomFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_toc, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.toc_list)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = TocListAdapter(chapters) { index ->
            onChapterSelected(index)
            dismiss()
        }

        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setDimAmount(0.01f)            // 设置阴影程度
        }
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme  // 自定义圆角样式
    }

    override fun processContainer() {
        // 使得 BottomSheetDialog 显示的区域可以包含导航栏,避免总是被design_bottom_sheet设置一个24dp的paddingBottom
        val container = dialog!!.findViewById<FrameLayout>(materialR.id.container)!!
        container.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets -> insets }
            it.post {
                it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, 0)
            }
        }
    }
}