package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 对于通过GLSL实现的翻页动画，可以使用这个PageGLSurfaceView来承载。
 * AbstractPageContainer启用GLSL支持后，PageGLSurfaceView将会被创建并添加到AbstractPageContainer中。
 */
class PageGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val downPos = PointF()
    private val touchPos = PointF()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                touchPos.x = event.x
                touchPos.y = event.y
            }
        }
        return true
    }

    class PageRenderer: Renderer {
        override fun onDrawFrame(gl: GL10?) {
            TODO("Not yet implemented")
        }

        override fun onSurfaceChanged(
            gl: GL10?,
            width: Int,
            height: Int
        ) {
            TODO("Not yet implemented")
        }

        override fun onSurfaceCreated(
            gl: GL10?,
            config: EGLConfig?
        ) {
            TODO("Not yet implemented")
        }
    }
}