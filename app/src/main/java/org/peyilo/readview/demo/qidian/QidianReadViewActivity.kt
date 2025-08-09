package org.peyilo.readview.demo.qidian

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.SimpleReadView
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SimulationPageManagers
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.readview.AppPreferences
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
        val isDemo = selectedFilePath == null
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
        initPageIndex(selectedFile, isDemo)
    }

    /**
     * isDemo指的是当前打开的是默认的txt文件，而不是通过选择文件传过来的文件
     */
    private fun initPageIndex(selectedFile: File, isDemo: Boolean) {
        readview = findViewById(R.id.readview)
        readview.layoutManager = getLayoutManager(AppPreferences.getFlipMode())      // Set the page turning mode

        readview.openBook(
            SimpleNativeLoader(selectedFile).apply {
                networkLagFlag = true           // 模拟网络延迟

                // 如果有需要可以指定章节标题正则表达式,用来分割章节
                // addTitleRegex("第\\d+章 .*")
            },
            chapIndex = if (isDemo) AppPreferences.getChapIndex() else 1,
            pageIndex = if (isDemo) AppPreferences.getChapPageIndex() else 1,
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
        readview.preprocessBefore = 1
        readview.preprocessBehind = 1

        if (isDemo) {                   // 为demo专门定制的ChapLoadPage
            readview.setPageDelegate(object : SimpleReadView.PageDelegate() {
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
     * 显示设置面板: 切换翻页模式
     */
    fun showSettings() {
        val tag = "SettingsFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            SettingsFragment({
                if (readview.layoutManager is CoverLayoutManager) return@SettingsFragment
                readview.layoutManager = CoverLayoutManager()
                AppPreferences.setFlipMode(getFlipMode(readview.layoutManager))
            }, {
                if (readview.layoutManager is SlideLayoutManager) return@SettingsFragment
                readview.layoutManager = SlideLayoutManager()
                AppPreferences.setFlipMode(getFlipMode(readview.layoutManager))
            }, {
                if (readview.layoutManager is SimulationPageManagers.Style1) return@SettingsFragment
                readview.layoutManager = SimulationPageManagers.Style1()
                AppPreferences.setFlipMode(getFlipMode(readview.layoutManager))
            }, {
                if (readview.layoutManager is ScrollLayoutManager) return@SettingsFragment
                readview.layoutManager = ScrollLayoutManager()
                AppPreferences.setFlipMode(getFlipMode(readview.layoutManager))
            }, {
                if (readview.layoutManager is IBookSlideLayoutManager) return@SettingsFragment
                readview.layoutManager = IBookSlideLayoutManager()
                AppPreferences.setFlipMode(getFlipMode(readview.layoutManager))
            }).show(fm, tag)
        }
    }

    fun getFlipMode(layoutManager: AbstractPageContainer.LayoutManager): Int {
        return when (layoutManager) {
            is CoverLayoutManager -> 0
            is SlideLayoutManager -> 1
            is SimulationPageManagers.Style1 -> 2
            is ScrollLayoutManager -> 3
            is IBookSlideLayoutManager -> 4
            else -> throw IllegalStateException()
        }
    }

    fun getLayoutManager(flipModel: Int): AbstractPageContainer.LayoutManager {
        return when(flipModel) {
            0 -> CoverLayoutManager()
            1 -> SlideLayoutManager()
            2 -> SimulationPageManagers.Style1()
            3 -> ScrollLayoutManager()
            4 -> IBookSlideLayoutManager()
            else -> throw IllegalStateException()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppPreferences.setChapIndex(readview.getCurChapIndex())
        AppPreferences.setChapPageIndex(readview.getCurChapPageIndex())
    }
}