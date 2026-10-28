package org.peyilo.ioscurl

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ioscurl.layout.*
import org.peyilo.ioscurl.model.*
import org.peyilo.ioscurl.transition.*

class LayoutTransitionParityTest {
    private fun input(name: String) =
        DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name")!!))

    @Test
    fun originalPageSourcesMirrorsAndUnderlaysIncludingReservedSpreads() {
        input("layout-golden.bin.gz").use { f ->
            repeat(f.readInt()) {
                val binding = f.readInt()
                val direction = f.readInt()
                val ds = f.readInt() != 0
                val c =
                    CurlConfig(
                        320.0,
                        458.5,
                        binding = CurlBinding.entries.first { it.nativeValue == binding },
                        direction = CurlDirection.entries[direction],
                        doubleSided = ds,
                    )
                val actual = CurlLayout.build(c)
                val passes = f.readInt()
                assertEquals(passes, actual.passes.size)
                fun rect() =
                    CurlRect(f.readDouble(), f.readDouble(), f.readDouble(), f.readDouble())
                for (p in actual.passes) {
                    assertEquals(f.readInt(), p.source.nativeValue)
                    assertEquals(rect(), p.frame)
                    assertEquals(f.readInt() != 0, p.front)
                    assertEquals(f.readInt() != 0, p.back)
                    assertEquals(f.readInt() != 0, p.mirrorContent)
                }
                for (pages in listOf(actual.underlays, actual.outgoing, actual.incoming)) {
                    assertEquals(f.readInt(), pages.size)
                    for (p in pages) {
                        assertEquals(f.readInt(), p.source.nativeValue)
                        assertEquals(rect(), p.frame)
                    }
                }
            }
        }
    }

    @Test
    fun originalCompatibilityQueueAndScheduleDecisions() {
        input("transition-golden.bin.gz").use { f ->
            repeat(f.readInt()) { n ->
                val binding = f.readInt()
                val direction = f.readInt()
                val kind = f.readInt()
                val ds = f.readInt() != 0
                val c =
                    CurlConfig(
                        320.0,
                        458.5,
                        CurlPoint(300.0, 120.0),
                        CurlBinding.entries.first { it.nativeValue == binding },
                        CurlDirection.entries[direction],
                        CurlKind.entries.first { it.nativeValue == kind },
                        ds,
                    )
                val present = f.readInt() != 0
                val old =
                    CurlTransitionState((n + 1).toLong(), f.readInt(), f.readInt(), f.readInt())
                val ac = f.readInt()
                val pending = f.readInt()
                val manual = f.readInt() != 0
                val elapsed = f.readDouble()
                val delay = f.readDouble()
                val d =
                    CurlTransitions.decide(
                        c,
                        old.takeIf { present },
                        ac,
                        pending,
                        manual,
                        elapsed,
                        delay,
                    )
                assertEquals("accepted $n", f.readInt() != 0, d.accepted)
                assertEquals("queued $n", f.readInt() != 0, d.queued)
                assertEquals("began $n", f.readInt() != 0, d.began)
                assertEquals("scheduled $n", f.readInt() != 0, d.scheduled)
                assertEquals("assertion $n", f.readInt() != 0, d.assertion)
                assertEquals("type $n", f.readInt(), d.type)
                assertEquals("delay $n", f.readLong(), d.delayNanos)
                assertEquals("previous $n", f.readLong(), d.previousId)
            }
        }
    }

    @Test
    fun fifoCleanupAndPendingOppositeDirection() {
        val queue = CurlTransitions()
        val config = CurlConfig(320.0, 458.5, kind = CurlKind.Automatic)
        val a = CurlTransitionState(1, 1, 0)
        val b = CurlTransitionState(2, 1, 0)
        val c = CurlTransitionState(3, 4, 1)
        assertTrue(queue.enqueue(config, a, 0.0).began)
        assertTrue(queue.enqueue(config, b, .1).scheduled)
        assertTrue(queue.enqueue(config.copy(direction = CurlDirection.Reverse), c, .2).queued)
        assertTrue(queue.animationStopped(b, true, .5).isEmpty())
        val events = queue.animationStopped(a, true, .6)
        assertEquals(
            listOf(1L, 2L),
            events.filter { it.operation == CurlLayerOperation.Completion }.map { it.state },
        )
        assertEquals(CurlLayerOperation.Begin, events.last().operation)
        assertEquals(3L, queue.active.single().id)
        assertTrue(queue.pending.isEmpty())
    }
}
