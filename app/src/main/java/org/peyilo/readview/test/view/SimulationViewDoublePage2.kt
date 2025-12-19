package org.peyilo.readview.test.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import org.peyilo.readview.R

class SimulationViewDoublePage2(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): View(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        setBackgroundColor(Color.GRAY)
    }

    // 确定两个page位置的关键点
    private val topLeftPoint = PointF()
    private val topMiddlePoint = PointF()
    private val topRightPoint = PointF()
    private val bottomLeftPoint = PointF()
    private val bottomMiddlePoint = PointF()
    private val bottomRightPoint = PointF()

    private val rightPageRegion = Path()
    private val leftPageRigion = Path()
    private val allPageRegion = Path()

    private val pageWidth get() =  topRightPoint.x - topMiddlePoint.x
    private val pageHeight get() = bottomRightPoint.y - topRightPoint.y

    private val viewVerticalPadding = 40F
    private val viewHorizontalPadding = 0F

    // 前后两个bitmap
    private val leftBitmap = BitmapFactory.decodeResource(resources, R.drawable.curpage)
    private val rightBitmap = BitmapFactory.decodeResource(resources, R.drawable.nextpage)


    private var isFlipping = false

    // 触控相关坐标
    private val touchPos = PointF()
    private val downPos = PointF()


    private val greenPaint = Paint().apply {
        color = "#88FFA6".toColorInt()
        style = Paint.Style.FILL
    }
    private val yellowPaint = Paint().apply {
        color = "#FFF988".toColorInt()
        style = Paint.Style.FILL
    }
    private val bluePaint = Paint().apply {
        color = "#7DB5FF".toColorInt()
        style = Paint.Style.FILL
    }

    private val purplePaint = Paint().apply {
        color = "#D284FF".toColorInt()
        style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        initPageSize()
    }

    private fun initPageSize() {
        val remainedWidth = measuredWidth - viewHorizontalPadding * 2
        val remainedHeight = measuredHeight - viewVerticalPadding * 2

        assert(leftBitmap.width == rightBitmap.width)
        assert(leftBitmap.height == rightBitmap.height)
        val bitmapWidth = leftBitmap.width + rightBitmap.width
        val bitmapHeight = leftBitmap.height
        val scale = minOf(
            remainedWidth / bitmapWidth,
            remainedHeight / bitmapHeight
        )
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale

        topMiddlePoint.x = measuredWidth / 2.0F
        topMiddlePoint.y = (measuredHeight - scaledHeight) / 2.0F
        bottomMiddlePoint.x = topMiddlePoint.x
        bottomMiddlePoint.y = (measuredHeight + scaledHeight) / 2.0F
        topLeftPoint.x = (measuredWidth - scaledWidth) / 2.0F
        topLeftPoint.y = topMiddlePoint.y
        bottomLeftPoint.x = topLeftPoint.x
        bottomLeftPoint.y = bottomMiddlePoint.y
        topRightPoint.x = (measuredWidth + scaledWidth) / 2.0F
        topRightPoint.y = topMiddlePoint.y
        bottomRightPoint.x = topRightPoint.x
        bottomRightPoint.y = bottomMiddlePoint.y
        rightPageRegion.apply {
            reset()
            moveTo(topMiddlePoint.x, topMiddlePoint.y)
            lineTo(topRightPoint.x, topRightPoint.y)
            lineTo(bottomRightPoint.x, bottomRightPoint.y)
            lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
            close()
        }
        leftPageRigion.apply {
            reset()
            moveTo(topMiddlePoint.x, topMiddlePoint.y)
            lineTo(topLeftPoint.x, topLeftPoint.y)
            lineTo(bottomLeftPoint.x, bottomLeftPoint.y)
            lineTo(bottomMiddlePoint.x, bottomMiddlePoint.y)
            close()
        }
        allPageRegion.apply {
            addPath(leftPageRigion)
            addPath(rightPageRegion)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(leftPageRigion, greenPaint)
        canvas.drawPath(rightPageRegion, yellowPaint)
        if (!isFlipping) {
            canvas.drawBitmap(
                leftBitmap, null,
                RectF(topLeftPoint.x, topLeftPoint.y, bottomMiddlePoint.x, bottomMiddlePoint.y), null
            )
            canvas.drawBitmap(
                rightBitmap, null,
                RectF(topMiddlePoint.x, topMiddlePoint.y, bottomRightPoint.x, bottomRightPoint.y), null
            )
        } else {
            computePoints()
            computePaths()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                isFlipping = false
                invalidate()
                // 关于为什么要取整，因为scroller只支持int，导致dx、dy必须是整数，从而使得动画结束后，还有微小的误差
                // 所以通过取整，来避免误差积累
                downPos.x = event.x.toInt().toFloat()
                downPos.y = event.y.toInt().toFloat()
                touchPos.x = event.x.toInt().toFloat()
                touchPos.y = event.y.toInt().toFloat()
            }
            MotionEvent.ACTION_MOVE -> {
                touchPos.x = event.x.toInt().toFloat()
                touchPos.y = event.y.toInt().toFloat()
                if (!isFlipping) isFlipping = true
                invalidate()
            }
        }
        return true
    }

    private fun computePoints() {

    }

    private fun computePaths() {

    }
}