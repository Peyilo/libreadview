package org.peyilo.ioscurl.gesture

import kotlin.math.sqrt
import org.peyilo.ioscurl.model.CurlPoint

/** 1.5s hold deadline and 45pt movement radius; timer/event tie order is chosen by the host. */
class CurlTap(var sloppiness: Double = 1.0) {
    private companion object {
        const val MAX_TAP_DURATION_SECONDS = 1.5
        const val MAX_MOVEMENT_POINTS = 45.0
    }

    var state = CurlRecognition.Possible
        private set

    private var activePointer: Int? = null
    private var deadline = Double.POSITIVE_INFINITY
    private var start = CurlPoint(0.0, 0.0)

    fun advance(time: Double, inclusive: Boolean = true): CurlRecognition {
        if (
            state == CurlRecognition.Possible &&
                activePointer != null &&
                (if (inclusive) time >= deadline else time > deadline)
        )
            state = CurlRecognition.Failed
        return state
    }

    fun event(event: CurlTouch, timersFirst: Boolean = true): CurlRecognition {
        advance(event.seconds, timersFirst)
        if (state.nativeValue >= 3) return state
        when (event.kind) {
            CurlTouchKind.Down ->
                if (activePointer != null) {
                    if (event.pointer != activePointer) state = CurlRecognition.Failed
                } else {
                    activePointer = event.pointer
                    start = event.point
                    deadline = event.seconds + MAX_TAP_DURATION_SECONDS
                }

            CurlTouchKind.Move ->
                if (activePointer == event.pointer) {
                    val dx = event.point.x - start.x
                    val dy = event.point.y - start.y
                    if (sqrt(dx * dx + dy * dy) > MAX_MOVEMENT_POINTS * sloppiness)
                        state = CurlRecognition.Failed
                }

            CurlTouchKind.Up ->
                if (activePointer == event.pointer) {
                    activePointer = null
                    deadline = Double.POSITIVE_INFINITY
                    state = CurlRecognition.Ended
                }

            CurlTouchKind.Cancel ->
                if (activePointer == event.pointer) {
                    activePointer = null
                    deadline = Double.POSITIVE_INFINITY
                    state = CurlRecognition.Failed
                }
        }
        return state
    }
}
