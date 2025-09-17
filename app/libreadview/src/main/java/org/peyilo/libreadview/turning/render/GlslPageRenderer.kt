package org.peyilo.libreadview.turning.render

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import org.peyilo.libreadview.PageGLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlslPageRenderer(val glView: PageGLSurfaceView): PageGLSurfaceView.PageRenderer() {

    private var _topBitmap: Bitmap? = null
    private var _bottomBitmap: Bitmap? = null

    private val topBitmap: Bitmap get() = _topBitmap!!
    private val bottomBitmap: Bitmap get() = _bottomBitmap!!

    private var program: Int = 0
    private var textureId0: Int = 0
    private var textureId1: Int = 0

    private var iResolutionHandle = 0
    private var iTimeHandle = 0
    private var iMouseHandle = 0
    private var channel0Handle = 0
    private var channel1Handle = 0

    private var startTime = System.currentTimeMillis()

    private var screenWidth = 0
    private var screenHeight = 0

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    private val fragmentShaderCode = """
precision mediump float;

uniform vec3 iResolution;
uniform vec4 iMouse;
uniform float iTime;

uniform sampler2D iChannel0;
uniform sampler2D iChannel1;

varying vec2 vTexCoord; // 顶点着色器传来的 [0,1] 范围坐标

#define pi 3.14159265359
#define radius .05

void main() {
    // vTexCoord 原本就是 [0,1]，我们可以直接用
    vec2 uv = vTexCoord;

    // 修正宽高比
    float aspect = iResolution.x / iResolution.y;
    uv.x *= aspect;

    // 处理鼠标坐标（需要归一化到 [0,1]）
    vec2 mouse = iMouse.xy / iResolution.xy;
    mouse.x *= aspect;

    vec2 click = iMouse.zw / iResolution.xy;
    click.x *= aspect;

    vec2 mouseDir = normalize(click - mouse);
    vec2 origin = clamp(mouse - mouseDir * mouse.x / mouseDir.x, 0., 1.);

    float mouseDist = clamp(length(mouse - origin)
        + (aspect - (abs(iMouse.z) / iResolution.x) * aspect) / mouseDir.x,
        0., aspect / mouseDir.x);

    if (mouseDir.x < 0.) {
        mouseDist = distance(mouse, origin);
    }

    float proj = dot(uv - origin, mouseDir);
    float dist = proj - mouseDist;
    vec2 linePoint = uv - dist * mouseDir;

    vec4 fragColor;
    if (dist > radius) {
        fragColor = texture2D(iChannel1, vTexCoord);
        fragColor.rgb *= pow(clamp(dist - radius, 0., 1.) * 1.5, .2);
    }
    else if (dist >= 0.) {
        float theta = asin(dist / radius);
        vec2 p2 = linePoint + mouseDir * (pi - theta) * radius;
        vec2 p1 = linePoint + mouseDir * theta * radius;
        uv = (p2.x <= aspect && p2.y <= 1. && p2.x > 0. && p2.y > 0.) ? p2 : p1;
        fragColor = texture2D(iChannel0, uv / vec2(aspect,1.));
        fragColor.rgb *= pow(clamp((radius - dist) / radius, 0., 1.), .2);
    }
    else {
        vec2 p = linePoint + mouseDir * (abs(dist) + pi * radius);
        uv = (p.x <= aspect && p.y <= 1. && p.x > 0. && p.y > 0.) ? p : uv;
        fragColor = texture2D(iChannel0, uv / vec2(aspect,1.));
    }

    gl_FragColor = fragColor;
}

    """

    private val squareCoords = floatArrayOf(
        -1f,  1f,
        -1f, -1f,
        1f, -1f,
        1f,  1f
    )

    private val texCoords = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 1f,
        1f, 0f
    )

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    private fun loadShaderFromAssets(context: Context, filename: String): String {
        context.assets.open(filename).use { input ->
            return input.bufferedReader().use { it.readText() }
        }
    }

    fun setBitmaps(topBitmap: Bitmap, bottomBitmap: Bitmap) {
        this._topBitmap = topBitmap
        this._bottomBitmap = bottomBitmap

        // 把更新任务丢到 GLThread
        glView.queueEvent {
            updateTexture(textureId0, _topBitmap)
            updateTexture(textureId1, _bottomBitmap)
        }
    }

    // 封装一个更新函数：仅替换内容
    private fun updateTexture(textureId: Int, bitmap: Bitmap?) {
        if (textureId == 0 || bitmap == null) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glUniform3f(iResolutionHandle, screenWidth.toFloat(), screenHeight.toFloat(), 1f)

        val now = System.currentTimeMillis()
        val time = (now - startTime) / 1000f
        GLES20.glUniform1f(iTimeHandle, time)

        GLES20.glUniform4f(iMouseHandle, touchPos.x, touchPos.y, downPos.x, downPos.y)

        // 绑定纹理通道 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId0)
        GLES20.glUniform1i(channel0Handle, 0)

        // 绑定纹理通道 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId1)
        GLES20.glUniform1i(channel1Handle, 1)

        drawQuad()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        screenWidth = width
        screenHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?
    ) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        textureId0 = loadTexture(topBitmap)
        textureId1 = loadTexture(bottomBitmap)

        iResolutionHandle = GLES20.glGetUniformLocation(program, "iResolution")
        iTimeHandle = GLES20.glGetUniformLocation(program, "iTime")
        iMouseHandle = GLES20.glGetUniformLocation(program, "iMouse")
        channel0Handle = GLES20.glGetUniformLocation(program, "iChannel0")
        channel1Handle = GLES20.glGetUniformLocation(program, "iChannel1")
    }

    private fun drawQuad() {
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        val vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(squareCoords).position(0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuffer.put(texCoords).position(0)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        val drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        drawListBuffer.put(drawOrder).position(0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}