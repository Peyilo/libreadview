package org.peyilo.readview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.libreadview.ReadView
import org.peyilo.libreadview.manager.SimulationPageManagers

class ReadViewActivity : AppCompatActivity() {

    private lateinit var readview: ReadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_view)
        supportActionBar?.hide()

        readview = findViewById(R.id.readview)

        readview.pageManager = SimulationPageManagers.Style1()

    }
}