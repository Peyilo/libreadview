package org.peyilo.ioscurl.transition

import kotlin.math.max
import org.peyilo.ioscurl.control.CurlControl
import org.peyilo.ioscurl.model.CurlConfig
import org.peyilo.ioscurl.model.CurlKind

private const val AUTOMATIC_RUNNING = 1
private const val MANUAL_DRAGGING = 2
private const val STOPPING_STATE_THRESHOLD = 3
private const val ANIMATION_STOPPED = 4
private const val MANUAL_RELEASED = 5
private const val MAX_TRANSITIONS = 256
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val SECONDS_PER_NANO = 1e-9

data class CurlTransitionState(
    val id: Long,
    var type: Int,
    var direction: Int,
    var state: Int = AUTOMATIC_RUNNING,
    var willComplete: Boolean = true,
    var finished: Boolean = false,
    var active: Boolean = true,
    var completionCount: Int = 0,
    var completed: Boolean = false,
    var doubleSided: Boolean = false,
    var beginSeconds: Double = 0.0,
)

data class CurlTransitionDecision(
    val accepted: Boolean,
    val type: Int,
    val queued: Boolean = false,
    val began: Boolean = false,
    val scheduled: Boolean = false,
    val assertion: Boolean = false,
    val delayNanos: Long = 0,
    val previousId: Long = 0,
)

enum class CurlLayerOperation {
    Add,
    Above,
    Below,
    AddContent,
    Begin,
    Remove,
    Finally,
    Completion,
}

data class CurlLayerEvent(
    val operation: CurlLayerOperation,
    val state: Long,
    val face: Int = -1,
    val relativeState: Long = 0,
    val relativeFace: Int = 0,
    val finished: Boolean = false,
    val completed: Boolean = false,
)

/**
 * Optional portable orchestration. Android's single-page adapter continues to use the library's
 * serialized navigation transactions; a future spread compositor can consume these layer events. No
 * callback is invoked from this queue, and page/source lifetime remains with the host.
 */
class CurlTransitions(val delayBetweenSeconds: Double = 0.0) {
    val active: List<CurlTransitionState>
        get() = activeStates.toList()

    val pending: List<CurlTransitionState>
        get() = pendingStates.toList()

    private val activeStates = mutableListOf<CurlTransitionState>()
    private val pendingStates = mutableListOf<CurlTransitionState>()
    private var manual: CurlTransitionState? = null

    fun enqueue(
        config: CurlConfig,
        state: CurlTransitionState,
        now: Double,
    ): CurlTransitionDecision {
        check(activeStates.size < MAX_TRANSITIONS && pendingStates.size < MAX_TRANSITIONS)
        val old = activeStates.lastOrNull()
        val decision =
            decide(
                config,
                old,
                activeStates.size,
                pendingStates.size,
                manual != null,
                if (old == null) 0.0 else now - old.beginSeconds,
                delayBetweenSeconds,
            )
        if (!decision.accepted) return decision
        state.type = decision.type
        state.direction = config.direction.nativeValue
        state.state = if (automatic(state.type)) AUTOMATIC_RUNNING else MANUAL_DRAGGING
        state.willComplete = true
        state.finished = false
        state.active = true
        state.completed = false
        state.doubleSided = config.doubleSided
        if (!automatic(state.type)) manual = state
        if (decision.queued) pendingStates += state
        else {
            state.beginSeconds = now + decision.delayNanos * SECONDS_PER_NANO
            activeStates += state
        }
        return decision
    }

    fun endManual(state: CurlTransitionState, complete: Boolean): Boolean {
        if (automatic(state.type) || !setState(state, MANUAL_RELEASED, complete)) return false
        if (manual === state) manual = null
        return true
    }

