package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection
import org.peyilo.libreadview.utils.LogHelper

/**
 * 无动画翻页实现
 */
class NoAnimPageManagers private constructor() {

    companion object {
        private const val TAG = "NoAnimPageManagers"
    }

    /**
     * NoAnimPageManagers的水平布局实现
     */
    class Horizontal: FlipOnReleaseLayoutManager.Horizontal() {

        private var draggedView: View? = null           // 当前滑动手势选中的page

        // 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
        override fun prepareAnim(initDire: PageDirection) {
            draggedView = when(initDire) {
                PageDirection.NEXT -> pageContainer.getCurPage()!!
                PageDirection.PREV -> pageContainer.getPrevPage()!!
                else -> throw IllegalStateException()
            }
        }

        override fun startNextAnim() {
            draggedView!!.translationX = -pageContainer.width.toFloat()
        }

        override fun startPrevAnim() {
            draggedView!!.translationX = 0F
        }

        override fun abortAnim() {
            super.abortAnim()
            draggedView = null
        }

        private fun getTranslateX(position: Int): Float {
            val containerPageCount = pageContainer.getContainerPageCount()
            return when {
                containerPageCount >= 3 -> when {
                    position == 2 && !pageContainer.isFirstPage() -> -pageContainer.width.toFloat()
                    position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                    else -> 0F
                }
                containerPageCount == 2 -> when {
                    position == 1 && pageContainer.isLastPage() -> -pageContainer.width.toFloat()
                    else -> 0F
                }
                containerPageCount == 1 -> 0F
                else -> throw IllegalStateException("onAddPage: The pageContainer.itemCount is 0.")
            }
        }

        override fun onAddPage(view: View, position: Int) {
            view.translationX = getTranslateX(position)
            LogHelper.d(TAG, "onAddPage: childCount = ${pageContainer.childCount}, " +
                    "containerPageCount = ${pageContainer.getContainerPageCount()}, $position -> ${view.translationX}")
        }

    }

    /**
     * NoAnimPageManagers的垂直布局实现
     */
    class Vertical: FlipOnReleaseLayoutManager.Vertical() {

        private var draggedView: View? = null           // 当前滑动手势选中的page

        // 当initDire完成初始化且有开启动画的需要，PageContainer就会调用该函数
        override fun prepareAnim(initDire: PageDirection) {
            draggedView = when(initDire) {
                PageDirection.NEXT -> pageContainer.getCurPage()!!
                PageDirection.PREV -> pageContainer.getPrevPage()!!
                else -> throw IllegalStateException()
            }
        }

        override fun startNextAnim() {
            draggedView!!.translationY = -pageContainer.height.toFloat()
        }

        override fun startPrevAnim() {
            draggedView!!.translationY = 0F
        }

        override fun abortAnim() {
            super.abortAnim()
            draggedView = null
        }

        private fun getTranslateY(position: Int): Float {
            val containerPageCount = pageContainer.getContainerPageCount()
            return when {
                containerPageCount >= 3 -> when {
                    position == 2 && !pageContainer.isFirstPage() -> -pageContainer.height.toFloat()
                    position == 1 && pageContainer.isLastPage() -> -pageContainer.height.toFloat()
                    else -> 0F
                }
                containerPageCount == 2 -> when {
                    position == 1 && pageContainer.isLastPage() -> -pageContainer.height.toFloat()
                    else -> 0F
                }
                containerPageCount == 1 -> 0F
                else -> throw IllegalStateException("onAddPage: The pageContainer.itemCount is 0.")
            }
        }

        override fun onAddPage(view: View, position: Int) {
            view.translationY = getTranslateY(position)
            LogHelper.d(TAG, "onAddPage: childCount = ${pageContainer.childCount}, " +
                    "containerPageCount = ${pageContainer.getContainerPageCount()}, $position -> ${view.translationY}")
        }
    }
}