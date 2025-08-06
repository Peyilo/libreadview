package org.peyilo.libreadview

import androidx.annotation.IntRange

interface PageNavigator {

    /**
     * 获取容器中全部page的数量
     */
    fun getContainerPageCount(): Int

    /**
     * 获取当前显示的page，位于容器中的索引，从1开始
     */
    fun getCurContainerPageIndex(): Int

    /**
     * 跳转到指定章节
     * @param pageIndex 章节索引，从1开始
     */
    fun navigatePage(@IntRange(from = 1) pageIndex: Int): Boolean

    /**
     * 跳转到第一页
     * @return 是否跳转成功
     */
    fun navigateToFirstPage(): Boolean

    /**
     * 跳转到最后一页
     * @return 是否跳转成功
     */
    fun navigateToLastPage(): Boolean

    /**
     * 跳转到下一页
     * @return 是否跳转成功
     */
    fun navigateToNextPage(): Boolean

    /**
     * 跳转到上一页
     * @return 是否跳转成功
     */
    fun navigateToPrevPage(): Boolean

}