package org.peyilo.libreadview.turning.ioscurl

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import org.peyilo.ioscurl.mesh.CurlColor
import org.peyilo.ioscurl.mesh.CurlMaterial

/** Samples the actual turning front page once; neutral black shadows retain the receiving hue. */
internal object IosCurlMaterial {
    fun resolve(front: View, snapshot: Bitmap, shadowOpacity: Float): CurlMaterial {
        val background =
            (front.background as? ColorDrawable)?.color?.takeIf { Color.alpha(it) == 255 }
                ?: dominantColor(snapshot)
        val material = CurlMaterial()
        return material.copy(
            back =
                CurlColor(
                    Color.red(background) / 255.0,
                    Color.green(background) / 255.0,
                    Color.blue(background) / 255.0,
                    material.back.alpha,
                ),
            shadow = CurlColor(0.0, 0.0, 0.0, shadowOpacity.toDouble()),
        )
    }

    private fun dominantColor(snapshot: Bitmap): Int {
        val buckets = HashMap<Int, LongArray>()
        for (row in 0 until SAMPLE_GRID) for (column in 0 until SAMPLE_GRID) {
            val x =
                ((column + 0.5) * snapshot.width / SAMPLE_GRID)
                    .toInt()
                    .coerceIn(0, snapshot.width - 1)
            val y =
                ((row + 0.5) * snapshot.height / SAMPLE_GRID)
                    .toInt()
                    .coerceIn(0, snapshot.height - 1)
            val color = snapshot.getPixel(x, y)
            if (Color.alpha(color) == 0) continue
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { LongArray(4) }
            bucket[0] += r
            bucket[1] += g
            bucket[2] += b
            bucket[3]++
        }
        val color = buckets.values.maxByOrNull { it[3] } ?: return Color.WHITE
        return Color.rgb(
            (color[0] / color[3]).toInt(),
            (color[1] / color[3]).toInt(),
            (color[2] / color[3]).toInt(),
        )
    }

    private const val SAMPLE_GRID = 8
}
