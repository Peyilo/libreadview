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

        findViewById<Button>(R.id.btn1).setOnClickListener {
            startActivity(Intent(this@MainActivity, SecondActivity::class.java))
        }

//        findViewById<Button>(R.id.btn2).setOnClickListener {
//
//        }

        findViewById<Button>(R.id.btn3).setOnClickListener {
            startActivity(Intent(this@MainActivity, FourthActivity::class.java))
        }

        findViewById<Button>(R.id.btn4).setOnClickListener {
            startActivity(Intent(this@MainActivity, PageContainerActivity::class.java))
        }
    }
}