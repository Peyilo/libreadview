package org.peyilo.libreadview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable

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

}