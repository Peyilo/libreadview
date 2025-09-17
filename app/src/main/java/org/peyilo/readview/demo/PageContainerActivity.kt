package org.peyilo.readview.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.manager.IBookCurlLayoutManager
import org.peyilo.readview.databinding.ActivityPageContainerBinding
import org.peyilo.readview.demo.fragment.PageTurnChangeFragment
import org.peyilo.readview.demo.view.GridPage
import kotlin.random.Random

class PageContainerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageContainerBinding

    private val pageContainer: PageContainer get() = binding.pageContainer
    private val colors = mutableListOf<Pair<Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // Generate demo data: 1000 pages with random background colors and numbers
        repeat(1000) {
            val randomColor = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            colors.add(Pair(randomColor, it + 1))
        }

        // Set the initial page index
        pageContainer.initPageIndex(2)

        // Choose a page animation manager (see options below)
        pageContainer.layoutManager = IBookCurlLayoutManager()

        // Set adapter
        pageContainer.adapter = ColorAdapter(colors)

        // Handle tap regions: left 30% = previous, right 30% = next
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
        val tag = "PageTurnChangeFragment"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            PageTurnChangeFragment(pageContainer).show(fm, tag)
        }
    }
}