package org.peyilo.readview

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        findViewById<Button>(R.id.btn_pagecontainer_demo).setOnClickListener {
            startActivity(Intent(this@MainActivity, PageContainerActivity::class.java))
        }

        findViewById<Button>(R.id.btn_readview_demo).setOnClickListener {
            startActivity(Intent(this@MainActivity, ReadViewActivity::class.java))
        }

    }
}