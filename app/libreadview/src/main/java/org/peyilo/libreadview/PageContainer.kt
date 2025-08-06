package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet

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

}