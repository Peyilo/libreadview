package org.peyilo.readview.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.graphics.get
import org.peyilo.readview.R
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * // shader：尝试使用mesh实现以下着色器效果https://www.shadertoy.com/view/ls3cDB
 * void mainImage( out vec4 fragColor, in vec2 fragCoord )
 * {
 *     float aspect = iResolution.x / iResolution.y;
 *
 *     vec2 uv = fragCoord * vec2(aspect, 1.) / iResolution.xy;
 *     vec2 mouse = iMouse.xy  * vec2(aspect, 1.) / iResolution.xy;
 *     vec2 mouseDir = normalize(abs(iMouse.zw) - iMouse.xy);
 *     vec2 origin = clamp(mouse - mouseDir * mouse.x / mouseDir.x, 0., 1.);
 *
 *     float mouseDist = clamp(length(mouse - origin)
 *         + (aspect - (abs(iMouse.z) / iResolution.x) * aspect) / mouseDir.x, 0., aspect / mouseDir.x);
 *
 *     if (mouseDir.x < 0.)
 *     {
 *         mouseDist = distance(mouse, origin);
 *     }
 *
 *     float proj = dot(uv - origin, mouseDir);
 *     float dist = proj - mouseDist;
 *
 *     vec2 linePoint = uv - dist * mouseDir;
 *
 *     if (dist > radius)
 *     {
 *         fragColor = texture(iChannel1, uv * vec2(1. / aspect, 1.));
 *         fragColor.rgb *= pow(clamp(dist - radius, 0., 1.) * 1.5, .2);
 *     }
 *     else if (dist >= 0.)
 *     {
 *         // map to cylinder point
 *         float theta = asin(dist / radius);
 *         vec2 p2 = linePoint + mouseDir * (pi - theta) * radius;
 *         vec2 p1 = linePoint + mouseDir * theta * radius;
 *         uv = (p2.x <= aspect && p2.y <= 1. && p2.x > 0. && p2.y > 0.) ? p2 : p1;
 *         fragColor = texture(iChannel0, uv * vec2(1. / aspect, 1.));
 *         fragColor.rgb *= pow(clamp((radius - dist) / radius, 0., 1.), .2);
 *     }
 *     else
 *     {
 *         vec2 p = linePoint + mouseDir * (abs(dist) + pi * radius);
 *         uv = (p.x <= aspect && p.y <= 1. && p.x > 0. && p.y > 0.) ? p : uv;
 *         fragColor = texture(iChannel0, uv * vec2(1. / aspect, 1.));
 *     }
 * }
 */
class MeshCurlView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                frontBmp = BitmapFactory.decodeResource(resources, R.drawable.curpage)
                backBmp = BitmapFactory.decodeResource(resources, R.drawable.nextpage)
                viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        frontBmp?.recycle()
        backBmp?.recycle()
        frontBmp = null
        backBmp = null
    }

    private var frontBmp: Bitmap? = null  // iChannel0
    private var backBmp: Bitmap? = null   // iChannel1

    // 网格密度（越大越细腻，但计算与绘制越重）
    private var meshWidth = 10
    private var meshHeight = 20

    private var verts = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)

    /**
     * iMouse.zw
     */
    private var down = PointF(-1f, -1f)

    /**
     * iMouse.xy
     */
    private var cur = PointF(-1f, -1f)


    // 半径等
    private val pi = Math.PI.toFloat()
    var radius = 0.12f // 以归一化坐标计（相对短边），可按需调

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                down.set(event.x, event.y)
                cur.set(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                cur.set(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

            }
        }
        return true
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val front = frontBmp ?: return
        val back = backBmp ?: return
        if (width == 0 || height == 0) return

        var index = 0
        for (y in 0..meshHeight) {
            val fy = height.toFloat() * y / meshHeight
            for (x in 0..meshWidth) {
                val fx = width * x.toFloat() / meshWidth
                verts[index * 2] = fx
                verts[index * 2 + 1] = fy
                index++
            }
        }

        // 背景先画（相当于 shader 中 dist > radius 的分支）
        canvas.drawBitmapMesh(back, meshWidth, meshHeight, verts, 0, null, 0, null)

        // 将触摸点转换为 shader 的归一化坐标系
        // 归一化：x:[0,aspect], y:[0,1]
        val aspect = width.toFloat() / height.toFloat()

        // 计算mouse
        val mouse = PointF(
            cur.x / width * aspect,
            cur.y / height
        )

        // 计算mouseDir
        val dirX = abs(down.x) - cur.x
        val dirY = abs(down.y) - cur.y
        val len = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(1e-6f)          // 防止除0
        val mouseDir = PointF(dirX / len, dirY / len)

        // 计算origin
        val t = mouse.x / mouseDir.x
        val originX = (mouse.x - mouseDir.x * t).coerceIn(0f, 1f)
        val originY = (mouse.y - mouseDir.y * t).coerceIn(0f, 1f)
        val origin = PointF(originX, originY)

        // 计算mouseDist
        val baseDist = hypot(mouse.x - origin.x, mouse.y - origin.y)
        val pressNormX = abs(down.x) / width
        val compensation = (aspect - pressNormX * aspect) / mouseDir.x
        var mouseDist = (baseDist + compensation).coerceIn(0f, aspect / mouseDir.x)

        if (mouseDir.x < 0f) {
            mouseDist = hypot(mouse.x - origin.x, mouse.y - origin.y)
        }

        for (i in verts.indices step 2) {
            // vec2 uv = fragCoord * vec2(aspect, 1.) / iResolution.xy
            val fragCoordX = verts[i]
            val fragCoordY = verts[i + 1]
            val uvX = fragCoordX * aspect / width
            val uvY = fragCoordY / height
            val uv = PointF(uvX, uvY)

            val proj = (uv.x - origin.x) * mouseDir.x + (uv.y - origin.y) * mouseDir.y
            val dist = proj - mouseDist
            val linePoint = PointF(
                uv.x - dist * mouseDir.x,
                uv.y - dist * mouseDir.y
            )

            if (dist > radius) {
                continue
            } else if (dist >= 0f) {
                // 圆柱面
                val theta = asin((dist / radius).coerceIn(-1f, 1f))
                val p2 = PointF(
                    linePoint.x + mouseDir.x * (pi - theta) * radius,
                    linePoint.y + mouseDir.y * (pi - theta) * radius
                )
                val p1 = PointF(
                    linePoint.x + mouseDir.x * theta * radius,
                    linePoint.y + mouseDir.y * theta * radius
                )

                val uv = if (p2.x <= aspect && p2.y <= 1f && p2.x > 0f && p2.y > 0f) p2 else p1

                val texUvX = uv.x * (1f / aspect)
                val texUvY = uv.y
                verts[i] = texUvX * width
                verts[i + 1] = texUvY * height
            } else {
                // 背面
                val p = PointF(
                    linePoint.x + mouseDir.x * (abs(dist) + pi * radius),
                    linePoint.y + mouseDir.y * (abs(dist) + pi * radius)
                )

                val uv = if (p.x <= aspect && p.y <= 1f && p.x > 0f && p.y > 0f) p else uv

                val texUvX = uv.x * (1f / aspect)
                val texUvY = uv.y
                verts[i] = texUvX * width
                verts[i + 1] = texUvY * height
            }
        }
        canvas.drawBitmapMesh(front, meshWidth, meshHeight, verts, 0, null, 0, null)
    }

    private fun hypot(x: Float, y: Float) = kotlin.math.sqrt(x * x + y * y)

}