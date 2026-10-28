package org.peyilo.readview.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.databinding.ActivityTestBinding
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.turning.IosCurlEffect
import org.peyilo.readview.demo.PageContainerActivity

class TestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TestActivity"
    }

    private lateinit var binding: ActivityTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        val reader = PageContainer(this).apply { pageEffect = IosCurlEffect() }
        binding.root.addView(reader)
        reader.adapter = PageContainerActivity.ColorAdapter(
            (1..12).map { number -> android.graphics.Color.rgb(240 - number * 4, 230, 210 + number * 2) to number }
        )
    }
}
