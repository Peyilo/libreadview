package org.peyilo.readview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

private const val TAG = "MyView"
class MyView(context: Context, attrs: AttributeSet?): ViewGroup(context, attrs) {

    init {
        isChildrenDrawingOrderEnabled = true
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        // 倒序绘制：最后添加的 View 先画，最早添加的最后画（被覆盖）
        return childCount - 1 - i
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.d(TAG, "onMeasure, current thread: ${Thread.currentThread().name}")
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, heightMeasureSpec)
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        Log.d(TAG, "onLayout, current thread: ${Thread.currentThread().name}")
        val child1 = getChildAt(0)
        child1.layout(0, 0, child1.measuredWidth, child1.measuredHeight)
        child1.translationX = -500F
        val child2 = getChildAt(1)
        child2.layout(0, 0, child2.measuredWidth, child2.measuredHeight)
        child2.translationX = 500F
    }

    override fun onDraw(canvas: Canvas) {
        Log.d(TAG, "onDraw, current thread: ${Thread.currentThread().name}")
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG, "onTouchEvent, current thread: ${Thread.currentThread().name}")
        return super.onTouchEvent(event)
    }

}