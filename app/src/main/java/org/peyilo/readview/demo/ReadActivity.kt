package org.peyilo.readview.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookCurlLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.databinding.ActivityUniversalReadViewBinding
import org.peyilo.readview.demo.fragment.ChapListFragment
import org.peyilo.readview.demo.fragment.ControlPanelFragment
import org.peyilo.readview.demo.fragment.SettingsFragment

open class ReadActivity: AppCompatActivity() {

    protected lateinit var binding: ActivityUniversalReadViewBinding

    protected val readview: SimpleReadView get() = binding.readview

    protected val chapTitleList: MutableList<String> = mutableListOf()

    private var curThemeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUniversalReadViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        initReadView(readview)
    }

    protected open fun initReadView(readview: SimpleReadView) {
        // 设置翻页模式
        readview.layoutManager = IBookSlideLayoutManager()

        // 当目录完成初始化后，获取章节标题列表
        readview.setCallback(object : SimpleReadView.Callback {
            override fun onInitTocResult(success: Boolean) {
                if (!success) return
                for (i in 1..readview.getChapCount()) {
                    val chapTitle = readview.getChapTitle(i)
                    chapTitleList.add(chapTitle)
                }
            }
        })

        // 点击屏幕区域翻页或显示菜单
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

        // 设置预加载章节数，如下设置会产生这样的效果：加载指定章节时，同时预加载前后各1章
        readview.preprocessBefore = 1
        readview.preprocessBehind = 1

        // 设置主题
        updateReadViewTheme()
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
                    // 切换主题
                    curThemeIndex++
                    updateReadViewTheme()
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
                if (readview.layoutManager is IBookCurlLayoutManager) return@SettingsFragment
                readview.layoutManager = IBookCurlLayoutManager()
            }, {
                if (readview.layoutManager is ScrollLayoutManager) return@SettingsFragment
                readview.layoutManager = ScrollLayoutManager()
            }, {
                if (readview.layoutManager is IBookSlideLayoutManager) return@SettingsFragment
                readview.layoutManager = IBookSlideLayoutManager()
            }).show(fm, tag)
        }
    }

    fun updateReadViewTheme() {
        val curTheme = ReadViewTheme.allThemes[(curThemeIndex) % ReadViewTheme.allThemes.size]
        readview.setContentTextColor(curTheme.contentColor)
        readview.setTitleTextColor(curTheme.titleColor)
        readview.setPageBackground(curTheme.background)
        readview.setHeaderAndFooterTextColor(curTheme.headerAndFooterTextColor)
        readview.setContentTextSize(curTheme.contentSize)
        readview.setTitleTextSize(curTheme.titleSize)
    }
}