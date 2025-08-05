package org.peyilo.readview

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.ReadView
import org.peyilo.libreadview.ReadViewCallback
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.utils.LogHelper
import org.peyilo.readview.fragment.ChapListFragment
import org.peyilo.readview.fragment.ControlPanelFragment
import java.io.File

class ReadViewActivity : AppCompatActivity() {

    private lateinit var readview: ReadView

    private val chapterList: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_view)

        supportActionBar?.hide()
        // 从 Intent 获取文件路径
        val filePath = intent.getStringExtra("SELECTED_FILE_PATH")
        readview = findViewById(R.id.readview)
        readview.layoutManager = IBookSlideLayoutManager()
        filePath?.let {
            val selectedFile = File(it)
            LogHelper.d("ReadViewActivity", "File selected: ${selectedFile.absolutePath}")
            readview.openBook(
                SimpleNativeLoader(selectedFile).apply {
                    addTitleRegex("第\\d+章 .*")
                    networkLagFlag = true
                },
                chapIndex = 1,
                pageIndex = 1,
            )
        }
        readview.setCallback(object : ReadViewCallback {
            override fun onInitToc(success: Boolean) {
                if (!success) return
                for (i in 1..readview.getChapCount()) {
                    chapterList.add(readview.getChap(i))
                }
            }
        })
        readview.setOnClickRegionListener { xPercent, yPercent ->
            when(xPercent) {
                in 0..30 -> readview.navigateToPrevChapter()
                in 70..100 -> readview.navigateToNextChapter()
                else -> {
                    showControlPanel(true)
                }
            }
            true
        }
    }

    /**
     * 控制底部菜单栏的显示
     */
    private fun showControlPanel(visible: Boolean) {
        val tag = "ControlPanelFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (visible) {          // 避免重复显示
            if (existing == null) {
                ControlPanelFragment {
                    showControlPanel(false)
                    showChapList()
                }.show(fm, tag)
            }
        } else {
            // 如果已显示，就 dismiss 掉
            if (existing is ControlPanelFragment) {
                existing.dismiss()
            }
        }
    }

    /**
     * 显示目录列表视图
     */
    fun showChapList() {
        val tag = "ChapListFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            ChapListFragment(chapterList) { chapterIndex ->
                readview.navigateToChapter(chapterIndex + 1)
            }.show(fm, tag)
        }
    }

}