package org.peyilo.libreadview.basic

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import org.peyilo.libreadview.R

class HandlePopup(context: Context) {
    private val popup = PopupWindow(context)
    private val handleView = View(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.ic_selection_handle)
    }

    init {
        popup.contentView = handleView
        popup.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    fun showAt(x: Float, y: Float, parent: View) {
        val location = IntArray(2)
        parent.getLocationOnScreen(location)
        popup.showAtLocation(parent, Gravity.NO_GRAVITY, (location[0] + x).toInt(), (location[1] + y).toInt())
    }

    fun updatePosition(x: Float, y: Float, parent: View) {
        val location = IntArray(2)
        parent.getLocationOnScreen(location)
        popup.update((location[0] + x).toInt(), (location[1] + y).toInt(), -1, -1)
    }

    fun dismiss() {
        popup.dismiss()
    }
}