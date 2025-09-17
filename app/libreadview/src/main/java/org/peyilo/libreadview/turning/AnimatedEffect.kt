package org.peyilo.libreadview.turning

/**
 * 实现这个接口的PageEffect，表示实现了翻页动画
 */
interface AnimatedEffect {

    /**
     * 设置翻页动画持续时间，单位：ms毫秒
     */
    fun setAnimDuration(animDuration: Int)

}