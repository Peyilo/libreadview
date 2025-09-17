package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.turning.CoverEffect
import org.peyilo.libreadview.turning.IBookCurlEffect
import org.peyilo.libreadview.turning.IBookSlideEffect
import org.peyilo.libreadview.turning.NoAnimEffects
import org.peyilo.libreadview.turning.ScrollEffect
import org.peyilo.libreadview.turning.SlideEffect
import org.peyilo.readview.R
import org.peyilo.readview.demo.getCurrentThemeIndex
import org.peyilo.readview.demo.setReadViewTheme
import org.peyilo.readview.demo.view.SegmentedLabelControll

class SettingsFragment(
    private val readview: BasicReadView,
): BaseBottomFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        val turningControll = view.findViewById<SegmentedLabelControll>(R.id.setting_turning_controll)
        turningControll.label.text = "翻页效果"
        turningControll.segmented.apply {
            setOptions(listOf("无动画", "覆盖", "滑动", "仿真", "滚动", "Slide"))
            setSelectedIndex(
                when (readview.pageEffect) {
                    is NoAnimEffects.Horizontal -> 0
                    is CoverEffect -> 1
                    is SlideEffect -> 2
                    is IBookCurlEffect -> 3
                    is ScrollEffect -> 4
                    is IBookSlideEffect -> 5
                    else -> 0
                }
            )
            setOnOptionSelectedListener { index ->
                when (index) {
                    0 -> readview.pageEffect = NoAnimEffects.Horizontal()
                    1 -> readview.pageEffect = CoverEffect()
                    2 -> readview.pageEffect = SlideEffect()
                    3 -> readview.pageEffect = IBookCurlEffect()
                    4 -> readview.pageEffect = ScrollEffect()
                    5 -> readview.pageEffect = IBookSlideEffect()
                    else -> throw IllegalStateException("Unexpected value: $index")
                }
            }
        }

        val themeControll = view.findViewById<SegmentedLabelControll>(R.id.setting_theme_controll)
        themeControll.label.text = "阅读主题"
        themeControll.segmented.apply {
            setOptions(listOf("主题1", "主题2", "主题3", "主题4", "主题5"))
            // 这里的主题索引和 ReadView 里的主题索引是一致的
            setSelectedIndex(readview.getCurrentThemeIndex())
            setOnOptionSelectedListener { index ->
                readview.setReadViewTheme(index)
            }
        }
    }

}