package org.peyilo.readview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.readview.ui.GridPage
import kotlin.random.Random

class PageChangeActivity : AppCompatActivity() {

    private lateinit var pageContainer: PageContainer
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
        setContentView(R.layout.activity_page_change)
        supportActionBar?.hide()

        repeat(0) {
            val randomColor = generateRandomColor()
//            val randomColor = Color.WHITE
            colors.add(Pair(randomColor, it + 1))
        }

        pageContainer = findViewById(R.id.pageContainer)
        pageContainer.initPageIndex(1)
        pageContainer.layoutManager = IBookSlideLayoutManager()
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