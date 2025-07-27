package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.scale
import androidx.core.graphics.withTranslation
import org.peyilo.readview.R
import kotlin.math.PI
import kotlin.math.cos

class MeshViewDemo @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.sample_image)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val meshWidth = 10
    private val meshHeight = 10
    private val count = (meshWidth + 1) * (meshHeight + 1)

    private val meshVerts = FloatArray(count * 2)

    private lateinit var scaledBitmap: Bitmap

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dx = (width - scaledBitmap.width) / 2f
        val dy = (height - scaledBitmap.height) / 2f
        canvas.withTranslation(dx, dy) {
            drawBitmapMesh(scaledBitmap, meshWidth, meshHeight, meshVerts, 0, null, 0, paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val viewRatio = w.toFloat() / h
        val bmpRatio = bitmap.width.toFloat() / bitmap.height

        val targetWidth: Int
        val targetHeight: Int

        if (bmpRatio > viewRatio) {
            // 图片比 View 更宽，宽度撑满，按比例缩放高度
            targetWidth = w
            targetHeight = (w / bmpRatio).toInt()
        } else {
            // 图片比 View 更高，高度撑满，按比例缩放宽度
            targetHeight = h
            targetWidth = (h * bmpRatio).toInt()
        }

        // 生成缩放后的 Bitmap
        scaledBitmap = bitmap.scale(targetWidth, targetHeight)

        // 重新生成 mesh 顶点数组
        setupMesh(scaledBitmap.width, scaledBitmap.height)
        invalidate()
    }

    private fun setupMesh(width: Int, height: Int) {
        var index = 0
        var last1 = 0F
        var last2 = 0F
        for (y in 0..(meshHeight * 0.8).toInt()) {
            val fy = height.toFloat() * y / meshHeight
            for (x in 0..meshWidth) {
                val fx = 2 / PI * width * cos(PI / 2 * (1 - x.toFloat() / meshWidth))
                meshVerts[index * 2] = fx.toFloat()
                meshVerts[index * 2 + 1] = fy
                index++
                last1 = fx.toFloat()
                last2 = fy
            }
        }
        for (y in ((meshHeight * 0.8).toInt()+1)..meshHeight) {
            for (x in 0..meshWidth) {
                meshVerts[index * 2] = last1
                meshVerts[index * 2 + 1] = last2
                index++
            }
        }
    }

}