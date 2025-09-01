package org.peyilo.readview.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SimpleCurlPageManager
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.readview.databinding.ActivityUniversalReadViewBinding
import org.peyilo.readview.demo.qidian.QidianChapLoadPage
import org.peyilo.readview.fragment.ChapListFragment
import org.peyilo.readview.fragment.ControlPanelFragment
import org.peyilo.readview.fragment.SettingsFragment
import org.peyilo.readview.loader.BiqugeBookLoader

class NetworkLoadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUniversalReadViewBinding

    private val readview: SimpleReadView get() = binding.readview

    private val chapTitleList: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUniversalReadViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        readview.layoutManager = IBookSlideLayoutManager()      // Set the page turning mode

        // 网络加载: https://www.yuyouku.com/book/185030
        readview.openBook(
            BiqugeBookLoader(185030),
            chapIndex = 1,
            pageIndex = 1,
        )

        readview.setCallback(object : SimpleReadView.Callback {
            override fun onInitToc(success: Boolean) {
                if (!success) return
                for (i in 1..readview.getChapCount()) {
                    val chapTitle = readview.getChapTitle(i)
                    chapTitleList.add(chapTitle)
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

        // 为demo专门定制的ChapLoadPage
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
            }, {
                if (readview.layoutManager is SlideLayoutManager) return@SettingsFragment
                readview.layoutManager = SlideLayoutManager()
            }, {
                if (readview.layoutManager is SimpleCurlPageManager) return@SettingsFragment
                readview.layoutManager = SimpleCurlPageManager()
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