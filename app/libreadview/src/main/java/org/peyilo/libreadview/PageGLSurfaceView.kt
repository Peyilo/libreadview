package org.peyilo.libreadview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import org.peyilo.libreadview.util.LogHelper

/**
 * 对于通过GLSL实现的翻页动画，可以使用这个PageGLSurfaceView来承载。
 */
class PageGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private var _renderer: PageRenderer? = null
    private val renderer: PageRenderer get() =  _renderer!!

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun setRenderer(renderer: Renderer) {
        if (renderer is PageRenderer) {
            super.setRenderer(renderer)
            _renderer = renderer
        } else {
            throw IllegalStateException("Renderer must be a PageRenderer")
        }
    }

    companion object {
        private const val TAG = "PageGLSurfaceView"
    }

    abstract class PageRenderer: Renderer {
        val downPos = PointF()
        val touchPos = PointF()

        fun setDownPos(downPosX: Float, downPosY: Float) {
            downPos.set(downPosX, downPosY)
            LogHelper.d(TAG, "setDownPos: $downPos")
        }

        fun setTouchPos(touchPosX: Float, touchPosY: Float) {
            touchPos.set(touchPosX, touchPosY)
            LogHelper.d(TAG, "setTouchPos: $touchPos")
        }

        /**
         * 加载shader
         * @param type GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
         * @param code shader代码
         * @return shader句柄
         */
        protected fun loadShader(type: Int, code: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, code)
                GLES20.glCompileShader(shader)
            }
        }

        /**
         * 加载bitmap作为纹理
         * @param bitmap 位图
         * @return 纹理句柄
         */
        protected fun loadTexture(bitmap: Bitmap): Int {
            val textureHandle = IntArray(1)
            GLES20.glGenTextures(1, textureHandle, 0)

            if (textureHandle[0] != 0) {
                val options = BitmapFactory.Options()
                options.inScaled = false

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }

            return textureHandle[0]
        }
    }
}