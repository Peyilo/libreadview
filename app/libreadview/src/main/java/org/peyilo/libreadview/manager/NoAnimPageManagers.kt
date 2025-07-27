package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection

/**
 * 无动画翻页实现
 */
class NoAnimPageManagers {

    /**
     * NoAnimPageManagers的水平布局实现
     */
    class Horizontal: HorizontalPageManager() {

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

        override fun onNextCarouselLayout() {
            if (pageContainer.itemCount >= 3) {
                pageContainer.apply {
                    getNextPage()?.translationX = 0F
                }
            }
        }

        override fun onPrevCarouselLayout() {
            pageContainer.apply {
                getPrevPage()?.translationX = -width.toFloat()
            }
        }
    }

    /**
     * NoAnimPageManagers的垂直布局实现
     */
    class Vertical: VerticalPageManager() {

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

        override fun onNextCarouselLayout() {
            if (pageContainer.itemCount >= 3) {
                pageContainer.apply {
                    getNextPage()?.translationY = 0F
                }
            }
        }

        override fun onPrevCarouselLayout() {
            pageContainer.apply {
                getPrevPage()?.translationY = -height.toFloat()
            }
        }

    }
}