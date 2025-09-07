package org.peyilo.readview.test.view

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class MyGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer: MyRenderer

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0
        renderer = MyRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y   // 不要翻转

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                renderer.setMouse(x, y, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                renderer.setMouse(x, y, renderer.mouseZ, renderer.mouseW)
            }
            MotionEvent.ACTION_UP -> {
//                renderer.setMouse(0f, 0f, 0f, 0f)
            }
        }
        return true
    }

}