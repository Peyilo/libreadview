package org.peyilo.readview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import org.peyilo.readview.databinding.ActivityMainBinding
import org.peyilo.readview.demo.NetworkLoadActivity
import org.peyilo.readview.demo.PageChangeActivity
import org.peyilo.readview.demo.PageContainerActivity
import org.peyilo.readview.demo.TxtReadActivity
import org.peyilo.readview.test.OpenGLActivity
import org.peyilo.readview.test.TestActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // 文件选择成功，读取文件并保存到应用的私有目录
            // 获取输入流
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.let { input ->
                // 创建目标文件，在应用的私有目录中
                val selectedFile = File(filesDir, uri.path!!.split("/").let { it[it.size - 1] })
                val outputStream = FileOutputStream(selectedFile)
                input.copyTo(outputStream)
                input.close()
                outputStream.close()
                val intent = Intent(this@MainActivity, TxtReadActivity::class.java)
                intent.putExtra("SELECTED_FILE_PATH", selectedFile.absolutePath)
                startActivity(intent)
            }
        } else {
            // 用户取消了选择
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // 为各个按钮设置点击事件，启动相应的Activity
        binding.btnPagecontainerDemo.setOnClickListener {
            startActivity(Intent(this@MainActivity, PageContainerActivity::class.java))
        }

        binding.btnSelectFile.setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        binding.btnReadviewDemo.setOnClickListener {
            val intent = Intent(this@MainActivity, TxtReadActivity::class.java)
            startActivity(intent)
        }

        binding.btnPageChangeDemo.setOnClickListener {
            startActivity(Intent(this@MainActivity, PageChangeActivity::class.java))
        }

        binding.btnNetwork.setOnClickListener {
            val intent = Intent(this@MainActivity, NetworkLoadActivity::class.java)
            startActivity(intent)
        }

        binding.btnTest.setOnClickListener {
            val intent = Intent(this@MainActivity, TestActivity::class.java)
            startActivity(intent)
        }

        binding.btnOpengl.setOnClickListener {
            val intent = Intent(this@MainActivity, OpenGLActivity::class.java)
            startActivity(intent)
        }

    }


}