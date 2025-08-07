package org.peyilo.readview.demo.qidian

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.SimpleReadView
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SimulationPageManagers
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.readview.R
import org.peyilo.readview.copyAssetToInternalStorage
import org.peyilo.readview.fragment.ChapListFragment
import org.peyilo.readview.fragment.ControlPanelFragment
import org.peyilo.readview.fragment.SettingsFragment
import java.io.File

class QidianReadViewActivity : AppCompatActivity() {

    private lateinit var readview: SimpleReadView

    private val chapTitleList: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_qidian_read_view)
        supportActionBar?.hide()

        // 从 Intent 获取文件路径
        val selectedFilePath = intent.getStringExtra("SELECTED_FILE_PATH")
        var selectedFile: File?
        if (selectedFilePath == null) {
            // 未指定本地文件路径，使用内置的本地文件
            val assetFileName = "txts/妖精之诗 作者：尼希维尔特.txt"
            selectedFile = File(this.filesDir, assetFileName.split("/").last())
            if (!selectedFile.exists()) {
                copyAssetToInternalStorage(this, assetFileName, selectedFile)
            }
        } else {
            // 指定了本地文件路径，直接使用该文件
            selectedFile = File(selectedFilePath)
        }

        readview = findViewById(R.id.readview)
        readview.layoutManager = IBookSlideLayoutManager()      // Set the page turning mode to scrolling

        readview.openBook(
            SimpleNativeLoader(selectedFile).apply {
                networkLagFlag = true           // 模拟网络延迟

                // 如果有需要可以指定章节标题正则表达式,用来分割章节
                // addTitleRegex("第\\d+章 .*")
            },
            chapIndex = 1,
            pageIndex = 1,
        )
        readview.setCallback(object : SimpleReadView.Callback {
            override fun onInitToc(success: Boolean) {
                if (!success) return
                for (i in 1..readview.getChapCount()) {
                    chapTitleList.add(readview.getChapTitle(i) ?: "无标题")
                }
            }
        })
        readview.setOnClickRegionListener { xPercent, yPercent ->
            when(xPercent) {
                in 0..30 -> readview.flipToPrevPage()
                in 70..100 -> readview.flipToNextPage()
                else -> {
                    showControlPanel(true)
                }
            }
            true
        }
        readview.preprocessBefore = 0
        readview.preprocessBehind = 0
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
                ControlPanelFragment(onPrevChapClick = {
                    readview.navigateToPrevChapter()
                }, onNextChapClick = {
                    readview.navigateToNextChapter()
                }, onTocBtnClick = {
                    showControlPanel(false)
                    showChapList()
                }, onThemeBtnClick = {
                    // TODO: 切换主题

                }, onSettingsBtnClick = {
                    showControlPanel(false)
                    showSettings()
                }).show(fm, tag)
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
            ChapListFragment(chapTitleList) { chapterIndex ->
                readview.navigateToChapter(chapterIndex + 1)
            }.show(fm, tag)
        }
    }

    /**
     * 显示设置面板
     */
    fun showSettings() {
        val tag = "SettingsFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            SettingsFragment({
                if (readview.layoutManager is CoverLayoutManager) return@SettingsFragment
                readview.layoutManager = CoverLayoutManager()
            }, {
                if (readview.layoutManager is SlideLayoutManager) return@SettingsFragment
                readview.layoutManager = SlideLayoutManager()
            }, {
                if (readview.layoutManager is SimulationPageManagers.Style1) return@SettingsFragment
                readview.layoutManager = SimulationPageManagers.Style1()
            }, {
                if (readview.layoutManager is ScrollLayoutManager) return@SettingsFragment
                readview.layoutManager = ScrollLayoutManager()
            }, {
                if (readview.layoutManager is IBookSlideLayoutManager) return@SettingsFragment
                readview.layoutManager = IBookSlideLayoutManager()
            }).show(fm, tag)
        }
    }
}