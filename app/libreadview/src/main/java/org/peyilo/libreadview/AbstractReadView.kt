package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntRange
import org.peyilo.libreadview.loader.BookLoader

/**
 * ReadView必须支持以下功能：
 *  - 书籍导航
 *  - 长按选择页面中的文字
 */
abstract class AbstractReadView(
    context: Context, attrs: AttributeSet? = null
): PageContainer(context, attrs), TextSelector, BookNavigator {

    /**
     * 预处理章节数：需要预处理当前章节之前的preprocessBefore个章节
     */
    var preprocessBefore = 0

    /**
     * 预处理章节数：需要预处理当前章节之后的preprocessBehind个章节
     */
    var preprocessBehind = 0


    /**
     * 对指定章节及其前后指定数量的章节执行预处理操作。
     *
     * 此方法以 `chapIndex` 为中心，向前处理最多 [preprocessBefore] 个章节，
     * 向后处理最多 [preprocessBehind] 个章节，共处理一个连续的章节窗口区间。
     *
     * @param chapIndex 要处理的中心章节索引（从 1 开始）
     * @param process   对每个章节索引执行的处理逻辑（回调函数）
     *
     * 示例：若 chapIndex = 5，preprocessBefore = 2，preprocessBehind = 1，
     *      则会处理章节 [3, 4, 5, 6]（只要不越界）
     */
    private fun processNearbyChapters(@IntRange(from = 1) chapIndex: Int, process: (index: Int) -> Unit) {
        val chapCount = getChapCount()
        process(chapIndex)
        var i = chapIndex - 1
        while (i > 0 && i >= chapIndex - preprocessBefore) {
            process(i)
            i--
        }
        i = chapIndex + 1
        while (i <= chapCount && i <= chapIndex + preprocessBehind) {
            process(i)
            i++
        }
    }

    /**
     * 打开书籍并跳转到指定的章节与页码。
     *
     * 子类应实现此方法以完成具体的书籍加载逻辑，例如：
     * - 加载章节内容
     * - 初始化页面渲染数据
     * - 滚动或分页定位到指定章节和页码
     *
     * @param loader 用于加载书籍内容的加载器实例
     * @param chapIndex 要打开的章节索引，从 1 开始（默认打开第 1 章）
     * @param pageIndex 要跳转的页码索引，从 1 开始（默认跳转到第 1 页）
     *
     * 实现需注意：
     * - 若指定章节或页码不存在，应进行合理的边界检查
     * - 应通过 `processNearbyChapters` 进行相邻章节处理
     */
    abstract fun openBook(
        loader: BookLoader,
        @IntRange(from = 1) chapIndex: Int = 1,
        @IntRange(from = 1) pageIndex: Int = 1
    )

}