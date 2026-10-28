package org.peyilo.libreadview.turning.ioscurl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.floor
import kotlin.math.sqrt
import org.peyilo.ioscurl.control.CurlControl
import org.peyilo.ioscurl.control.CurlTiming
import org.peyilo.ioscurl.layout.CurlLayout
import org.peyilo.ioscurl.layout.CurlPageSource
import org.peyilo.ioscurl.mesh.CurlMesh
import org.peyilo.ioscurl.mesh.CurlMeshInput
import org.peyilo.ioscurl.model.CurlConfig
import org.peyilo.ioscurl.model.CurlKind
import org.peyilo.ioscurl.model.CurlPoint
import org.peyilo.libreadview.turning.IosCurlOptions
import org.peyilo.libreadview.turning.ioscurl.render.IosCurlRenderer

/** One immutable page pair and one managed rendering transaction. Never queries book navigation. */
internal class IosCurlSession(
    private val config: CurlConfig,
    val bounds: RectF,
    private val density: Double,
    private val options: IosCurlOptions,
    private val durationScale: Double,
    private val startedAt: Double,
    current: View,
    target: View,
) : AutoCloseable {
    private val mesh = CurlMesh()
    private val layout = CurlLayout.build(config)
    private val begin = CurlControl.begin(config)
    private val automatic = config.kind == CurlKind.Automatic
    private val turningTexture = texture(layout.passes.single().source)
    private val underlays = layout.underlays.map { texture(it.source) to it.frame }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var renderer: IosCurlRenderer? = null
    private var output: Bitmap? = null
    private var values = begin.from
    private var material = org.peyilo.ioscurl.mesh.CurlMaterial()
    private var pointer = config.initial
    private var settleFrom = values
    private var settleTo = values
    private var settleStartedAt: Double? = null
    private var settleDuration = 0.0
    var commits = false
        private set

    init {
        var first: Bitmap? = null
        var second: Bitmap? = null
        try {
            val (width, height) =
                snapshotSize(current.width, current.height, options.bitmapBudgetBytes)
            first = capture(current, width, height)
            second = capture(target, width, height)
            material =
                IosCurlMaterial.resolve(
                    if (turningTexture == 0) current else target,
                    if (turningTexture == 0) first else second,
                    options.shadowOpacity,
                )
            // Own the EGL resources before upload, so a partial texture-upload failure is closed.
            renderer = IosCurlRenderer()
            renderer!!.upload(first, second)
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            renderer!!.bindOutput(output!!)
            render()
        } catch (error: Throwable) {
            close()
            throw error
        } finally {
            first?.recycle()
            second?.recycle()
        }
    }

    val isSettling: Boolean
        get() = settleStartedAt != null

    fun needsFrame(now: Double): Boolean =
        isSettling || now - startedAt < begin.durationSeconds * durationScale

    fun updatePointer(point: CurlPoint, now: Double) {
        if (isSettling) return
        pointer = point
        advance(now)
    }

    fun release(commit: Boolean, velocity: Double, now: Double) {
        commits = commit
        settleFrom = values.renderValues()
        if (automatic) {
            settleFrom = begin.from
            settleTo = begin.to
            settleDuration = begin.durationSeconds * durationScale
        } else {
            val endpoints = CurlControl.endpoints(config)
            settleTo = if (commit) endpoints.to else endpoints.from
            settleDuration =
                CurlControl.duration(config, velocity, commit, settleFrom.time) * durationScale
        }
        settleStartedAt = now
    }

    /** Advances the original independent timing tracks; true only when settling has finished. */
    fun advance(now: Double): Boolean {
        val settling = settleStartedAt
        if (settling != null) {
            val fraction =
                if (settleDuration == 0.0) 1.0
                else ((now - settling) / settleDuration).coerceIn(0.0, 1.0)
            values =
                CurlTiming.interpolate(
                    settleFrom,
                    settleTo,
                    CurlControl.type(config),
                    !automatic,
                    fraction,
                    settleDuration,
                )
            render()
            return fraction >= 1.0
        }
        if (!automatic) {
            val target = CurlControl.update(config, pointer).renderValues()
            val duration = begin.durationSeconds * durationScale
            val elapsed = (now - startedAt).coerceAtLeast(0.0)
            values =
                if (duration > 0.0 && elapsed < duration) {
                    CurlTiming.interpolate(
                        begin.from,
                        target,
                        CurlControl.type(config),
                        false,
                        elapsed / duration,
                        duration,
                    )
                } else target
            render()
        }
        return false
    }

    private fun render() {
        val frame =
            mesh.build(
                CurlMeshInput(
                    values.renderValues(),
                    config.width,
                    config.height,
                    density.coerceIn(0.1, 4.0),
                    material = material,
                    shadowSoftness = options.shadowSoftness.toDouble(),
                )
            )
        // Single-column: both faces sample the same turning sheet. Independent recto/verso
        // sources remain an explicit CurlLayout responsibility for a future spread adapter.
        renderer!!.render(
            frame,
            config.width.toFloat(),
            config.height.toFloat(),
            turningTexture,
            1 - turningTexture,
            underlays,
        )
    }

    fun draw(canvas: Canvas) {
        val bitmap = output ?: return
        val save = canvas.save()
        try {
            canvas.clipRect(bounds)
            canvas.translate(bounds.left, bounds.bottom)
            canvas.scale(1f, -1f)
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, bounds.width(), bounds.height()), paint)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    override fun close() {
        try {
            renderer?.close()
        } finally {
            renderer = null
            output?.recycle()
            output = null
        }
    }

    companion object {
        private const val SNAPSHOT_AND_RASTER_BYTES_PER_PIXEL = 16L

        private fun texture(source: CurlPageSource) = if (source.nativeValue < 2) 0 else 1

        private fun snapshotSize(width: Int, height: Int, budget: Long): Pair<Int, Int> {
            require(width > 0 && height > 0)
            val maxPixels = budget / SNAPSHOT_AND_RASTER_BYTES_PER_PIXEL
            check(maxPixels >= 1) { "Insufficient iOSCurl bitmap budget" }
            val scale = sqrt(maxPixels.toDouble() / (width.toDouble() * height)).coerceAtMost(1.0)
            val w = floor(width * scale).toInt().coerceAtLeast(1)
            val h = floor(height * scale).toInt().coerceAtLeast(1)
            check(w.toLong() * h <= maxPixels) { "Insufficient iOSCurl bitmap budget" }
            return w to h
        }

        private fun capture(view: View, width: Int, height: Int): Bitmap {
            check(view.width > 0 && view.height > 0) { "Page has not been laid out" }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.scale(width.toFloat() / view.width, height.toFloat() / view.height)
                view.draw(canvas)
                canvas.setBitmap(null)
                return bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        }
    }
}
