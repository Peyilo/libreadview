package org.peyilo.readview.demo.pagecontainer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SimulationPageManagers
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.readview.R
import org.peyilo.readview.databinding.ActivityPageChangeBinding
import org.peyilo.readview.fragment.SettingsFragment
import org.peyilo.readview.ui.GridPage
import kotlin.random.Random

class PageChangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageChangeBinding
    private val pageContainer: PageContainer get() = binding.pageContainer

    private val colors = mutableListOf<Pair<Int, Int>>()

    private fun generateRandomColor(): Int {
        val red = Random.nextInt(0, 256)
        val green = Random.nextInt(0, 256)
        val blue = Random.nextInt(0, 256)
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
        pageContainer.layoutManager = CoverLayoutManager()
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
                if (pageContainer.layoutManager is CoverLayoutManager) return@SettingsFragment
                pageContainer.layoutManager = CoverLayoutManager()
            }, {
                if (pageContainer.layoutManager is SlideLayoutManager) return@SettingsFragment
                pageContainer.layoutManager = SlideLayoutManager()
            }, {
                if (pageContainer.layoutManager is SimulationPageManagers.Style1) return@SettingsFragment
                pageContainer.layoutManager = SimulationPageManagers.Style1()
            }, {
                if (pageContainer.layoutManager is ScrollLayoutManager) return@SettingsFragment
                pageContainer.layoutManager = ScrollLayoutManager()
            }, {
                if (pageContainer.layoutManager is IBookSlideLayoutManager) return@SettingsFragment
                pageContainer.layoutManager = IBookSlideLayoutManager()
            }).show(fm, tag)
        }
    }

}