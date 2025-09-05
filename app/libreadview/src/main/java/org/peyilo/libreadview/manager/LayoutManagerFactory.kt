package org.peyilo.libreadview.manager

import org.peyilo.libreadview.AbstractPageContainer

/**
 * LayoutManager工厂类
 */
object LayoutManagerFactory {

    /**
     * 无动画翻页（水平翻页）
     */
    const val NO_ANIMATION = 0

    /**
     * 无动画翻页（垂直翻页）
     */
    const val NO_ANIMATION_VERTICAL = 1

    /**
     * 仿真翻页：模拟纸张的翻页效果
     */
    const val CURL = 2

    /**
     * 覆盖翻页
     */
    const val COVER = 3

    /**
     * 滑动翻页
     */
    const val SLIDE = 4

    /**
     * 滚动翻页
     */
    const val SCROLL = 5

    /**
     * 仿真翻页：仿iOS风格的curl翻页
     */
    const val IBOOK_CURL = 6

    /**
     * 滑动翻页：仿iBook风格的滑动翻页
     */
    const val IBOOK_SLIDE = 7

    /**
     * 根据指定的LayoutManager类型创建对应的LayoutManager
     */
    fun create(layoutManagerType: Int): AbstractPageContainer.LayoutManager = when (layoutManagerType) {
        NO_ANIMATION -> NoAnimLayoutManagers.Horizontal()
        NO_ANIMATION_VERTICAL -> NoAnimLayoutManagers.Vertical()
        CURL -> SimpleCurlLayoutManager()
        COVER -> CoverLayoutManager()
        SLIDE -> SlideLayoutManager()
        SCROLL -> ScrollLayoutManager()
        IBOOK_CURL -> IBookCurlLayoutManager()
        IBOOK_SLIDE -> IBookSlideLayoutManager()
        else -> throw IllegalStateException("Not support layoutManagerType: $layoutManagerType")
    }

    fun getType(layoutManager: AbstractPageContainer.LayoutManager): Int =  when (layoutManager) {
        is NoAnimLayoutManagers.Horizontal -> NO_ANIMATION
        is NoAnimLayoutManagers.Vertical -> NO_ANIMATION_VERTICAL
        is SimpleCurlLayoutManager -> CURL
        is CoverLayoutManager -> COVER
        is SlideLayoutManager -> SLIDE
        is ScrollLayoutManager -> SCROLL
        is IBookCurlLayoutManager -> IBOOK_CURL
        is IBookSlideLayoutManager -> IBOOK_SLIDE
        else -> throw IllegalStateException("Not support layoutManager: $layoutManager")
    }

}