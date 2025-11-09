package org.peyilo.readview.demo

import org.mozilla.universalchardet.UniversalDetector
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.load.TxtFileLoader
import org.peyilo.readview.demo.extensions.customChapLoadPage
import java.io.File

// 一个用于打开本地txt文件的阅读页面
class TxtReadActivity : ReadActivity() {

    /**
     * isDemo指的是当前打开的是默认的txt文件，而不是通过选择文件传过来的文件
     */
    private var isDefault = false

    // 检测文件编码格式
    private fun getEncodeing(file: File): String {
        val encoding: String? = UniversalDetector.detectCharset(file)           // 检测编码格式
        return encoding ?: "UTF-8"
    }

    override fun initReadView(readview: BasicReadView) {
        super.initReadView(readview)

        // 从 Intent 获取文件路径
        val selectedFilePath = intent.getStringExtra("SELECTED_FILE_PATH")
        isDefault = selectedFilePath == null
        if (isDefault) readview.customChapLoadPage()

        if (selectedFilePath == null) {
            // 没有指定文件路径，使用assets 目录下的默认txt文件
            val assetFileName = "txts/妖精之诗 作者：尼希维尔特.txt"
            readview.openAssetFile(
                assetFileName,
                encoding = "UTF-8"
            )
        } else {
            // 指定了本地文件路径，直接使用该文件
            val selectedFile = File(selectedFilePath)
            // 加载本地txt文件作为内容
            readview.openBook(
                TxtFileLoader(
                    selectedFile, encoding = getEncodeing(selectedFile)
                ).apply {
                    // 如果有需要可以指定章节标题正则表达式,用来分割章节
                    // addTitleRegex("第\\d+章 .*")
                    networkLagFlag = true
                },
                chapIndex = 1,
                pageIndex = 1,
            )
        }
    }

}