package org.peyilo.libreadview.util

import android.content.Context
import android.util.TypedValue

object DisplayUtil {

    /** dp -> px */
    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    /** px -> dp */
    fun pxToDp(context: Context, px: Float): Float {
        val density = context.resources.displayMetrics.density
        return px / density
    }

    /** sp -> px */
    fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    /** px -> sp */
    fun pxToSp(context: Context, px: Float): Float {
        val fontScale = context.resources.configuration.fontScale
        val density = context.resources.displayMetrics.density
        return px / (fontScale * density)
    }
}
