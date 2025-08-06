package org.peyilo.libreadview

import androidx.annotation.IntRange

/**
 * 一个定义了书记导航所需实现的函数的接口
 */
interface BookNavigator {

    /**
     * 获取章节数量
     */
    fun getChapCount(): Int

    /**
     * 当前章节的理论分页数量（根据排版计算得出）
     */
    fun getCurChapPageCount(): Int

    /**
     * 获取当前章节索引，从1开始
     */
    fun getCurChapIndex(): Int

    /**
     * 获取当前页在当前章节的索引，从1开始
     */
    fun getCurChapPageIndex(): Int

    /**
     * 获取指定章节的标题
     * @return 标题, 可能为null
     */
    fun getChapTitle(@IntRange(from = 1) chapIndex: Int): String?

    /**
     * 跳转到指定章节的指定页
     * @param chapIndex 章节索引，从1开始
     * @param chapPageIndex 页索引，从1开始
     */
    fun navigateBook(@IntRange(from = 1) chapIndex: Int, @IntRange(from = 1) chapPageIndex: Int): Boolean

    /**
     * 跳转到指定章节
     * @param chapIndex 章节索引，从1开始
     * @return 是否跳转成功
     */
    fun navigateToChapter(@IntRange(from = 1) chapIndex: Int): Boolean

    /**
     * 跳转到下一章节
     * @return 是否跳转成功
     */
    fun navigateToNextChapter(): Boolean

    /**
     * 跳转到上一章节
     * @return 是否跳转成功
     */
    fun navigateToPrevChapter(): Boolean

}