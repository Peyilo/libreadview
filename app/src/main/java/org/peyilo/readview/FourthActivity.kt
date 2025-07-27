package org.peyilo.readview

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class FourthActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val colors = mutableListOf<Int>()

    private fun generateRandomColor(): Int {
        val red = Random.nextInt(0, 256)
        val green = Random.nextInt(0, 256)
        val blue = Random.nextInt(0, 256)
        return Color.rgb(red, green, blue)          // 不透明随机颜色
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fourth)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.adapter = NameAdapter(colors)
        repeat(10000) {
            val randomColor = generateRandomColor()
            colors.add(randomColor)
        }
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    class NameAdapter(private val items: List<Int>) :
        RecyclerView.Adapter<NameAdapter.NameViewHolder>() {

        inner class NameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val view: View = itemView.findViewById(R.id.view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NameViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_name, parent, false)
            return NameViewHolder(view)
        }

        override fun onBindViewHolder(holder: NameViewHolder, position: Int) {
            holder.view.setBackgroundColor(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}