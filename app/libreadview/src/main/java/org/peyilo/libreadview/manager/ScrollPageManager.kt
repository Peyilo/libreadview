package org.peyilo.libreadview.manager

import android.view.View
import org.peyilo.libreadview.PageContainer.PageDirection
import kotlin.math.max
import kotlin.math.min

/**
 * 滚动翻页实现
 */
class ScrollPageManager: NoFlipOnReleasePageManager.Vertical() {

    private var curPage: View? = null
    private lateinit var prevPages: List<View>
    private lateinit var nextPages: List<View>

    private var lastdy = 0F

    companion object {
        private const val TAG = "ScrollPageManager"
    }
    
    override fun flipToNextPage(limited: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun flipToPrevPage(limited: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun initPagePosition() {
        needInitPagePosition = false
        pageContainer.apply {
            getAllPrevPages().forEachIndexed { index, page ->
                page.translationY = -height.toFloat() * (index + 1)
            }
            // 将位于当前页之后的page，全部移动到屏幕外之下
            getAllNextPages().forEachIndexed { index, page ->
                page.translationY = height.toFloat() * (index + 1)
            }
        }
    }

    override fun prepareAnim(initDire: PageDirection) {
        if (initDire == PageDirection.NONE)
            throw IllegalStateException("prepareAnim: initDire is PageDirection.None")
        lastdy = 0F
        curPage = pageContainer.getCurPage()
        prevPages = pageContainer.getAllPrevPages()
        nextPages = pageContainer.getAllNextPages()
    }

    private fun getTopTranslationY(): Float {
        var topTranslationY = 0F
        prevPages.forEach { prevPage ->
            if (prevPage.translationY < topTranslationY) {
                topTranslationY = prevPage.translationY
            }
        }
        curPage?.let {
           if (curPage!!.translationY < topTranslationY) {
               topTranslationY = curPage!!.translationY
           }
        }
        return topTranslationY
    }

    private fun getBottomTranslationY(): Float {
        var bottomTranslationY = 0F
        nextPages.forEach { nextPage ->
            if (nextPage.translationY > bottomTranslationY) {
                bottomTranslationY = nextPage.translationY
            }
        }
        curPage?.let {
            if (curPage!!.translationY > bottomTranslationY) {
                bottomTranslationY = curPage!!.translationY
            }
        }
        return bottomTranslationY
    }

    private fun canMoveDown() = getBottomTranslationY() != 0F
    private fun canMoveUp() = getTopTranslationY() != 0F

    override fun onDragging(initDire: PageDirection, dx: Float, dy: Float) {
        if (initDire == PageDirection.NONE)
            throw IllegalStateException("onDragging: initDire is PageDirection.None")

        var moveDis = dy - lastdy
        if (moveDis > 0) {
            val topTranslationY = getTopTranslationY()
            moveDis = min(moveDis, -topTranslationY)
            if (moveDis > 0) {
                curPage?.translationY += moveDis
                prevPages.forEach {
                    it.translationY += moveDis
                }
                nextPages.forEach {
                    it.translationY += moveDis
                }
            }
        } else {
            val bottomTranslationY = getBottomTranslationY()
            moveDis = max(moveDis, -bottomTranslationY)
            if (moveDis < 0) {
                curPage?.translationY += moveDis
                prevPages.forEach {
                    it.translationY += moveDis
                }
                nextPages.forEach {
                    it.translationY += moveDis
                }
            }
        }

        lastdy = dy
    }

}