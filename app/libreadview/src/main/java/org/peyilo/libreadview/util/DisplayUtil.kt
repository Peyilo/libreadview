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

    fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /** px -> dp */
    fun pxToDp(context: Context, px: Float): Float {
        val density = context.resources.displayMetrics.density
        return px / density
    }

    fun pxToDp(context: Context, px: Int): Int {
        val density = context.resources.displayMetrics.density
        return (px / density).toInt()
    }

    /** sp -> px */
    fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    fun spToPx(context: Context, sp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /** px -> sp */
    fun pxToSp(context: Context, px: Float): Float {
        val fontScale = context.resources.configuration.fontScale
        val density = context.resources.displayMetrics.density
        return px / (fontScale * density)
    }

    fun pxToSp(context: Context, px: Int): Int {
        val fontScale = context.resources.configuration.fontScale
        val density = context.resources.displayMetrics.density
        return (px / (fontScale * density)).toInt()
    }
}