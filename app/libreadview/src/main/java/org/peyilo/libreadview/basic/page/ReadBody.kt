package org.peyilo.libreadview.basic.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.basic.page.ReadBody.InnerAdapter.InnerViewHolder
import org.peyilo.libreadview.data.page.PageData
import org.peyilo.libreadview.layout.PageContentProvider
import org.peyilo.libreadview.turning.NoAnimEffects
import org.peyilo.libreadview.turning.util.computeTime

/**
 * 正文显示视图
 */
class ReadBody(
    context: Context, attrs: AttributeSet? = null
): PageContainer(context, attrs) {

    var content: PageData? = null

    var provider: PageContentProvider? = null

    private val items: MutableList<Pair<PageData?, PageContentProvider?>> = mutableListOf()

    init {
        pageEffect = NoAnimEffects.Horizontal()
        adapter = InnerAdapter(items)
    }

    companion object {
        private const val TAG = "ReadBody"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeTime(TAG, "onDraw") {
            provider?.apply {
                content?.let {
                    drawPage(content!!, canvas)
                }
            }
        }
    }

    private class InnerBody(
        context: Context, attrs: AttributeSet? = null
    ): View(context, attrs) {
        var content: PageData? = null

        var provider: PageContentProvider? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            provider?.apply {
                content?.let {
                    drawPage(content!!, canvas)
                }
            }
        }
    }

    private class InnerAdapter(
        private val items: List<Pair<PageData?, PageContentProvider?>>
    ): Adapter<InnerViewHolder>() {
        class InnerViewHolder(itemView: View): ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerViewHolder {
            val view = InnerBody(parent.context)
            return InnerViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: InnerViewHolder, position: Int) {
            val itemView = holder.itemView as InnerBody
            itemView.content = items[position].first
            itemView.provider = items[position].second
            itemView.invalidate()
        }

        override fun getItemCount(): Int = items.size
    }

    fun updateContent(
        content: PageData?,
        provider: PageContentProvider?
    ) {
        this.content = content
        this.provider = provider
        if (items.isEmpty()) {
            items.add(Pair(content, provider))
        } else {
            items[0] = Pair(content, provider)
        }
        adapter.notifyDataSetChanged()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}