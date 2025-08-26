package org.peyilo.readview.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.peyilo.readview.R

class GLRenderView(context: Context, attrs: AttributeSet?): View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private val bmpTop = BitmapFactory.decodeResource(resources, R.drawable.curpage)
    private val bmpBottom = BitmapFactory.decodeResource(resources, R.drawable.nextpage)

    // 在你的自定义 View 里
    private val gl = GLRenderer()
    private var touchX = 0f
    private var touchY = 0f
    private var downX = 0f
    private var downY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        gl.render(
            canvas,
            topBitmap = bmpTop,
            bottomBitmap = bmpBottom,
            mouseX = touchX, mouseY = touchY, mouseZ = downX, mouseW = downY
        )
        // 若需要动画：invalidate() 或 Choreographer.postFrameCallback 触发下一帧
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = x
                touchY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return true
    }

}