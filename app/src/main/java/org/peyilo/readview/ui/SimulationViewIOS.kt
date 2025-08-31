package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.peyilo.libreadview.manager.SimulationRenderer


class SimulationViewIOS(context: Context, attrs: AttributeSet?): View(context, attrs) {

    private val renderer = SimulationRenderer()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        renderer.setPageSize(width.toFloat(), height.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                renderer.initDownPosition(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                renderer.updateTouchPosition(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.render(canvas)
 }


    companion object {
        private const val TAG = "SimulationViewIOS"
    }
}