package org.peyilo.libreadview.manager.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.createBitmap
import org.peyilo.libreadview.util.LogHelper

private const val TAG = "ViewExtensions"

/**
 * 截取当前页面的绘制结果，并以bitmap方式返回
 * 注意，该方法开销较大，不要频繁调用
 * @return 截取的bitmap
 */
fun View.screenshot(): Bitmap? {
    val start = System.nanoTime()
    return if (width > 0 && height > 0) {
        val screenshot = createBitmap(width, height)
        val c = Canvas(screenshot)
        this.draw(c)
        c.setBitmap(null)
        screenshot.prepareToDraw()
        val end = System.nanoTime()
        LogHelper.d(TAG, "View.screenshot ${(end - start) / 1000}us")
        screenshot
    } else {
        null
    }
}

// 复用版截图：把内容画到传入的 bitmap，不再 new
fun View.screenshotInto(reuse: Bitmap): Bitmap {
    require(reuse.width == width && reuse.height == height) {
        "reuse bitmap size must match view size"
    }
    computeTime(TAG, "View.screenshotInto") {
        val c = Canvas(reuse)
        this.draw(c)
    }
    return reuse
}
