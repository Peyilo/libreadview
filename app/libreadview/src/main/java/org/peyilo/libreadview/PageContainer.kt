package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntRange

open class PageContainer(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): AbstractPageContainer(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    /**
     * PageContainer对外提供了adapter的getter和setter
     */
    var adapter: Adapter<out ViewHolder>
        get() = getInnerAdapter() ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) = setInnerAdapter(value)

    /**
     * 初始化页码：设置一个标记位curPageIndex，在adapter设置之后决定显示页码
     * 注意，这个函数仅提供初始化页码功能，不能用于任意页码跳转。
     * 该函数建议在设置Adapter之前调用，这样消耗少一点。
     */
    fun initPageIndex(@IntRange(from = 1) pageIndex: Int) {
        initContainerPageIndex(pageIndex)
        getInnerAdapter()?.notifyDataSetChanged()
    }

}