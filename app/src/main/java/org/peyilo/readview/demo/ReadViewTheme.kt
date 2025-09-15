package org.peyilo.readview.demo

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import org.peyilo.libreadview.simple.ReadStyleBuilder
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.App
import org.peyilo.readview.R

/**
 * 定义了几种阅读主题：背景色、文字色等
 */
class ReadViewTheme(
    val titleColor: Int = Color.BLACK,
    val contentColor: Int = "#1E1A1A".toColorInt(),
    val background: Drawable = "#EEEDED".toColorInt().toDrawable(),
    val headerAndFooterTextColor: Int = Color.BLACK,
) {
    companion object {
        val whiteTheme = ReadViewTheme()

        val paperTheme = ReadViewTheme(
            background = AppCompatResources.getDrawable(
                App.Companion.applicationContext,
                R.drawable.read_page_bg_1)!!,
        )

        val nightTheme = ReadViewTheme(
            titleColor = "#F2F2F0".toColorInt(),
            contentColor = "#F2F2F0".toColorInt(),
            background = AppCompatResources.getDrawable(
                App.Companion.applicationContext,
                R.drawable.read_page_bg_2)!!,
            headerAndFooterTextColor = "#808085".toColorInt(),
        )

        val eyeCareTheme = ReadViewTheme(
            background = "#D2E4D2".toColorInt().toDrawable(),
        )

        val blackTheme = ReadViewTheme(
            titleColor = "#F2F2F0".toColorInt(),
            contentColor = "#F2F2F0".toColorInt(),
            background = "#1C1C1E".toColorInt().toDrawable(),
            headerAndFooterTextColor = "#808085".toColorInt(),
        )

        val allThemes = listOf(whiteTheme, nightTheme, paperTheme, eyeCareTheme, blackTheme)
    }

}

fun SimpleReadView.setReadViewTheme(idx: Int = 0) {
    val curTheme = ReadViewTheme.allThemes[idx]
    ReadStyleBuilder(this).apply {
        setContentTextColor(curTheme.contentColor)
        setTitleTextColor(curTheme.titleColor)
        setPageBackground(curTheme.background)
        setHeaderAndFooterTextColor(curTheme.headerAndFooterTextColor)
    }.build()
}