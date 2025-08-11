package org.peyilo.readview

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.utils.LogHelper
import org.peyilo.readview.data.User
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

        val user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("user", User::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("user")
        }

        LogHelper.d(TAG, "user: $user")

    }
}