package org.peyilo.readview

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.databinding.ActivityTestBinding
import androidx.core.graphics.createBitmap

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
    }
}