package org.peyilo.readview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.ReadView
import org.peyilo.libreadview.loader.SimpleNativeLoader
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.utils.LogHelper
import java.io.File

class ReadViewActivity : AppCompatActivity() {

    private lateinit var readview: ReadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_view)
        supportActionBar?.hide()

        // 从 Intent 获取文件路径
        val filePath = intent.getStringExtra("SELECTED_FILE_PATH")

        readview = findViewById(R.id.readview)

        readview.layoutManager = IBookSlideLayoutManager()

        filePath?.let {
            val selectedFile = File(it)
            LogHelper.d("ReadViewActivity", "File selected: ${selectedFile.absolutePath}")

            readview.openBook(
                SimpleNativeLoader(selectedFile).apply {
                    addTitleRegex("第\\d+章 .*")
                    networkLagFlag = true
                },
//                SimpleTextLoader("你好啊"),
                chapIndex = 1,
                pageIndex = 1,
            )
        }

        readview.setOnClickRegionListener { xPercent, yPercent ->
            when(xPercent) {
                in 0..30 -> readview.navigateToPrevChapter()
                in 70..100 -> readview.navigateToNextChapter()
                else -> Unit
            }
            true
        }
    }
}