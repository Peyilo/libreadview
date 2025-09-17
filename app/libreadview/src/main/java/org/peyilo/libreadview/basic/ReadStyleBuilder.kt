package org.peyilo.libreadview.basic

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import org.peyilo.libreadview.basic.page.ReadPage
import org.peyilo.libreadview.util.DisplayUtil

/**
 * Builder for defining style options of a [BasicReadView].
 *
 * This builder provides a fluent API for setting style-related properties
 * such as paddings, margins, text sizes, text colors, and page background.
 * Once all style options are configured, call [build] to apply them
 * to a target [BasicReadView].
 *
 * Key points:
 * - Centralizes all style setters into a single builder.
 * - Enables chain calls for better readability and convenience.
 * - Batches multiple style changes to apply them at once, avoiding unnecessary redraws/re-layouts.
 */
class ReadStyleBuilder(private val readView: BasicReadView) {

    // Text layout parameters
    private var firstParaIndent: Float? = null
    private var titleTextMargin: Float? = null
    private var contentTextMargin: Float? = null
    private var lineMargin: Float? = null
    private var paraMargin: Float? = null

    // Text style properties
    private var titleTextSize: Float? = null
    private var contentTextSize: Float? = null
    private var titleTextColor: Int? = null
    private var contentTextColor: Int? = null
    private var headerAndFooterTextColor: Int? = null

    // Page background
    private var mPageBackground: Drawable? = null

    // Custom typefaces
    private var titleTypeface: Typeface? = null
    private var contentTypeface: Typeface? = null

    /** Sets the first paragraph indent in content. */
    fun setFirstParaIndent(indent: Float) = apply { firstParaIndent = indent }

    /** Sets margin around title text blocks. */
    fun setTitleTextMargin(margin: Float) = apply { titleTextMargin = margin }

    /** Sets margin around content text blocks. */
    fun setContentTextMargin(margin: Float) = apply { contentTextMargin = margin }

    /** Sets line spacing inside paragraphs. */
    fun setLineMargin(margin: Float) = apply { lineMargin = margin }

    /** Sets spacing between paragraphs. */
    fun setParaMargin(margin: Float) = apply { paraMargin = margin }

    /** Sets title text size. */
    fun setTitleTextSize(size: Float) = apply { titleTextSize = size }

    /** Sets content text size. */
    fun setContentTextSize(size: Float) = apply { contentTextSize = size }

    /** Sets title text color. */
    fun setTitleTextColor(color: Int) = apply { titleTextColor = color }

    /** Sets content text color. */
    fun setContentTextColor(color: Int) = apply { contentTextColor = color }

    /** Sets text color for header and footer (progress, clock, etc.). */
    fun setHeaderAndFooterTextColor(color: Int) = apply { headerAndFooterTextColor = color }

    /** Sets a drawable as the page background. */
    fun setPageBackground(background: Drawable) = apply { mPageBackground = background }

    /** Sets a solid color as the page background. */
    fun setPageBackgroundColor(color: Int) = apply { setPageBackground(color.toDrawable()) }

    fun setTitleTypeface(typeface: Typeface) = apply { titleTypeface = typeface }

    fun setContentTypeface(typeface: Typeface) = apply { contentTypeface = typeface }

    /**
     * Applies all configured style properties to the given [BasicReadView].
     *
     * - Updates paint properties (colors, text sizes, background).
     * - Invalidates page content if necessary to trigger redraw.
     * - Triggers re-layout if paddings, margins, or text sizes were changed.
     */
    fun build() {
        // Apply paint colors and background
        titleTextColor?.let { readView.mReadStyle.titlePaint.color = it }
        contentTextColor?.let { readView.mReadStyle.contentPaint.color = it }
        headerAndFooterTextColor?.let { readView.mReadStyle.mHeaderAndFooterTextColor = it }
        mPageBackground?.let { readView.mReadStyle.mPageBackground = it }

        // Refresh pages if style-related attributes changed
        if (titleTextColor != null || contentTextColor != null
            || headerAndFooterTextColor != null || mPageBackground != null) {
            readView.traverseAllCreatedPages { page ->
                mPageBackground?.let { bg -> page.background = bg }
                if (page is ReadPage) {
                    if (titleTextColor != null || contentTextColor != null) {
                        page.body.invalidate()
                    }
                    headerAndFooterTextColor?.let { color ->
                        page.chapTitle.setTextColor(color)
                        page.progress.setTextColor(color)
                        page.clock.setTextColor(color)
                    }
                }
            }
        }

        // Apply layout-related parameters and trigger re-layout if needed
        if (firstParaIndent != null || contentTextMargin != null
            || lineMargin != null || paraMargin != null
            || titleTextSize != null || contentTextSize != null) {

            firstParaIndent?.let { readView.mReadStyle.firstParaIndent = DisplayUtil.dpToPx(readView.context, it) }
            titleTextMargin?.let { readView.mReadStyle.titleTextMargin = DisplayUtil.dpToPx(readView.context, it) }
            contentTextMargin?.let { readView.mReadStyle.contentTextMargin = DisplayUtil.dpToPx(readView.context, it) }
            lineMargin?.let { readView.mReadStyle.contentLineMargin = DisplayUtil.dpToPx(readView.context, it) }
            paraMargin?.let { readView.mReadStyle.contentParaMargin = DisplayUtil.dpToPx(readView.context, it) }

            titleTextSize?.let { readView.mReadStyle.titlePaint.textSize = DisplayUtil.spToPx(readView.context, it) }
            contentTextSize?.let { readView.mReadStyle.contentPaint.textSize = DisplayUtil.spToPx(readView.context, it) }

            titleTypeface?.let { readView.mReadStyle.titlePaint.typeface = it }
            contentTypeface?.let { readView.mReadStyle.contentPaint.typeface = it}
            readView.invalidateReadLayout()
        }
    }
}
