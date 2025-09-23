package org.peyilo.readview.demo

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import org.peyilo.readview.App
import org.peyilo.readview.R

/**
 * 定义了几种阅读主题：背景色、文字色等
 */
class ReadViewTheme(
    val titleColor: Int = Color.BLACK,
    val contentColor: Int = "#1E1A1A".toColorInt(),
    val background: Drawable = "#F6F6F6".toColorInt().toDrawable(),
    val headerAndFooterTextColor: Int = Color.BLACK,
) {
    companion object {
        val whiteTheme by lazy { ReadViewTheme() }

        val paperTheme by lazy {
            ReadViewTheme(
                background = AppCompatResources.getDrawable(
                    App.Companion.applicationContext,
                    R.drawable.read_page_bg_1)!!,
            )
        }

        val nightTheme by lazy {
            ReadViewTheme(
                titleColor = "#F2F2F0".toColorInt(),
                contentColor = "#F2F2F0".toColorInt(),
                background = AppCompatResources.getDrawable(
                    App.Companion.applicationContext,
                    R.drawable.read_page_bg_2)!!,
                headerAndFooterTextColor = "#808085".toColorInt(),
            )
        }

        val eyeCareTheme by lazy {
            ReadViewTheme(
                background = "#D2E4D2".toColorInt().toDrawable(),
            )
        }

        val blackTheme by lazy {
            ReadViewTheme(
                titleColor = "#F2F2F0".toColorInt(),
                contentColor = "#F2F2F0".toColorInt(),
                background = "#1C1C1E".toColorInt().toDrawable(),
                headerAndFooterTextColor = "#808085".toColorInt(),
            )
        }


        fun getTheme(idx: Int): ReadViewTheme = when (idx) {
            0 -> whiteTheme
            1 -> nightTheme
            2 -> paperTheme
            3 -> eyeCareTheme
            4 -> blackTheme
            else -> throw IllegalArgumentException("Invalid theme index: $idx")
        }
    }

}
