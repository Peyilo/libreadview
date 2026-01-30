package org.peyilo.libreadview.basic.page

import android.content.Context
import android.util.AttributeSet
import org.peyilo.libreadview.R

class ContentOnlyReadPage (
    context: Context, attrs: AttributeSet? = null
) : BaseReadPage(context, attrs) {

    init {
        bindLayout(
            R.layout.libreadview_item_content_only_read_page,
            R.id.page_content
        )
    }

}