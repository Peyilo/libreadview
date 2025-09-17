package org.peyilo.libreadview.simple.page

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes

/**
 * 一个用展示小说内容的页面，并且定义该页面需要包含三个部分：页眉、正文、页脚
 */
open class BaseReadPage(
    context: Context, attrs: AttributeSet? = null
): ViewGroup(context, attrs) {

    lateinit var root: View               // 填充的布局
        private set
    lateinit var body: ReadBody          // 正文显示视图
        private set
    lateinit var header: View                // 页眉视图
        private set
    lateinit var footer: View               // 页脚视图
        private set

    var headerPaddingTop get() = header.paddingTop
        set(value) {
            header.setPadding(headerPaddingLeft, value, headerPaddingRight, headerPaddingBottom)
        }
    var headerPaddingBottom get() = header.paddingBottom
        set(value) {
            header.setPadding(headerPaddingLeft, headerPaddingTop, headerPaddingRight, value)
        }
    var headerPaddingLeft get() = header.paddingLeft
        set(value) {
            header.setPadding(value, headerPaddingTop, headerPaddingRight, headerPaddingBottom)
        }
    var headerPaddingRight get() = header.paddingRight
        set(value) {
            header.setPadding(headerPaddingLeft, headerPaddingTop, value, headerPaddingBottom)
        }

    var footerPaddingTop get() = footer.paddingTop
        set(value) {
            footer.setPadding(footerPaddingLeft, value, footerPaddingRight, footerPaddingBottom)
        }
    var footerPaddingBottom get() = footer.paddingBottom
        set(value) {
            footer.setPadding(footerPaddingLeft, footerPaddingTop, footerPaddingRight, value)
        }
    var footerPaddingLeft get() = footer.paddingLeft
        set(value) {
            footer.setPadding(value, footerPaddingTop, footerPaddingRight, footerPaddingBottom)
        }
    var footerPaddingRight get() = footer.paddingRight
        set(value) {
            footer.setPadding(footerPaddingLeft, footerPaddingTop, value, footerPaddingBottom)
        }


    /**
     * 绑定布局
     * @param layoutId 布局资源ID
     * @param contentId 正文显示视图ID，必须是ReadContent的实现类
     * @param headerId 页眉视图ID
     * @param footerId 页脚视图ID
     */
    open fun bindLayout(
        @LayoutRes layoutId: Int, @IdRes contentId: Int,
        @IdRes headerId: Int, @IdRes footerId: Int
    ) {
        LayoutInflater.from(context).inflate(layoutId, this)
        root = getChildAt(0)
        body = root.findViewById(contentId)
        header = root.findViewById(headerId)
        footer = root.findViewById(footerId)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount != 1) {
            throw IllegalStateException("only support childCount == 1")
        }

        // 父容器的最终宽高
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)

        // 可用空间 = 父容器大小 - padding
        val childWidth = widthSize - paddingLeft - paddingRight
        val childHeight = heightSize - paddingTop - paddingBottom

        val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)

        root.measure(childWidthSpec, childHeightSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = paddingLeft
        val top = paddingTop
        val right = left + root.measuredWidth
        val bottom = top + root.measuredHeight
        root.layout(left, top, right, bottom)
    }

    fun getBodyWidth(): Int = body.measuredWidth

    fun getBodyHeight(): Int = body.measuredHeight

}