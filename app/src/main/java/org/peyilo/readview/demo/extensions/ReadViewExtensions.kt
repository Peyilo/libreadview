package org.peyilo.readview.demo.extensions

import android.content.Context
import android.view.View
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.readview.demo.view.QidianChapLoadPage

/**
 * 使用自定义的章节加载页面QidianChapLoadPage
 */
fun BasicReadView.customChapLoadPage() {
    setPageDelegate(object : BasicReadView.PageDelegate() {
        override fun createChapLoadPage(context: Context): View = QidianChapLoadPage(context)
        override fun bindChapLoadPage(
            page: View,
            title: String,
            chapIndex: Int
        ) {
            val page = page as QidianChapLoadPage
            page.title = title
            page.chapIndex = chapIndex
        }
    })
}
