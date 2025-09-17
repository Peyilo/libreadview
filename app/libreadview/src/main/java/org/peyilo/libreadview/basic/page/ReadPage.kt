package org.peyilo.libreadview.basic.page

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import org.peyilo.libreadview.R

/**
 * 一个用展示小说内容的页面
 * TODO: 页面中需要支持各种各样的视图，如video、picture、text、button
 */
class ReadPage (
    context: Context, attrs: AttributeSet? = null
): BaseReadPage(context, attrs) {

    init {
        bindLayout(
            R.layout.libreadview_item_read_page,
            R.id.page_content,
            R.id.page_header,
            R.id.page_footer
            )
    }

    lateinit var progress: TextView
        private set
    lateinit var clock: TextView
        private set
    lateinit var chapTitle: TextView
        private set
    lateinit var quitImgView: ImageView
        private set

    override fun bindLayout(layoutId: Int, contentId: Int, headerId: Int, footerId: Int) {
        super.bindLayout(layoutId, contentId, headerId, footerId)
        progress = root.findViewById(R.id.page_footer_progress)
        clock = root.findViewById(R.id.page_footer_clock)
        chapTitle = root.findViewById(R.id.page_header_chap_title)
        quitImgView = root.findViewById(R.id.page_header_quit_img)
    }

}