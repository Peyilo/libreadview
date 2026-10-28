package org.peyilo.ioscurl

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ioscurl.control.*
import org.peyilo.ioscurl.gesture.*
import org.peyilo.ioscurl.model.*

class GestureTimingParityTest {
    private fun input(name: String) =
        DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name")!!))

    @Test
    fun nativePanSamplingGatesHysteresisAndMultitouch() {
        input("pan-golden.bin.gz").use { f ->
            repeat(f.readInt()) { trace ->
                val pan = CurlPan(f.readDouble(), f.readDouble(), CurlAxis.entries[f.readInt()])
                pan.sloppiness = f.readDouble()
                pan.canHandle = f.readInt() != 0
                pan.currentValid = f.readInt() != 0
                pan.incomingForward = f.readInt() != 0
                pan.incomingReverse = f.readInt() != 0
                pan.manualUncommitted = f.readInt() != 0
                pan.incomingUnknown = f.readInt() != 0
                repeat(f.readInt()) { step ->
                    val event =
                        CurlTouch(
                            CurlTouchKind.entries[f.readInt()],
                            f.readInt(),
                            CurlPoint(f.readDouble(), f.readDouble()),
                            f.readDouble(),
                        )
                    val actual = pan.event(event)
                    val tag = "trace $trace step $step"
                    assertEquals(tag, f.readInt(), actual.state.nativeValue)
                    assertEquals(tag, f.readInt(), actual.beginDirection)
                    assertEquals(tag, f.readInt(), actual.end.direction)
                    assertEquals(tag, f.readInt() != 0, actual.commandBegin)
                    for (p in listOf(actual.location, actual.translation, actual.velocity)) {
                        assertEquals(tag, f.readDouble(), p.x, 0.0)
                        assertEquals(tag, f.readDouble(), p.y, 0.0)
                    }
                    assertEquals(tag, f.readDouble(), actual.end.suggestedVelocity, 0.0)
                }
            }
        }
    }

    @Test
    fun nativeTapMovementAndTimerBoundary() {
        input("tap-golden.bin.gz").use { f ->
            repeat(f.readInt()) { trace ->
                val tap = CurlTap(f.readDouble())
                repeat(f.readInt()) { step ->
                    val event =
                        CurlTouch(
                            CurlTouchKind.entries[f.readInt()],
                            f.readInt(),
                            CurlPoint(f.readDouble(), f.readDouble()),
                            f.readDouble(),
                        )
                    val first = f.readInt() != 0
                    val expected = f.readInt()
                    assertEquals("tap $trace:$step", expected, tap.event(event, first).nativeValue)
                }
            }
        }
    }

    @Test
    fun nativeIndependentAnimationTracks() {
        input("timing-golden.bin.gz").use { f ->
            repeat(f.readInt()) { n ->
                val type = f.readInt()
                val ending = f.readInt() != 0
                val property = f.readInt()
                val duration = f.readDouble()
                val progress = f.readDouble()
                val expected = f.readDouble()
                val actual =
                    CurlTiming.solve(progress, CurlTiming.points(type, ending, property), duration)
                assertEquals("timing $n", expected.toRawBits(), actual.toRawBits())
            }
        }
    }
}
