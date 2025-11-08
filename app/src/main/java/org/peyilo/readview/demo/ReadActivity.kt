package org.peyilo.readview.demo

import android.os.Bundle
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.turning.ScrollEffect
import org.peyilo.readview.BaseActivity
import org.peyilo.readview.databinding.ActivityUniversalReadViewBinding
import org.peyilo.readview.demo.extensions.clearReadViewThemeCache
import org.peyilo.readview.demo.extensions.setReadViewTheme
import org.peyilo.readview.demo.fragment.ChapListFragment
import org.peyilo.readview.demo.fragment.ControlPanelFragment
import org.peyilo.readview.demo.fragment.SettingsFragment
import org.peyilo.readview.demo.fragment.TypesettingFragment
import java.util.concurrent.atomic.AtomicBoolean

open class ReadActivity: BaseActivity() {

    protected lateinit var binding: ActivityUniversalReadViewBinding

    val readview: BasicReadView get() = binding.readview

    protected val chapTitleList: MutableList<String> = mutableListOf()

    protected val tocInitStatus = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUniversalReadViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        initReadView(readview)
    }

    protected open fun initReadView(readview: BasicReadView) {
        // 设置翻页模式
        readview.pageEffect = ScrollEffect()

        // 当目录完成初始化后，获取章节标题列表
        readview.setCallback(object : BasicReadView.Callback {
            override fun onInitTocResult(success: Boolean) {
                if (!success) return
                for (i in 1..readview.getChapCount()) {
                    val chapTitle = readview.getChapTitle(i)
                    chapTitleList.add(chapTitle)
                }
                tocInitStatus.set(true)
            }
        })

        // 点击屏幕区域翻页或显示菜单
        readview.setOnClickRegionListener { xPercent, _ ->
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
        readview.preprocessBefore = 0
        readview.preprocessBehind = 0

        // 设置标题、正文字体
         readview.setTitleTypeface(FontManager.getTypeface("linja-waso-Bold"))
         readview.setContentTypeface(FontManager.getTypeface("linja-waso"))

        // 设置主题
        readview.setReadViewTheme()

        // 给readpage的页眉退出按钮设置点击事件
        readview.setHeaderQuitBtnOnClickListener {
            // 可以在这里做一些提示保存进度等操作
            // 获取当前章节索引和页码：
            // readview.getCurChapIndex()
            // readview.getCurChapPageIndex()
           finish()
        }
    }

    /**
     * 控制底部菜单栏的显示
     */
    fun showControlPanel(visible: Boolean) {
        if (!tocInitStatus.get()) return            // 目录未初始化完成前，不显示菜单
        val tag = "ControlPanelFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (visible) {          // 避免重复显示
            if (existing == null) {
                ControlPanelFragment(readview, this).show(fm, tag)
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
    fun showChapList(callback: ((Int, Boolean) -> Unit)? = null) {
        val tag = "ChapListFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            ChapListFragment(chapTitleList) { chapterIndex ->
                readview.navigateToChapter(chapterIndex + 1).let {
                    callback?.invoke(chapterIndex + 1, it)
                }
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
            SettingsFragment(this).show(fm, tag)
        }
    }

    /**
     * 显示排版设置面板
     */
    fun showTypesettingPanel() {
        val tag = "TypesettingFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            TypesettingFragment(readview).show(fm, tag)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readview.clearReadViewThemeCache()
    }

}