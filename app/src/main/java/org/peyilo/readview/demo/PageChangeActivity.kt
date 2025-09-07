package org.peyilo.readview.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.manager.LayoutManagerFactory
import org.peyilo.readview.R
import org.peyilo.readview.databinding.ActivityPageChangeBinding
import org.peyilo.readview.demo.fragment.SettingsFragment
import org.peyilo.readview.demo.view.GridPage
import kotlin.random.Random

class PageChangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageChangeBinding
    private val pageContainer: PageContainer get() = binding.pageContainer

    private val colors = mutableListOf<Pair<Int, Int>>()

    private fun generateRandomColor(): Int {
        val red = Random.Default.nextInt(0, 256)
        val green = Random.Default.nextInt(0, 256)
        val blue = Random.Default.nextInt(0, 256)
        return Color.rgb(red, green, blue)          // 不透明随机颜色
    }

    private var state = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repeat(0) {
            val randomColor = generateRandomColor()
            colors.add(Pair(randomColor, it + 1))
        }

        pageContainer.initPageIndex(1)
        pageContainer.layoutManager = LayoutManagerFactory.create(LayoutManagerFactory.IBOOK_SLIDE)
        pageContainer.adapter = ColorAdapter(colors)


        findViewById<Button>(R.id.btn_update).setOnClickListener {
            val size = colors.size
            repeat(size) {
                val randomColor =  if (state) Color.WHITE else generateRandomColor()
                val number = colors[it].second
                colors.removeAt(it)
                colors.add(it, Pair(randomColor, number))
            }
            state = !state
            pageContainer.adapter.notifyItemRangeChanged(0, colors.size)
        }

        var counter = 0
        findViewById<Button>(R.id.btn_insert).setOnClickListener {
            colors.add(Pair(Color.WHITE, counter))
            counter += 1
            pageContainer.adapter.notifyItemInserted(colors.size)
        }

        findViewById<Button>(R.id.btn_remove).setOnClickListener {
            colors.removeAt(0)
            pageContainer.adapter.notifyItemRemoved(0)
        }

        pageContainer.setOnClickRegionListener { xPercent, _ ->
            when (xPercent) {
                in 0..30 -> pageContainer.flipToPrevPage()
                in 70..100 -> pageContainer.flipToNextPage()
                else -> showSettings()
            }
            true
        }
    }

    class ColorAdapter(private val items: List<Pair<Int, Int>>) :
        AbstractPageContainer.Adapter<ColorAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(itemView: View) : AbstractPageContainer.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = GridPage(parent.context)
            return ColorViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val itemView = holder.itemView as GridPage
            itemView.setBackgroundColor(items[position].first)
            itemView.content.number = items[position].second
            itemView.progress.text = "${position + 1}/${items.size}"
            itemView.header.text = "这是第${position + 1}页"
        }

        override fun getItemCount(): Int = items.size
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
                if (LayoutManagerFactory.getType(pageContainer.layoutManager)
                    == LayoutManagerFactory.COVER
                ) return@SettingsFragment
                pageContainer.layoutManager =
                    LayoutManagerFactory.create(LayoutManagerFactory.COVER)
            }, {
                if (LayoutManagerFactory.getType(pageContainer.layoutManager)
                    == LayoutManagerFactory.SLIDE
                ) return@SettingsFragment
                pageContainer.layoutManager =
                    LayoutManagerFactory.create(LayoutManagerFactory.SLIDE)
            }, {
                if (LayoutManagerFactory.getType(pageContainer.layoutManager)
                    == LayoutManagerFactory.CURL
                ) return@SettingsFragment
                pageContainer.layoutManager = LayoutManagerFactory.create(LayoutManagerFactory.CURL)
            }, {
                if (LayoutManagerFactory.getType(pageContainer.layoutManager)
                    == LayoutManagerFactory.SCROLL
                ) return@SettingsFragment
                pageContainer.layoutManager =
                    LayoutManagerFactory.create(LayoutManagerFactory.SCROLL)
            }, {
                if (LayoutManagerFactory.getType(pageContainer.layoutManager)
                    == LayoutManagerFactory.IBOOK_SLIDE
                ) return@SettingsFragment
                pageContainer.layoutManager =
                    LayoutManagerFactory.create(LayoutManagerFactory.IBOOK_SLIDE)
            }).show(fm, tag)
        }
    }

}