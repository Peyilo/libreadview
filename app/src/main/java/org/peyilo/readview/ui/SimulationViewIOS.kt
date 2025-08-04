package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import org.peyilo.libreadview.utils.LogHelper
import org.peyilo.libreadview.utils.Vec


class SimulationViewIOS(context: Context, attrs: AttributeSet?): View(context, attrs) {

    private var isDragging = false

    private val downPos = PointF()
    private val dragPos = PointF()

    private val dragDir = PointF()
    private val dragOrthogonalDir = PointF()

    private val axisStartPos = PointF()
    private val axisEndPos = PointF()

    private val radius get() = 0.1 * width

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                isDragging = true
                dragPos.x = event.x
                dragPos.y = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                invalidate()
            }
        }
        return true
    }

    val paint1 = Paint().apply {
        color = Color.BLACK
        strokeWidth = 18F
        textSize = 50F
    }
    val paint2 = Paint().apply {
        color = Color.CYAN
        strokeWidth = 18F
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 计算拖动方向
        dragDir.apply {
            x = downPos.x - dragPos.x
            y = downPos.y - dragPos.y
        }
        Vec.normalize(dragDir)

        canvas.drawLine(dragPos.x, dragPos.y, downPos.x, downPos.y, paint2)

        dragOrthogonalDir.apply {       // 与拖动方向正交的方向
            x = -dragDir.y
            y = dragDir.x
        }

//        val start = dragPos - dragOrthogonalDir * (dragPos.y / abs(dragOrthogonalDir.y))
        axisStartPos.apply {
            x = dragPos.x - dragPos.y * dragOrthogonalDir.x / dragOrthogonalDir.y
            y = 0F
            if (x < 0F) {
                x = 0F
            } else if (x > width) {
                x = width.toFloat()
                y = dragPos.y - (width - dragPos.x) * (dragOrthogonalDir.y / dragOrthogonalDir.x)
            }
        }

        when {
            dragOrthogonalDir.x == 0F -> axisEndPos.apply {
                x = dragPos.x
                y = height.toFloat()
            }
            dragOrthogonalDir.x > 0F -> {
                val dy = (width - dragPos.x) * (dragOrthogonalDir.y / dragOrthogonalDir.x)
                axisEndPos.apply {
                    x = width.toFloat()
                    y = dragPos.y + dy
                    if (y > height) {
                        y = height.toFloat()
                        x = dragPos.x + (height - dragPos.y) * (dragOrthogonalDir.x / dragOrthogonalDir.y)
                    }
                }
            }
            dragOrthogonalDir.x < 0F -> {
                axisEndPos.apply {
                    y = height.toFloat()
                    x = dragPos.x + (height - dragPos.y) * (dragOrthogonalDir.x / dragOrthogonalDir.y)
                    if (x < 0F) {
                        x = 0F
                    }
                }
            }
        }

        LogHelper.d(TAG, "onDraw: start: (${axisStartPos.x}, ${axisStartPos.y}), end: (${axisEndPos.x}, ${axisEndPos.y})")
        canvas.drawLine(axisStartPos.x, axisStartPos.y, axisEndPos.x, axisEndPos.y, paint2)
        canvas.drawText("start: (${axisStartPos.x}, ${axisStartPos.y}), end: (${axisEndPos.x}, ${axisEndPos.y})", 50F, 50F, paint1)
    }


    companion object {
        private const val TAG = "SimulationViewIOS"
    }
}