package org.peyilo.libreadview.turning.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.widget.Scroller
import androidx.core.graphics.withClip
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.util.LogHelper
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "GoogleCurlRenderer"
class GoogleCurlRenderer: CurlRenderer() {

    private var initDir = AbstractPageContainer.PageDirection.NONE

    private val radius: Float = 0.15f

    private val touchPos = PointF()
    private val downPos = PointF()

    private val pathA = Path()
    private val pathB = Path()

    var meshWidth = 30
    var meshHeight = 50

    private val meshVertsCount by lazy { (meshWidth + 1) * (meshHeight + 1) }

    private val regionAMeshVerts by lazy{ FloatArray(meshVertsCount * 2) }

    private var edgePos = pageWidth
    private val shadowWidth get() = min(0.08F * pageWidth, 100F)

    fun setInitDirection(direction: AbstractPageContainer.PageDirection) {
        // 只能是NEXT或PREV
        if (direction == AbstractPageContainer.PageDirection.NONE) {
            throw IllegalArgumentException("Init direction must be NEXT or PREV")
        }
        initDir = direction
    }

    override fun initControllPosition(
        x: Float, y: Float
    ) {
        downPos.x = x
        downPos.y = y
        touchPos.x = x
        touchPos.y = y
    }

    override fun updateTouchPosition(curX: Float, curY: Float) {
        touchPos.x = curX
        touchPos.y = curY
        edgePos = computeRegionAMeshVerts()
        if (edgePos >= 0) {
            pathA.reset()
            pathA.moveTo(topMiddlePoint.x, topMiddlePoint.y)
            pathA.lineTo(edgePos, topMiddlePoint.y)
            pathA.lineTo(edgePos, bottomMiddlePoint.y)
            pathA.lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
            pathA.close()

            pathB.set(rightPageRegion)
            pathB.op(pathA, Path.Op.DIFFERENCE)
        } else {
            pathA.reset()
            pathB.set(rightPageRegion)
        }
    }

    private fun computeRegionAMeshVerts(): Float {
        var index = 0
        val perc = if (initDir == AbstractPageContainer.PageDirection.NEXT) {
            ((downPos.x - touchPos.x) / pageWidth)           // 在x轴方向上的滑动距离占据全部宽度的比例
                .coerceIn(0f, 1f)
        } else {
            (1 + (downPos.x - touchPos.x) / pageWidth).coerceIn(0f, 1f)
        }
        LogHelper.d(TAG, "computeRegionAMeshVerts: perc=$perc")

        val dx = perc * meshWidth           // 在x轴方向上滑动的网格数量

        var calcRadius = radius             // 实际上就是sin函数振幅的大小
        if (perc < 0.2f) {                  // 当滑动的距离小于0.2时，振幅随着滑动距离增大而增大；当滑动距离大于0.2时，振幅保持不变
            calcRadius = radius * perc * 5f
        }

        var movX = 0f
        if (perc > 0.05f) {
            movX = perc - 0.05f
        }

        val hwRadio = pageHeight / pageWidth
        val hwCorrection = (hwRadio - 1F) / 2F
        val whRatio = 1f - calcRadius
        val cameraDistance = -3.0f

        var maxX = Float.MIN_VALUE
        for (row in 0..meshHeight) {
            for (col in 0..meshWidth) {
                val gridZ = (
                    calcRadius * sin(
                        Math.PI / (meshWidth * 0.7f) * (col - dx)
                    ) + calcRadius * 1.1f
                ).toFloat()

                val gridX = col.toFloat() / meshWidth * whRatio - movX
                val gridY = row.toFloat() / meshHeight * hwRadio - hwCorrection

                val k = cameraDistance / (cameraDistance + gridZ)

                regionAMeshVerts[index * 2] = gridX * k * pageWidth
                regionAMeshVerts[index * 2 + 1] = (gridY * k + hwCorrection) * pageWidth
                if (regionAMeshVerts[index * 2] > maxX) {
                    maxX = regionAMeshVerts[index * 2]
                }
                index++
            }
        }
        return maxX
    }

    override fun render(canvas: Canvas) {
        canvas.withClip(pathB) {
            drawBitmap(bottomBitmap, 0F, 0F, null)
            drawShadow(canvas)
        }
        canvas.withClip(pathA) {
            drawBitmapMesh(topBitmap, meshWidth, meshHeight,
                regionAMeshVerts, 0, null, 0, null)
        }
    }

    // 绘制阴影
    private fun drawShadow(canvas: Canvas) {

        val centerX = edgePos
        val halfWidth = shadowWidth * 0.5f

        val left = centerX - halfWidth
        val right = centerX + halfWidth
        val top = 0f
        val bottom = pageHeight

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 中间最深，两侧渐隐
        paint.shader = LinearGradient(
            left,
            0f,
            right,
            0f,
            intArrayOf(
                Color.argb(0, 0, 0, 0),   // 左侧透明
                Color.argb(60, 0, 0, 0),   // 左内
                Color.argb(120, 0, 0, 0),   // 正中最深
                Color.argb(60, 0, 0, 0),   // 右内
                Color.argb(0, 0, 0, 0)    // 右侧透明
            ),
            floatArrayOf(
                0f,
                0.25f,
                0.5f,
                0.75f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun flipToNextPage(scroller: Scroller, animDuration: Int) {
        val dx = (- pageWidth + (downPos.x - touchPos.x)).toInt()
        val dy = 0
        scroller.startScroll(touchPos.x.toInt(), touchPos.y.toInt(),
            dx, dy, animDuration)
    }

    override fun flipToPrevPage(scroller: Scroller, animDuration: Int) {
        val dx = (pageWidth - (downPos.x - touchPos.x)).toInt()
        val dy = 0
        scroller.startScroll(touchPos.x.toInt(), touchPos.y.toInt(),
            dx, dy, animDuration)
    }

}