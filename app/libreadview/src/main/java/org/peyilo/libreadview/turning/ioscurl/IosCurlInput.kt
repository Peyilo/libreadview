package org.peyilo.libreadview.turning.ioscurl

import android.view.MotionEvent
import org.peyilo.ioscurl.gesture.CurlPan
import org.peyilo.ioscurl.gesture.CurlPanResult
import org.peyilo.ioscurl.gesture.CurlRecognition
import org.peyilo.ioscurl.gesture.CurlTap
import org.peyilo.ioscurl.gesture.CurlTouch
import org.peyilo.ioscurl.gesture.CurlTouchKind
import org.peyilo.ioscurl.model.CurlAxis
import org.peyilo.ioscurl.model.CurlPoint

/** Converts Android pointer events into the recovered recognizers' logical points and seconds. */
internal class IosCurlInput(
    width: Double,
    height: Double,
    vertical: Boolean,
    private val density: Double,
    private val left: Float,
    private val top: Float,
) {
    private val pan =
        CurlPan(width, height, if (vertical) CurlAxis.Vertical else CurlAxis.Horizontal)
    private val tap = CurlTap()
    var beganDirection: Int? = null
        private set

    data class Update(val pan: CurlPanResult, val terminal: Boolean, val tapped: Boolean)

    fun event(event: MotionEvent, available: (Int) -> Boolean): Update {
        if (beganDirection == null) {
            pan.incomingForward = available(0)
            pan.incomingReverse = available(1)
        }
        val trackedPointer = pan.trackedPointer
        val kind =
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> CurlTouchKind.Down
                MotionEvent.ACTION_MOVE -> CurlTouchKind.Move
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> CurlTouchKind.Up
                else -> CurlTouchKind.Cancel
            }
        val index =
            if (kind == CurlTouchKind.Move) {
                trackedPointer?.let(event::findPointerIndex)?.takeIf { it >= 0 }
            } else event.actionIndex
        if (index != null) {
            val touch =
                CurlTouch(
                    kind,
                    event.getPointerId(index),
                    CurlPoint(
                        (event.getX(index) - left) / density,
                        (event.getY(index) - top) / density,
                    ),
                    event.eventTime / 1000.0,
                )
            pan.event(touch)
            tap.event(touch)
        }
        val result = pan.snapshot()
        if (
            beganDirection == null &&
                result.commandBegin &&
                (result.state == CurlRecognition.Began || result.state == CurlRecognition.Changed)
        ) {
            beganDirection = result.beginDirection
            pan.manualUncommitted = true
        }
        val terminal =
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                    event.getPointerId(event.actionIndex) == trackedPointer)
        return Update(result, terminal, terminal && tap.state == CurlRecognition.Ended)
    }
}
