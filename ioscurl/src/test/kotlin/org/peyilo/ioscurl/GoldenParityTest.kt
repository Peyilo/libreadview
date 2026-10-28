package org.peyilo.ioscurl

import java.io.DataInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ioscurl.control.*
import org.peyilo.ioscurl.math.*
import org.peyilo.ioscurl.mesh.*
import org.peyilo.ioscurl.model.*

class GoldenParityTest {
    private fun input(name: String) =
        DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name")!!))

    @Test
    fun originalMeshVerticesIndicesModesAndBatchOrder() {
        val mesh = CurlMesh()
        val digest = MessageDigest.getInstance("SHA-256")
        input("mesh-golden.bin.gz").use { f ->
            repeat(f.readInt()) { case ->
                val w = f.readDouble()
                val h = f.readDouble()
                val scale = f.readDouble()
                val values = CurlValues(f.readDouble(), f.readDouble(), f.readDouble())
                val start = f.readDouble()
                val end = f.readDouble()
                val front = f.readInt() != 0
                val back = f.readInt() != 0
                fun color() =
                    CurlColor(f.readDouble(), f.readDouble(), f.readDouble(), f.readDouble())
                val material = CurlMaterial(color(), color(), color(), color())
                val options = f.readInt()
                fun rect() =
                    CurlRect(f.readDouble(), f.readDouble(), f.readDouble(), f.readDouble())
                val sr = rect()
                val target = rect()
                val clip = rect()
                val shadow = rect()
                val actual =
                    mesh.build(
                        CurlMeshInput(
                            values,
                            w,
                            h,
                            scale,
                            start,
                            end,
                            front,
                            back,
                            material,
                            sr.takeIf { options and 1 != 0 },
                            target.takeIf { options and 2 != 0 },
                            clip.takeIf { options and 4 != 0 },
                            shadow.takeIf { options and 8 != 0 },
                        )
                    )
                assertEquals("width $case", f.readInt(), actual.width)
                assertEquals("height $case", f.readInt(), actual.height)
                val count = f.readInt()
                assertEquals("batches $case", count, actual.batches.size)
                repeat(count) { n ->
                    val b = actual.batches[n]
                    val tag = "case $case batch $n"
                    assertEquals(tag, f.readInt(), b.primitive)
                    assertEquals(tag, f.readInt(), b.mode)
                    assertEquals(tag, f.readInt(), b.blendFunction)
                    assertEquals(tag, f.readInt() != 0, b.blendEnabled)
                    assertEquals(tag, f.readInt(), b.vertexCount)
                    assertEquals(tag, f.readInt(), b.count)
                    val expected = ByteArray(32)
                    f.readFully(expected)
                    for (k in b.vertices.indices step 64) {
                        digest.update(b.vertices, k, 24)
                        digest.update(b.vertices, k + 40, 8)
                    }
                    val hash = digest.digest()
                    if (!expected.contentEquals(hash))
                        java.io.File("build/mesh-failure-$case-$n.bin").writeBytes(b.vertices)
                    assertArrayEquals("vertex attributes $tag", expected, hash)
                    val indices = f.readInt()
                    if (indices < 0) assertNull(b.indices)
                    else assertArrayEquals(tag, ShortArray(indices) { f.readShort() }, b.indices)
                }
            }
        }
    }

    @Test
    fun originalGestureToFilterParameters() {
        input("control-golden.bin.gz").use { f ->
            repeat(f.readInt()) { n ->
                val w = f.readDouble()
                val h = f.readDouble()
                val initial = CurlPoint(f.readDouble(), f.readDouble())
                val nativeBinding = f.readInt()
                val binding = CurlBinding.entries.first { it.nativeValue == nativeBinding }
                val d = f.readInt()
                val kind = f.readInt()
                val ds = f.readInt() != 0
                val c =
                    CurlConfig(
                        w,
                        h,
                        initial,
                        binding,
                        CurlDirection.entries[d],
                        CurlKind.entries.first { it.nativeValue == kind },
                        ds,
                    )
                val result =
                    CurlControl.update(c, CurlPoint(f.readDouble(), f.readDouble())).renderValues()
                val expected =
                    CurlValues(f.readDouble(), f.readDouble(), f.readDouble()).renderValues()
                assertEquals("time $n", expected.time.toRawBits(), result.time.toRawBits())
                assertEquals("radius $n", expected.radius.toRawBits(), result.radius.toRawBits())
                assertEquals("angle $n", expected.angle.toRawBits(), result.angle.toRawBits())
            }
        }
    }

    @Test
    fun originalMathRounding() {
        input("math-golden.bin.gz").use { f ->
            repeat(f.readInt()) { n ->
                val fn = f.readInt()
                val x = f.readFloat()
                val expected = f.readFloat()
                val actual =
                    when (fn) {
                        0 -> AppleMath.sin(x)
                        1 -> AppleMath.cos(x)
                        2 -> AppleMath.tan(x)
                        3 -> AppleMath.acos(x)
                        else -> AppleMath.exp(x)
                    }
                assertEquals("math $n fn=$fn x=$x", expected.toRawBits(), actual.toRawBits())
            }
        }
    }

    @Test
    fun fusedArithmeticRetainsProductAndSumRounding() {
        val rng = java.util.Random(20261027)
        repeat(100000) {
            val a = (rng.nextDouble() - .5) * 10000
            val b = (rng.nextDouble() - .5) * 10000
            val c = (rng.nextDouble() - .5) * 10000
            assertEquals(Math.fma(a, b, c).toRawBits(), BinaryMath.fma(a, b, c).toRawBits())
            assertEquals(
                Math.fma(a.toFloat(), b.toFloat(), c.toFloat()).toRawBits(),
                BinaryMath.fma(a.toFloat(), b.toFloat(), c.toFloat()).toRawBits(),
            )
        }
    }
}
