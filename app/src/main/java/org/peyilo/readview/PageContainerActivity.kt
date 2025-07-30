package org.peyilo.readview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.manager.SimulationPageManagers
import org.peyilo.readview.ui.GridPage
import kotlin.random.Random

class PageContainerActivity : AppCompatActivity() {

    private lateinit var pageContainerTop: PageContainer
    private val colors = mutableListOf<Pair<Int, Int>>()

    private fun generateRandomColor(): Int {
        val red = Random.nextInt(0, 256)
        val green = Random.nextInt(0, 256)
        val blue = Random.nextInt(0, 256)
        return Color.rgb(red, green, blue)          // 不透明随机颜色
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page_container)
        supportActionBar?.hide()

        repeat(10000) {
//            val randomColor = generateRandomColor()
            val randomColor = Color.WHITE
            colors.add(Pair(randomColor, it + 1))
        }

        pageContainerTop = findViewById(R.id.pageContainer)
        pageContainerTop.initPageIndex(1)
        pageContainerTop.pageManager = SimulationPageManagers.Style1()

        pageContainerTop.adapter = ColorAdapter(colors)
        pageContainerTop.setOnClickRegionListener{ xPercent, _ ->
            when (xPercent) {
                in 0..30 -> pageContainerTop.flipToPrevPage()
                in 70..100 -> pageContainerTop.flipToNextPage()
                else -> return@setOnClickRegionListener false
            }
            true
        }
    }

    class ColorAdapter(private val items: List<Pair<Int, Int>>) :
        PageContainer.Adapter<ColorAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(itemView: View) : PageContainer.ViewHolder(itemView)

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
}