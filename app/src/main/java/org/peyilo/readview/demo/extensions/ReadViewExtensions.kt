package org.peyilo.readview.demo.extensions

import android.content.Context
import android.view.View
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.basic.ReadStyleBuilder
import org.peyilo.readview.demo.ReadViewTheme
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

private val curThemeIndex = mutableMapOf<BasicReadView, Int>()

fun BasicReadView.setReadViewTheme(idx: Int = 0) {
    val curTheme = ReadViewTheme.getTheme(idx)
    ReadStyleBuilder(this).apply {
        setContentTextColor(curTheme.contentColor)
        setTitleTextColor(curTheme.titleColor)
        setPageBackground(curTheme.background)
        setHeaderAndFooterTextColor(curTheme.headerAndFooterTextColor)
    }.build()
    curThemeIndex[this] = idx
}

fun BasicReadView.getCurrentThemeIndex(): Int {
    return curThemeIndex[this] ?: 0
}

// 清除缓存的主题索引，避免内存泄漏
fun BasicReadView.clearReadViewThemeCache() {
    curThemeIndex.remove(this)
}