    fun animationStopped(
        state: CurlTransitionState,
        finished: Boolean,
        now: Double,
    ): List<CurlLayerEvent> {
        if (activeStates.none { it === state }) return emptyList()
        if (state.completionCount > 0) state.completionCount--
        if (state.completionCount != 0) return emptyList()
        if (state.state or ANIMATION_STOPPED != MANUAL_RELEASED) {
            if (state.state >= STOPPING_STATE_THRESHOLD) state.state = ANIMATION_STOPPED
            return emptyList()
        }
        state.finished = finished
        if (!automatic(state.type) && state.state != MANUAL_RELEASED) return emptyList()
        state.completed = true

        val events = mutableListOf<CurlLayerEvent>()
        fun event(op: CurlLayerOperation, s: CurlTransitionState, face: Int = -1) {
            events +=
                CurlLayerEvent(
                    op,
                    s.id,
                    face,
                    finished = s.finished,
                    completed = s.willComplete,
                )
        }
        while (activeStates.firstOrNull()?.completed == true) {
            val head = activeStates.removeAt(0)
            event(
                CurlLayerOperation.Completion,
                head,
            )
            head.active = false
            event(CurlLayerOperation.Remove, head, 0)
            if (head.doubleSided)
                event(
                    CurlLayerOperation.Remove,
                    head,
                    1,
                )
            event(CurlLayerOperation.Finally, head)
            head.completed = false
            if (manual === head) manual = null
        }
        if (activeStates.isEmpty() && pendingStates.isNotEmpty()) {
            val next = pendingStates.removeAt(0)
            activeStates += next
            next.beginSeconds = now
            event(CurlLayerOperation.Begin, next)
        }
        return events
    }

    companion object {
        private fun automatic(type: Int) = CurlKind.isAutomatic(type)

        fun compatible(old: CurlTransitionState, type: Int, direction: Int) =
            (automatic(old.type) || old.state == MANUAL_RELEASED) &&
                ((type <= 2 && old.type < 3) || (type >= 3 && old.type >= 3)) &&
                old.direction == direction

        fun setState(state: CurlTransitionState, newState: Int, complete: Boolean): Boolean {
            if (
                if (automatic(state.type)) state.state != AUTOMATIC_RUNNING
                else newState < state.state
            )
                return false
            state.state = newState
            state.willComplete = complete
            return true
        }

        fun decide(
            config: CurlConfig,
            old: CurlTransitionState?,
            activeCount: Int,
            pendingCount: Int,
            manual: Boolean,
            elapsed: Double,
            delay: Double,
        ): CurlTransitionDecision {
            val type = CurlControl.type(config)
            if (!automatic(type) && (pendingCount != 0 || manual))
                return CurlTransitionDecision(
                    false,
                    type,
                )
            if (activeCount == 0 && pendingCount == 0)
                return CurlTransitionDecision(
                    true,
                    type,
                    began = true,
                )
            if (
                pendingCount != 0 ||
                    old == null ||
                    !compatible(
                        old,
                        type,
                        config.direction.nativeValue,
                    )
            )
                return if (!automatic(type))
                    CurlTransitionDecision(
                        false,
                        type,
                        assertion = true,
                    )
                else CurlTransitionDecision(true, type, queued = true)
            return CurlTransitionDecision(
                true,
                type,
                began = true,
                scheduled = true,
                delayNanos = (max(delay - elapsed, 0.0) * NANOS_PER_SECOND).toLong(),
                previousId = old.id,
            )
        }

        fun beginEvents(
            type: Int,
            doubleSided: Boolean,
            id: Long,
            previousId: Long = 0,
            previousActive: Boolean = false,
        ): List<CurlLayerEvent> = buildList {
            for (face in 0 until if (doubleSided) 2 else 1) {
                val operation =
                    if (previousId == 0L || !previousActive)
                        if (face == 0) CurlLayerOperation.Add else CurlLayerOperation.Above
                    else if (type <= 2)
                        if (face == 0) CurlLayerOperation.Below else CurlLayerOperation.Above
                    else if (face == 0) CurlLayerOperation.Above else CurlLayerOperation.Below
                val relative =
                    if (previousId == 0L || !previousActive) if (face == 0) 0 else id
                    else previousId
                add(
                    CurlLayerEvent(
                        operation,
                        id,
                        face,
                        relative,
                        if (previousId == 0L || !previousActive) 0 else face,
                    )
                )
                add(CurlLayerEvent(CurlLayerOperation.AddContent, id, face))
            }
        }
    }
}
