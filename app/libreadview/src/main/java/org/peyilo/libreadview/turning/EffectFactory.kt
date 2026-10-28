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
     * iOS 原版算法的 Kotlin 仿真翻页。沿用旧仿真效果的 ID，恢复已有配置时使用新实现。
     */
    const val IOS_CURL = 2

    @Deprecated("Use IOS_CURL", ReplaceWith("IOS_CURL"))
    const val CURL = IOS_CURL

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
     * 滑动翻页：仿iBook风格的滑动翻页
     */
    const val IBOOK_SLIDE = 6

    const val GOOGLE_CURL = 7

    /**
     * 根据指定的PageEffect类型创建对应的PageEffect
     */
    fun create(effectType: Int): AbstractPageContainer.PageEffect = when (effectType) {
        NO_ANIMATION -> NoAnimEffects.Horizontal()
        NO_ANIMATION_VERTICAL -> NoAnimEffects.Vertical()
        IOS_CURL -> IosCurlEffect()
        COVER -> CoverEffect()
        SLIDE -> SlideEffect()
        SCROLL -> ScrollEffect()
        IBOOK_SLIDE -> IBookSlideEffect()
        GOOGLE_CURL -> GoogleCurlEffect()
        else -> throw IllegalStateException("Not support PageEffectType: $effectType")
    }

}
