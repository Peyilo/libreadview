package org.peyilo.libreadview.manager.util

import android.graphics.Bitmap
import org.peyilo.libreadview.util.LogHelper

class PageBitmapCache {

    companion object {
        private const val TAG = "PageBitmapCache"
    }

    var topBitmap: Bitmap? = null
        set(value) {
            field?.let {
                try {           // 如果不为null，尝试释放bitmap
                    field!!.recycle()
                } catch (e: Exception) {
                    LogHelper.e(TAG, "topBitmap: ${e.stackTrace}")
                }
            }
            field = value
        }

    var bottomBitmap: Bitmap? = null
        set(value) {
            field?.let {
                try {           // 如果不为null，尝试释放bitmap
                    field!!.recycle()
                } catch (e: Exception) {
                    LogHelper.e(TAG, "topBitmap: ${e.stackTrace}")
                }
            }
            field = value
        }

    fun clearBitmap() {
        topBitmap?.recycle()
        bottomBitmap?.recycle()
        topBitmap = null
        bottomBitmap = null
        LogHelper.d(TAG, "clearBitmap")
    }
}