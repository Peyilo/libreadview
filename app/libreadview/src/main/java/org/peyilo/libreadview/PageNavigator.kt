package org.peyilo.libreadview

interface PageNavigator {

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