package org.peyilo.readview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.databinding.ActivityTestBinding

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