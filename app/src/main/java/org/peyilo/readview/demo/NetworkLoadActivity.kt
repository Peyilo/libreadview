package org.peyilo.readview.demo

import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.readview.demo.extensions.customChapLoadPage
import org.peyilo.readview.demo.loader.BiqugeBookLoader

// 一个用于打开网络小说的阅读页面
class NetworkLoadActivity : ReadActivity() {

    override fun initReadView(readview: BasicReadView) {
        super.initReadView(readview)
        readview.customChapLoadPage()

        // 网络加载: https://www.yuyouku.com/book/185030
        readview.openBook(
            // BookLoader制定了如何加载章节内容
            BiqugeBookLoader(185030),
            chapIndex = 1,
            pageIndex = 1,
        )
    }

}