package org.peyilo.readview.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.basic.ContentOnlyView
import org.peyilo.libreadview.turning.ScrollEffect
import org.peyilo.readview.databinding.ActivityTestBinding
import org.peyilo.readview.demo.loader.BiqugeBookLoader

class TestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TestActivity"
    }

    private lateinit var binding: ActivityTestBinding

    val readview: ContentOnlyView get() = binding.readview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        // 设置翻页模式
        readview.pageEffect = ScrollEffect()
        // 点击屏幕区域翻页或显示菜单
        readview.setOnClickRegionListener { xPercent, _ ->
            when(xPercent) {
                in 0..30 -> readview.flipToPrevPage()
                in 70..100 -> readview.flipToNextPage()
                else -> Unit
            }
            true
        }

        // 设置预加载章节数，如下设置会产生这样的效果：加载指定章节时，同时预加载前后各1章
        readview.preprocessBefore = 1
        readview.preprocessBehind = 1

        // 网络加载: https://www.yuyouku.com/book/185030
        readview.openBook(
            // BookLoader制定了如何加载章节内容
            BiqugeBookLoader(185030),
            chapIndex = 1,
            pageIndex = 1,
        )
    }
}