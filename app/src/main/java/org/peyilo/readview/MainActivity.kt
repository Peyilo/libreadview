package org.peyilo.readview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
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

    private var selectedFile: File? = null

    private var recyclerView: RecyclerView? = null

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
            val intent = Intent(this@MainActivity, ReadViewActivity::class.java)

            // 传递文件路径或文件对象
            intent.putExtra("SELECTED_FILE_PATH", "/data/user/0/org.peyilo.readview/files/关于转生后我成为枕头公主这件事.txt")

            // 启动 ReadViewActivity
            startActivity(intent)
        }

//        recyclerView!!.adapter

    }

    // 读取文件并保存到应用的私有目录
    private fun saveFileToAppDirectory(uri: Uri) {
        try {
            // 获取输入流
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.let { input ->
                // 创建目标文件，在应用的私有目录中
                selectedFile = File(filesDir, uri.path!!.split("/").let { it[it.size - 1] })

                // 创建输出流
                val outputStream = FileOutputStream(selectedFile)

                // 复制输入流的内容到目标文件
                input.copyTo(outputStream)

                // 关闭流
                input.close()
                outputStream.close()

                // 文件已成功保存到应用目录
                Toast.makeText(this, "File saved to: ${selectedFile!!.absolutePath}", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }
}