package org.peyilo.readview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.demo.NetworkLoadActivity
import org.peyilo.readview.demo.pagecontainer.PageChangeActivity
import org.peyilo.readview.demo.pagecontainer.PageContainerActivity
import org.peyilo.readview.demo.qidian.QidianReadViewActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // 文件选择成功，开始读取并保存文件
            saveFileToAppDirectory(uri)
        } else {
            // 用户取消了选择
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    // 读取文件并保存到应用的私有目录
    private fun saveFileToAppDirectory(uri: Uri) {
        try {
            // 获取输入流
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.let { input ->
                // 创建目标文件，在应用的私有目录中
                val selectedFile = File(filesDir, uri.path!!.split("/").let { it[it.size - 1] })
                val outputStream = FileOutputStream(selectedFile)
                input.copyTo(outputStream)
                input.close()
                outputStream.close()
                val intent = Intent(this@MainActivity, QidianReadViewActivity::class.java)
                intent.putExtra("SELECTED_FILE_PATH", selectedFile.absolutePath)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        findViewById<Button>(R.id.btn_pagecontainer_demo).setOnClickListener {
            startActivity(Intent(this@MainActivity, PageContainerActivity::class.java))
        }

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btn_readview_demo).setOnClickListener {
            val intent = Intent(this@MainActivity, QidianReadViewActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_page_change_demo).setOnClickListener {
            startActivity(Intent(this@MainActivity, PageChangeActivity::class.java))
        }

        findViewById<Button>(R.id.btn_network).setOnClickListener {
            startActivity(Intent(this@MainActivity, NetworkLoadActivity::class.java))
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            startActivity(Intent(this@MainActivity, TestActivity::class.java))
        }

    }


}