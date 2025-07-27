package org.peyilo.libreadview.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.createBitmap

fun View.gone() {
    if (visibility != View.GONE) {
        visibility = View.GONE
    }
}

fun View.invisible() {
    if (visibility != View.INVISIBLE) {
        visibility = View.INVISIBLE
    }
}

fun View.visible() {
    if (visibility != View.VISIBLE) {
        visibility = View.VISIBLE
    }
}

// 截取当前页面的绘制结果，并以bitmap方式返回
// 注意，该方法开销较大，不要频繁调用
fun View.screenshot(): Bitmap? {
    return if (width > 0 && height > 0) {
        val screenshot = createBitmap(width, height)
        val c = Canvas(screenshot)
        // c.translate(-scrollX.toFloat(), -scrollY.toFloat())
        this.draw(c)            //
        c.setBitmap(null)
        screenshot.prepareToDraw()
        screenshot
    } else {
        null
    }
}