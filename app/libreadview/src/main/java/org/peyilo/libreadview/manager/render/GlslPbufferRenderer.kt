package org.peyilo.libreadview.manager.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

class GlslPbufferRenderer(context: Context) {

    // --------- EGL / GL 状态 ----------
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglConfig: EGLConfig? = null
    private var eglSurface: EGLSurface? = null

    // 用 PBuffer 离屏渲染
    private var surfaceWidth = -1
    private var surfaceHeight = -1

    // 程序/句柄
    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uResolutionLoc = 0
    private var uMouseLoc = 0
    private var uTimeLoc = 0
    private var uCh0Loc = 0
    private var uCh1Loc = 0

    // 顶点数据（两个三角形覆盖全屏）
    private val quadVertices = floatArrayOf(
        -1f,  1f,   // 左上
        -1f, -1f,   // 左下
        1f, -1f,   // 右下
        1f,  1f    // 右上
    )
    private val quadUV = floatArrayOf(
        0f, 0f,   // 左上
        0f, 1f,   // 左下
        1f, 1f,   // 右下
        1f, 0f    // 右上
    )
    private val quadIndices = shortArrayOf(0, 1, 2, 0, 2, 3)

    private val vb: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadVertices).position(0) }
    private val tb: FloatBuffer = ByteBuffer.allocateDirect(quadUV.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadUV).position(0) }
    private val ib: ShortBuffer = ByteBuffer.allocateDirect(quadIndices.size * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(quadIndices).position(0) }

    // 读回像素复用缓冲与位图
    private var pixelBuffer: IntBuffer? = null
    private var scratchBitmap: Bitmap? = null

    // 纹理缓存（每帧重建更安全；如需优化可做持久化+尺寸变更检测）
    private fun createTextureFromBitmap(bmp: Bitmap): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        return tex[0]
    }

    // 顶点着色器（最小化）
    private val vertexShaderCode = loadShaderFromAssets(context, "shader/vertex.glsl")

    // 片段着色器（示例：按手势 X 做左右分割；你可以替换为自己的 Shadertoy 逻辑）
    //
    // iChannel0 = topBitmap, iChannel1 = bottomBitmap
    // iMouse.xy = 当前触摸，zw = 按下位置（像素）
    private val fragmentShaderCode = loadShaderFromAssets(context, "shader/fragment.glsl")

    private fun loadShaderFromAssets(context: Context, filename: String): String {
        context.assets.open(filename).use { input ->
            return input.bufferedReader().use { it.readText() }
        }
    }

    // ---------------- 对外主方法 ----------------
    fun render(
        canvas: Canvas,
        topBitmap: Bitmap,
        bottomBitmap: Bitmap,
        mouseX: Float, mouseY: Float, mouseZ: Float, mouseW: Float
    ) {
        val w = canvas.width
        val h = canvas.height
        ensureEGL(w, h)
        ensureProgram()

        // 1) GL 渲染到 PBuffer
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // uniforms
        GLES20.glUniform3f(uResolutionLoc, w.toFloat(), h.toFloat(), 1f)
        val timeSec = SystemClock.uptimeMillis() / 1000f
        GLES20.glUniform1f(uTimeLoc, timeSec)
        GLES20.glUniform4f(uMouseLoc, mouseX, mouseY, mouseZ, mouseW)

        // 纹理
        val tex0 = createTextureFromBitmap(topBitmap)
        val tex1 = createTextureFromBitmap(bottomBitmap)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex0)
        GLES20.glUniform1i(uCh0Loc, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex1)
        GLES20.glUniform1i(uCh1Loc, 1)

        // 顶点/UV
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vb)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, tb)

        // 绘制
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, quadIndices.size, GLES20.GL_UNSIGNED_SHORT, ib)

        // 清理临时纹理（避免累积）
        GLES20.glDeleteTextures(1, intArrayOf(tex0), 0)
        GLES20.glDeleteTextures(1, intArrayOf(tex1), 0)

        // 2) 读回像素（RGBA）到复用缓冲
        val buf = ensurePixelBuffer(w, h)
        buf.position(0)
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

        // 3) 写入（或复用）位图并画到 Canvas
        // OpenGL 原点在左下，Canvas/Bitmap 原点在左上 —— 这里用 Canvas 变换翻转 Y，避免手动倒置数组
        val bmp = ensureScratchBitmap(w, h)
        bmp.setPixels(buf.array(), 0, w, 0, 0, w, h)

        canvas.withTranslation(0f, h.toFloat()) {
            // 垂直翻转：围绕画布中轴翻转后再平移
            scale(1f, -1f)
            drawBitmap(bmp, 0f, 0f, null)
        }
    }

    // ---------------- 内部：初始化 & 复用 ----------------
    private fun ensureEGL(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (eglDisplay != null && w == surfaceWidth && h == surfaceHeight && eglSurface != null) return

        releaseSurfaceOnly()

        if (eglDisplay == null) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)

            val cfgAttrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numCfg = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, cfgAttrs, 0, configs, 0, 1, numCfg, 0)
            eglConfig = configs[0]

            val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
        }

        // PBuffer surface
        val pbufAttrs = intArrayOf(
            EGL14.EGL_WIDTH, w,
            EGL14.EGL_HEIGHT, h,
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufAttrs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        surfaceWidth = w
        surfaceHeight = h
        // 像素缓存/位图会在下次 render 时按新尺寸重建
        pixelBuffer = null
        scratchBitmap = null
    }

    private fun ensureProgram() {
        if (program != 0) return

        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        GLES20.glUseProgram(program)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uResolutionLoc = GLES20.glGetUniformLocation(program, "iResolution")
        uMouseLoc = GLES20.glGetUniformLocation(program, "iMouse")
        uTimeLoc = GLES20.glGetUniformLocation(program, "iTime")
        uCh0Loc = GLES20.glGetUniformLocation(program, "iChannel0")
        uCh1Loc = GLES20.glGetUniformLocation(program, "iChannel1")
    }

    private fun ensurePixelBuffer(w: Int, h: Int): IntBuffer {
        val need = w * h
        val cur = pixelBuffer
        return if (cur == null || cur.capacity() != need) {
            val buf = IntArray(need)
            pixelBuffer = IntBuffer.wrap(buf)
            pixelBuffer!!
        } else cur
    }

    private fun ensureScratchBitmap(w: Int, h: Int): Bitmap {
        val bmp = scratchBitmap
        return if (bmp == null || bmp.width != w || bmp.height != h) {
            val newBmp = createBitmap(w, h)
            scratchBitmap = newBmp
            newBmp
        } else bmp
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun releaseSurfaceOnly() {
        eglSurface?.let {
            EGL14.eglDestroySurface(eglDisplay, it)
        }
        eglSurface = null
        surfaceWidth = -1
        surfaceHeight = -1
    }

    // 可选：供外部在销毁时调用，释放全部 EGL 资源
    fun release() {
        releaseSurfaceOnly()
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (eglContext != null) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = null
        }
        if (eglDisplay != null) {
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = null
        }
    }
}
