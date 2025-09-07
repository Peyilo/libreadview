package org.peyilo.readview.test

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.test.view.MyGLSurfaceView

class OpenGLActivity : AppCompatActivity() {
    private var glSurfaceView: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        glSurfaceView = MyGLSurfaceView(this)

        setContentView(glSurfaceView)
    }
}