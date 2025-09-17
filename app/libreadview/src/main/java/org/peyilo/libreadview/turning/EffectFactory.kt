package org.peyilo.libreadview.turning

import org.peyilo.libreadview.AbstractPageContainer

/**
 * PageEffect工厂类
 */
object EffectFactory {

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
     * 根据指定的PageEffect类型创建对应的PageEffect
     */
    fun create(effectType: Int): AbstractPageContainer.PageEffect = when (effectType) {
        NO_ANIMATION -> NoAnimEffects.Horizontal()
        NO_ANIMATION_VERTICAL -> NoAnimEffects.Vertical()
        CURL -> SimpleCurlEffect()
        COVER -> CoverEffect()
        SLIDE -> SlideEffect()
        SCROLL -> ScrollEffect()
        IBOOK_CURL -> IBookCurlEffect()
        IBOOK_SLIDE -> IBookSlideEffect()
        else -> throw IllegalStateException("Not support PageEffectType: $effectType")
    }

    fun getType(pageEffect: AbstractPageContainer.PageEffect): Int =  when (pageEffect) {
        is NoAnimEffects.Horizontal -> NO_ANIMATION
        is NoAnimEffects.Vertical -> NO_ANIMATION_VERTICAL
        is SimpleCurlEffect -> CURL
        is CoverEffect -> COVER
        is SlideEffect -> SLIDE
        is ScrollEffect -> SCROLL
        is IBookCurlEffect -> IBOOK_CURL
        is IBookSlideEffect -> IBOOK_SLIDE
        else -> throw IllegalStateException("Not support pageEffect: $pageEffect")
    }

}