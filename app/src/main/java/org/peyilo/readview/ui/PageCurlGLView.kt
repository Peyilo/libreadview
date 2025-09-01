package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PageCurlGLView(context: Context) : GLSurfaceView(context) {
    private val renderer = PageCurlRenderer(context)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY // 或者按需 RENDERMODE_WHEN_DIRTY
    }

    fun setProgress(p: Float) { renderer.progress = p.coerceIn(0f, 1f) }
    fun setAngle(deg: Int) { renderer.angleDeg = deg }
    fun setRadius(r: Float) { renderer.radius = r }
    fun setRoll(on: Boolean) { renderer.roll = on }
    fun setUncurl(on: Boolean) { renderer.uncurl = on }
    fun setGreyback(on: Boolean) { renderer.greyback = on }
    fun setOpacity(v: Float) { renderer.opacity = v }
    fun setShadow(v: Float) { renderer.shadow = v }
    fun setBitmaps(from: Bitmap, to: Bitmap) { renderer.setBitmaps(from, to) }
}

class PageCurlRenderer(private val ctx: Context) : GLSurfaceView.Renderer {
    // program/handles
    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var uFrom = 0
    private var uTo = 0
    private var uProgress = 0
    private var uRatio = 0
    private var uAngle = 0
    private var uRadius = 0
    private var uRoll = 0
    private var uUncurl = 0
    private var uGreyback = 0
    private var uOpacity = 0
    private var uShadow = 0

    // geometry: full-screen quad
    private val vb = ByteBuffer.allocateDirect(4 * (4 + 2) * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            // x,y,z,w (clipspace), u,v
            put(floatArrayOf(
                -1f, -1f, 0f, 1f,   0f, 0f,
                1f, -1f, 0f, 1f,   1f, 0f,
                -1f,  1f, 0f, 1f,   0f, 1f,
                1f,  1f, 0f, 1f,   1f, 1f
            ))
            position(0)
        }

    private val ib = ByteBuffer.allocateDirect(6 * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(shortArrayOf(0,1,2, 2,1,3))
            position(0)
        }

    private var texFrom = 0
    private var texTo = 0
    private var viewW = 1
    private var viewH = 1

    // uniforms (对外可改)
    var progress = 0f
    var angleDeg = 80
    var radius = 0.15f
    var roll = false
    var uncurl = false
    var greyback = false
    var opacity = 0.8f
    var shadow = 0.2f

    fun setBitmaps(from: Bitmap, to: Bitmap) {
        if (texFrom == 0) texFrom = createTexture()
        if (texTo == 0) texTo = createTexture()
        bindBitmapToTexture(texFrom, from)
        bindBitmapToTexture(texTo, to)
    }

    private fun loadShaderFromAssets(context: Context, filename: String): String {
        context.assets.open(filename).use { input ->
            return input.bufferedReader().use { it.readText() }
        }
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = buildProgram(
            loadShaderFromAssets(ctx, "vertex.glsl"),   // 放到 res/raw/vertex.glsl
            loadShaderFromAssets(ctx, "SimplePageCurl.glsl")  // 放到 res/raw/fragment.glsl
        )
        GLES20.glUseProgram(program)

        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aUV  = GLES20.glGetAttribLocation(program, "aTexCoord")
        uFrom = GLES20.glGetUniformLocation(program, "uFromTex")
        uTo   = GLES20.glGetUniformLocation(program, "uToTex")
        uProgress = GLES20.glGetUniformLocation(program, "uProgress")
        uRatio    = GLES20.glGetUniformLocation(program, "uRatio")
        uAngle    = GLES20.glGetUniformLocation(program, "uAngleDeg")
        uRadius   = GLES20.glGetUniformLocation(program, "uRadius")
        uRoll     = GLES20.glGetUniformLocation(program, "uRoll")
        uUncurl   = GLES20.glGetUniformLocation(program, "uUncurl")
        uGreyback = GLES20.glGetUniformLocation(program, "uGreyback")
        uOpacity  = GLES20.glGetUniformLocation(program, "uOpacity")
        uShadow   = GLES20.glGetUniformLocation(program, "uShadow")

        texFrom = createTexture()
        texTo = createTexture()
    }

    override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) {
        viewW = w; viewH = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        // 顶点属性
        vb.position(0)
        GLES20.glVertexAttribPointer(aPos, 4, GLES20.GL_FLOAT, false, 6*4, vb)
        GLES20.glEnableVertexAttribArray(aPos)
        vb.position(4)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 6*4, vb)
        GLES20.glEnableVertexAttribArray(aUV)

        // 纹理单元 0,1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texFrom)
        GLES20.glUniform1i(uFrom, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texTo)
        GLES20.glUniform1i(uTo, 1)

        // uniforms
        GLES20.glUniform1f(uProgress, progress)
        val ratio = viewW.toFloat() / viewH.toFloat()
        GLES20.glUniform1f(uRatio, ratio)
        GLES20.glUniform1i(uAngle, angleDeg)
        GLES20.glUniform1f(uRadius, radius)
        GLES20.glUniform1i(uRoll, if (roll) 1 else 0)
        GLES20.glUniform1i(uUncurl, if (uncurl) 1 else 0)
        GLES20.glUniform1i(uGreyback, if (greyback) 1 else 0)
        GLES20.glUniform1f(uOpacity, opacity)
        GLES20.glUniform1f(uShadow, shadow)

        // 绘制
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, 6,
            GLES20.GL_UNSIGNED_SHORT, ib
        )
    }

    // --- 工具函数 ---
    private fun createTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private fun bindBitmapToTexture(texId: Int, bmp: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun buildProgram(vSrc: String, fSrc: String): Int {
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compile error: $log")
            }
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("Program link error: $log")
        }
        return prog
    }
}
