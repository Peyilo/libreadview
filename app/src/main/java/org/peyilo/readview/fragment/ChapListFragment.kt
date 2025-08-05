package org.peyilo.readview.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.peyilo.readview.R
import org.peyilo.readview.adapter.TocListAdapter

class ChapListFragment(
    private val chapters: List<String>,
    private val onChapterSelected: (Int) -> Unit
) : BottomSheetDialogFragment() {

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
            window?.setDimAmount(0f) // 设置阴影程度
        }
    }


    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme // 自定义圆角样式
    }

}