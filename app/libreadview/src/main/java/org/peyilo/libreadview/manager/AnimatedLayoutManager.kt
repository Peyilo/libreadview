package org.peyilo.libreadview.manager

/**
 * 实现这个接口的LayoutManager，表示实现了翻页动画
 */
interface AnimatedLayoutManager {

    /**
     * 设置翻页动画持续时间，单位：ms毫秒
     */
    fun setAnimDuration(animDuration: Int)

}