package org.peyilo.libreadview.turning

import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.peyilo.ioscurl.gesture.CurlNavigation
import org.peyilo.ioscurl.gesture.CurlRecognition
import org.peyilo.ioscurl.model.CurlAxis
import org.peyilo.ioscurl.model.CurlBinding
import org.peyilo.ioscurl.model.CurlConfig
import org.peyilo.ioscurl.model.CurlDirection
import org.peyilo.ioscurl.model.CurlKind
import org.peyilo.ioscurl.model.CurlPoint
import org.peyilo.libreadview.AbstractPageContainer.PageDirection
import org.peyilo.libreadview.AbstractPageContainer.PageEffect
import org.peyilo.libreadview.turning.ioscurl.IosCurlInput
import org.peyilo.libreadview.turning.ioscurl.IosCurlSession
import org.peyilo.libreadview.util.LogHelper

/**
 * libreadview navigation adapter. The iOS recognizers, geometry, UVs and timing run in :ioscurl.
 */
class IosCurlEffect @JvmOverloads constructor(val options: IosCurlOptions = IosCurlOptions()) :
    PageEffect(), AnimatedEffect {
    private var input: IosCurlInput? = null
    private var session: IosCurlSession? = null
    private var direction = PageDirection.NONE
    private var started = false
    private var blockedGesture = false
    private var lastReleaseMillis: Long? = null
    private var durationScale = 1.0
    private var frameWidth = 0
    private var frameHeight = 0
    private var originPage: View? = null
    private var originIndex = -1

    private val density
        get() = pageContainer.resources.displayMetrics.density.toDouble()

    private val rtl
        get() =
            !options.vertical &&
                (options.rightToLeft
                    ?: (pageContainer.layoutDirection == View.LAYOUT_DIRECTION_RTL))

    private fun forward(physicalDirection: Int) = (physicalDirection == 0) != rtl

    private fun available(physicalDirection: Int) =
        if (forward(physicalDirection)) pageContainer.hasNextPage() else pageContainer.hasPrevPage()

    private fun now() = SystemClock.uptimeMillis() / 1000.0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            finishInterruptedTurn()
            val current = pageContainer.getCurPage() ?: return true
            if (current.width <= 0 || current.height <= 0) return true
            input =
                IosCurlInput(
                    current.width / density,
                    current.height / density,
                    options.vertical,
                    density,
                    current.left.toFloat(),
                    current.top.toFloat(),
                )
            blockedGesture = !intervalElapsed()
        }
        val recognizer = input ?: return true
        val result = recognizer.event(event, ::available)
        val physical = recognizer.beganDirection
        if (!started && !blockedGesture && physical != null) {
            direction = if (forward(physical)) PageDirection.NEXT else PageDirection.PREV
            started = true
            begin(result.pan.location, automatic = false)
        }
        if (started) {
            try {
                session?.updatePointer(result.pan.location, now())
            } catch (error: RuntimeException) {
                LogHelper.e(TAG, "Raster interrupted: ${error.message}")
                closeSession()
            } catch (error: OutOfMemoryError) {
                closeSession()
            }
        }
        if (result.terminal) {
            input = null
            if (started) {
                val commit =
                    result.pan.state == CurlRecognition.Ended &&
                        result.pan.end.direction == physical
                lastReleaseMillis = SystemClock.uptimeMillis()
                val active = session
                if (active == null) finishTurn(commit)
                else {
                    active.release(commit, result.pan.end.suggestedVelocity, now())
                    pageContainer.postInvalidateOnAnimation()
                }
            } else if (result.tapped && physical == null) {
                performTap(result.pan.location)
            }
        }
        return true
    }

    private fun performTap(point: CurlPoint) {
        val current = pageContainer.getCurPage() ?: return
        val handled =
            pageContainer.mOnClickRegionListener?.onClickRegion(
                (point.x * density / current.width * 100).toInt(),
                (point.y * density / current.height * 100).toInt(),
            ) ?: false
        if (handled) return
        val physical =
            CurlNavigation.tap(
                if (options.vertical) CurlAxis.Vertical else CurlAxis.Horizontal,
                current.width / density,
                current.height / density,
                point,
                tablet = minOf(current.width, current.height) / density >= 600,
            )
        if (physical >= 0) {
            if (forward(physical)) pageContainer.flipToNextPage()
            else pageContainer.flipToPrevPage()
        } else pageContainer.performClick()
    }

    private fun begin(initial: CurlPoint, automatic: Boolean) {
        val current = pageContainer.getCurPage() ?: return
        originPage = current
        originIndex = pageContainer.getCurContainerPageIndex()
        val target =
            if (direction == PageDirection.NEXT) pageContainer.getNextPage()
            else pageContainer.getPrevPage()
        if (target == null || current.width <= 0 || current.height <= 0 || durationScale == 0.0)
            return
        val binding =
            when {
                options.vertical && options.binding == IosCurlBinding.Minimum -> CurlBinding.Top
                options.vertical -> CurlBinding.Bottom
                options.binding == IosCurlBinding.Minimum -> CurlBinding.Left
                else -> CurlBinding.Right
            }
        val reverse = (direction == PageDirection.PREV) != rtl
        val config =
            CurlConfig(
                current.width / density,
                current.height / density,
                initial,
                binding,
                if (reverse) CurlDirection.Reverse else CurlDirection.Forward,
                if (automatic) CurlKind.Automatic else CurlKind.Manual,
            )
        frameWidth = pageContainer.width
        frameHeight = pageContainer.height
        try {
            session =
                IosCurlSession(
                    config,
                    RectF(
                        current.left.toFloat(),
                        current.top.toFloat(),
                        current.right.toFloat(),
                        current.bottom.toFloat(),
                    ),
                    density,
                    options,
                    durationScale,
                    now(),
                    current,
                    target,
                )
        } catch (error: OutOfMemoryError) {
            LogHelper.e(TAG, "Insufficient memory; retaining navigation without raster animation")
        } catch (error: RuntimeException) {
            LogHelper.e(TAG, "Raster unavailable: ${error.message}")
        }
        pageContainer.postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        val active = session ?: return
        if (pageContainer.width != frameWidth || pageContainer.height != frameHeight) {
            finishInterruptedTurn()
            return
        }
        try {
            val time = now()
            if (active.advance(time)) finishTurn(active.commits)
            else if (active.needsFrame(time)) pageContainer.postInvalidateOnAnimation()
        } catch (error: RuntimeException) {
            LogHelper.e(TAG, "Raster interrupted: ${error.message}")
            if (active.isSettling) finishTurn(active.commits) else closeSession()
        } catch (error: OutOfMemoryError) {
            if (active.isSettling) finishTurn(active.commits) else closeSession()
        }
    }

    private fun intervalElapsed(): Boolean =
        lastReleaseMillis?.let {
            SystemClock.uptimeMillis() - it >= minPageTurnInterval
        } ?: true

    override fun flipToNextPage(limited: Boolean) = automaticTurn(PageDirection.NEXT, limited)

    override fun flipToPrevPage(limited: Boolean) = automaticTurn(PageDirection.PREV, limited)

    private fun automaticTurn(requested: PageDirection, limited: Boolean): Boolean {
        if (limited && !intervalElapsed()) return false
        finishInterruptedTurn()
        val available =
            if (requested == PageDirection.NEXT) pageContainer.hasNextPage()
            else pageContainer.hasPrevPage()
        if (!available) return false
        direction = requested
        started = true
        lastReleaseMillis = SystemClock.uptimeMillis()
        begin(CurlPoint(0.0, 0.0), automatic = true)
        val active = session
        if (active == null) finishTurn(commit = true)
        else {
            active.release(commit = true, velocity = 0.0, now = now())
            pageContainer.postInvalidateOnAnimation()
        }
        return true
    }

    private fun finishInterruptedTurn() {
        // Configuration is also valid before attachment (for example in an apply block).
        if (!started && session == null && input == null) return
        val active = session
        finishTurn(active?.isSettling == true && active.commits)
        input = null
    }

    private fun finishTurn(commit: Boolean) {
        val completedDirection = direction
        // Dataset replacement can stop the effect after removing children or after an explicit
        // navigation changed the index. Never commit the old destination into that new state.
        val sameOrigin =
            pageContainer.getCurContainerPageIndex() == originIndex &&
                pageContainer.getPageChildCount() ==
                    minOf(3, pageContainer.getContainerPageCount()) &&
                pageContainer.getPageChildCount() > 0 &&
                pageContainer.getCurPage() === originPage
        closeSession()
        direction = PageDirection.NONE
        started = false
        originPage = null
        originIndex = -1
        if (commit && sameOrigin)
            when (completedDirection) {
                PageDirection.NEXT -> if (pageContainer.hasNextPage()) nextCarouselLayout()
                PageDirection.PREV -> if (pageContainer.hasPrevPage()) prevCarouselLayout()
                else -> Unit
            }
        positionPages()
        pageContainer.invalidate()
    }

    private fun closeSession() {
        val previous = session
        session = null
        previous?.close()
    }

    override fun dispatchDraw(canvas: Canvas) {
        session?.draw(canvas)
    }

    override fun needDrawChild() = session == null

    override fun forceNotInLayoutOrScroll() = finishInterruptedTurn()

    override fun onInitPagePosition() = positionPages()

    override fun onRestoreStatus() {
        finishTurn(commit = false)
        input = null
    }

    override fun onAddPage(view: View, position: Int) {
        positionPage(view, position)
    }

    private fun positionPages() {
        for (index in 0 until pageContainer.getPageChildCount()) {
            positionPage(pageContainer.getPageChildAt(index), index)
        }
    }

    private fun positionPage(view: View, index: Int) {
        val previous =
            !pageContainer.isFirstPage() && index >= if (pageContainer.isLastPage()) 1 else 2
        view.translationX =
            if (previous && !options.vertical) -pageContainer.width.toFloat() else 0f
        view.translationY =
            if (previous && options.vertical) -pageContainer.height.toFloat() else 0f
        view.visibility = View.VISIBLE
    }

    /**
     * 400ms preserves recovered timings; other values scale all tracks, zero disables animation.
     */
    override fun setAnimDuration(animDuration: Int) {
        require(animDuration >= 0)
        forceNotInLayoutOrScroll()
        durationScale = animDuration / DEFAULT_AUTOMATIC_DURATION_MS
    }

    override fun onDestroy() {
        // PageEffect releases its container reference before this callback.
        closeSession()
        input = null
        originPage = null
        originIndex = -1
        started = false
    }

    private companion object {
        const val TAG = "IosCurlEffect"
        const val DEFAULT_AUTOMATIC_DURATION_MS = 400.0
    }
}
