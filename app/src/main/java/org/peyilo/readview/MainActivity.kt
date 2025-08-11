package org.peyilo.readview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
import org.peyilo.libreadview.utils.LogHelper
import org.peyilo.readview.data.User
import org.peyilo.readview.databinding.ActivityMainBinding
import org.peyilo.readview.demo.NetworkLoadActivity
import org.peyilo.readview.demo.pagecontainer.PageChangeActivity
import org.peyilo.readview.demo.pagecontainer.PageContainerActivity
import org.peyilo.readview.demo.qidian.QidianReadViewActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MainActivity"
    }

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

    // 注册结果回调
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data

            val returned: Book? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra("book", Book::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra("book")
            }

            // —— 验证序列化是否“完好” ——
            if (returned != null) {
                // 你也可以拿它和原始对象对比
                LogHelper.d(TAG, "returned: $returned")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.btnPagecontainerDemo.setOnClickListener {
            startActivity(Intent(this@MainActivity, PageContainerActivity::class.java))
        }

        binding.btnSelectFile.setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        binding.btnReadviewDemo.setOnClickListener {
            val intent = Intent(this@MainActivity, QidianReadViewActivity::class.java)
            startActivity(intent)
        }

        binding.btnPageChangeDemo.setOnClickListener {
            startActivity(Intent(this@MainActivity, PageChangeActivity::class.java))
        }

        binding.btnNetwork.setOnClickListener {
            val intent = Intent(this@MainActivity, NetworkLoadActivity::class.java)
            activityResultLauncher.launch(intent)
            startActivity(intent)
        }

        binding.btnTest.setOnClickListener {
            val intent = Intent(this@MainActivity, TestActivity::class.java)
            val user = User("peyilo", 20, "Shanghai")
            user.apply {
                id = 2025
                what = "peyilo"
                obj = intent
            }
            intent.putExtra("user", user)
            startActivity(intent)
        }

    }


}