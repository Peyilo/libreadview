package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntRange

/**
 * ReadView和PageContainer的唯一的一个区别是：一个对内的adapter，一个对外的adapter
 */
abstract class ReadView(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): AbstractPageContainer(context, attrs, defStyleAttr, defStyleRes), BookNavigator {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    /**
     * ReadView对内提供了adapter的getter和setter
     */
    protected var adapter: Adapter<out ViewHolder>
        get() = getInnerAdapter() ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) = setInnerAdapter(value)

}