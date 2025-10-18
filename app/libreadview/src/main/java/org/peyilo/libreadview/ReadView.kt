package org.peyilo.libreadview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import org.peyilo.libreadview.util.LogHelper

/**
 * ReadView和PageContainer的唯一的一个区别是：一个对内的adapter，一个对外的adapter
 */
abstract class ReadView(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): AbstractPageContainer(context, attrs, defStyleAttr, defStyleRes), BookNavigator {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    companion object {
        private const val TAG = "ReadView"
    }

    /**
     * ReadView对内提供了adapter的getter和setter
     */
    protected var adapter: Adapter<out ViewHolder>
        get() = getInnerAdapter() ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) = setInnerAdapter(value)

    /**
     * 设置页面背景颜色
     */
    fun setPageBackgroundColor(@ColorInt color: Int) {
        setPageBackground(color.toDrawable())
    }

    /**
     * 设置页面背景为资源图片
     */
    fun setPageBackgroundResource(@DrawableRes resId: Int) {
        val drawable = AppCompatResources.getDrawable(context, resId)
            ?: throw IllegalArgumentException("Resource ID $resId is not valid.")
        setPageBackground(drawable)
    }

    /**
     * 设置页面背景为Drawable
     */
    abstract fun setPageBackground(drawable: Drawable)

    /**
     * 设置页面背景为Bitmap
     */
    fun setPageBackground(bitmap: Bitmap) {
        val drawable = bitmap.toDrawable(resources)
        setPageBackground(drawable)
    }

    private var isSelectedMode = false // 是否处于文字选择模式
    private var longPressTriggered = false
    private val downPos = PointF()

    private var handler = Handler(Looper.getMainLooper())

    // 系统推荐的长按时长（一般为500ms）
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    // 长按检测任务
    private val longPressRunnable = Runnable {
        if (!longPressTriggered) {
            longPressTriggered = true
            isSelectedMode = true
            onLongPress() // 回调逻辑
        }
    }

    private fun onLongPress() {
        Toast.makeText(context, "长按触发，进入选择模式", Toast.LENGTH_SHORT).show()
        // 可触发 UI 更新
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPos.x = event.x
                downPos.y = event.y
                longPressTriggered = false
                // 启动延迟检测任务
                handler.postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressDetection()
            }
        }

        // 一旦进入选择模式，接下来的事件不再交给AbstractPageContainer处理
        if (isSelectedMode) return true

        LogHelper.d(TAG, "selectText: super.onTouchEvent(event)")
        return super.onTouchEvent(event)
    }

    private fun cancelLongPressDetection() {
        handler.removeCallbacks(longPressRunnable)
    }

}