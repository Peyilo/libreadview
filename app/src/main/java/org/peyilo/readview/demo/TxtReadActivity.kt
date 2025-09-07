package org.peyilo.readview.demo

import org.mozilla.universalchardet.UniversalDetector
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.copyAssetToInternalStorage
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

    override fun initReadView(readview: SimpleReadView) {
        super.initReadView(readview)

        // 从 Intent 获取文件路径
        val selectedFilePath = intent.getStringExtra("SELECTED_FILE_PATH")
        isDefault = selectedFilePath == null
        var selectedFile: File?
        if (selectedFilePath == null) {
            // 未指定本地文件路径，使用内置的本地文件
            // 这个文件应该位于 app/src/main/assets/txts 目录下
            // 如果没有这个文件，可以尝试添加一个txt文件到该目录，并且修改 assetFileName 变量
            val assetFileName = "txts/妖精之诗 作者：尼希维尔特.txt"
            selectedFile = File(this.filesDir, assetFileName.split("/").last())
            if (!selectedFile.exists()) {
                try {
                    copyAssetToInternalStorage(this, assetFileName, selectedFile)
                } catch (_: Exception) {
                    throw IllegalStateException("Failed to copy asset file: $assetFileName,\n" +
                            " 为避免txt文件影响代码行数统计，\"app/src/main/assets/txts/妖精之诗 作者：尼希维尔特.txt\"这个文件并没有添加到版本控制中，" +
                            " 如果有需要，你可以尝试添加一个txt文件到 app/src/main/assets/txts 目录，并且修改 assetFileName 变量")
                }
            }
        } else {
            // 指定了本地文件路径，直接使用该文件
            selectedFile = File(selectedFilePath)
        }

        if (isDefault) readview.customChapLoadPage()

        // 加载本地txt文件作为内容
        readview.openBook(
            SimpleNativeLoader(
                selectedFile, encoding = getEncodeing(selectedFile)
            ).apply {
                // 如果有需要可以指定章节标题正则表达式,用来分割章节
                // addTitleRegex("第\\d+章 .*")
            },
            chapIndex = 1,
            pageIndex = 1,
        )
    }

}