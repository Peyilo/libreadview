package org.peyilo.readview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.PageContainer
import org.peyilo.libreadview.turning.EffectFactory
import org.peyilo.libreadview.turning.GoogleCurlEffect
import org.peyilo.libreadview.turning.IosCurlBinding
import org.peyilo.libreadview.turning.IosCurlEffect
import org.peyilo.libreadview.turning.IosCurlOptions
import org.peyilo.libreadview.turning.NoAnimEffects
import org.peyilo.readview.test.TestActivity

@RunWith(AndroidJUnit4::class)
class IosCurlIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test
    fun factoryRetainsLegacyIdAndOnlyTwoCurlImplementations() {
        IosCurlEffect().setAnimDuration(0)
        assertTrue(EffectFactory.create(EffectFactory.IOS_CURL) is IosCurlEffect)
        assertTrue(EffectFactory.create(2) is IosCurlEffect)
        assertTrue(EffectFactory.create(EffectFactory.GOOGLE_CURL) is GoogleCurlEffect)
    }

    @Test
    fun automaticTurnsCoverAxesBindingsRtlAndBothDirections() = withReader { reader ->
        for (vertical in listOf(false, true)) for (binding in IosCurlBinding.entries) {
            for (rtl in listOf(false, true)) for (forward in listOf(false, true)) {
                main {
                    reader.pageEffect.forceNotInLayoutOrScroll()
                    reader.navigatePage(3)
                    reader.pageEffect = IosCurlEffect(IosCurlOptions(vertical, binding, rtl))
                }
                instrumentation.waitForIdleSync()
                main {
                    assertTrue(
                        if (forward) reader.flipToNextPage(false) else reader.flipToPrevPage(false)
                    )
                    assertFalse(
                        "must render with the real iOS mesh",
                        reader.pageEffect.needDrawChild(),
                    )
                }
                await { reader.pageEffect.needDrawChild() }
                main {
                    assertEquals(if (forward) 4 else 2, reader.getCurContainerPageIndex())
                    assertEquals(0f, reader.getCurPage()!!.translationX)
                    assertEquals(0f, reader.getCurPage()!!.translationY)
                    assertEquals(3, reader.childCount)
                }
            }
        }
    }

    @Test
    fun cancellingAndRetryingKeepsBothPageSnapshotsVisibleInEitherDirection() =
        withReader { reader ->
            for (forward in listOf(true, false)) {
                for (attempt in 0 until 3) {
                    main { reader.navigatePage(3) }
                    instrumentation.waitForIdleSync()
                    val gesture = drag(reader, forward)
                    assertBothPagesVisible(reader)
                    gesture.end(MotionEvent.ACTION_CANCEL)
                    await { reader.pageEffect.needDrawChild() }
                    main { assertEquals(3, reader.getCurContainerPageIndex()) }
                }
                val retry = drag(reader, forward)
                assertBothPagesVisible(reader)
                retry.end(MotionEvent.ACTION_UP)
                await { reader.pageEffect.needDrawChild() }
                main { assertEquals(if (forward) 4 else 2, reader.getCurContainerPageIndex()) }
            }
        }

    @Test
    fun reversalCancelsAndMultiTouchDoesNotReplaceThePrimaryFinger() = withReader { reader ->
        val reversing = drag(reader, forward = true)
        reversing.move(0.65f, 0.58f, 650)
        reversing.move(0.85f, 0.58f, 800)
        reversing.move(0.96f, 0.58f, 1000)
        reversing.end(MotionEvent.ACTION_UP)
        await { reader.pageEffect.needDrawChild() }
        main { assertEquals(3, reader.getCurContainerPageIndex()) }

        // A second pointer before the recognizer begins must fail the whole gesture.
        val time = SystemClock.uptimeMillis()
        main {
            send(reader, time, 0, MotionEvent.ACTION_DOWN, 0.94f, 0.58f)
            sendTwo(reader, time, 40, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 0.94f)
            sendTwo(reader, time, 100, MotionEvent.ACTION_MOVE, 0.35f)
            sendTwo(reader, time, 160, MotionEvent.ACTION_POINTER_UP or (1 shl 8), 0.35f)
            send(reader, time, 200, MotionEvent.ACTION_UP, 0.35f, 0.58f)
            assertEquals(3, reader.getCurContainerPageIndex())
            assertTrue(reader.pageEffect.needDrawChild())
        }
        val active = drag(reader, forward = true)
        main {
            sendTwo(reader, active.down, 500, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 0.35f)
            sendTwo(reader, active.down, 550, MotionEvent.ACTION_POINTER_UP or (1 shl 8), 0.35f)
        }
        active.end(MotionEvent.ACTION_UP)
        await { reader.pageEffect.needDrawChild() }
        main { assertEquals(4, reader.getCurContainerPageIndex()) }
    }

    @Test
    fun boundariesAndBudgetFallbackRetainCommitAndCancelDecisions() = withReader { reader ->
        main {
            reader.pageEffect = IosCurlEffect().apply { setAnimDuration(0) }
            assertTrue(reader.flipToNextPage(false))
            assertEquals(4, reader.getCurContainerPageIndex())
            assertTrue(reader.pageEffect.needDrawChild())
            reader.navigatePage(1)
            assertFalse(reader.flipToPrevPage(false))
            reader.navigatePage(6)
            assertFalse(reader.flipToNextPage(false))
            reader.navigatePage(3)
            reader.pageEffect =
                IosCurlEffect(IosCurlOptions(bitmapBudgetBytes = 1)).also {
                    it.minPageTurnInterval = 0
                }
        }
        instrumentation.waitForIdleSync()
        val cancelled = drag(reader, true)
        main { assertTrue(reader.pageEffect.needDrawChild()) }
        cancelled.end(MotionEvent.ACTION_CANCEL)
        main { assertEquals(3, reader.getCurContainerPageIndex()) }
        val committed = drag(reader, true)
        committed.end(MotionEvent.ACTION_UP)
        main {
            assertEquals(4, reader.getCurContainerPageIndex())
            assertTrue(reader.flipToPrevPage(false))
            assertEquals(3, reader.getCurContainerPageIndex())
        }
    }

    @Test
    fun effectSwitchAndResizeCloseRasterTransactions() = withReader { reader ->
        drag(reader, true)
        main {
            reader.pageEffect = GoogleCurlEffect()
            assertEquals(3, reader.getCurContainerPageIndex())
            reader.pageEffect = IosCurlEffect()
            reader.flipToNextPage(false)
            reader.pageEffect = NoAnimEffects.Horizontal()
            assertEquals(4, reader.getCurContainerPageIndex())
            reader.pageEffect = IosCurlEffect()
        }
        instrumentation.waitForIdleSync()
        drag(reader, true)
        main {
            reader.layout(reader.left, reader.top, reader.right - 20, reader.bottom)
            reader.pageEffect.computeScroll()
            assertTrue(reader.pageEffect.needDrawChild())
            assertEquals(4, reader.getCurContainerPageIndex())
        }
    }

    @Test
    fun datasetRefreshAndExplicitNavigationCannotCommitAStaleRasterDestination() =
        withReader { reader ->
            main {
                assertTrue(reader.flipToNextPage(false))
                assertFalse(reader.pageEffect.needDrawChild())
                reader.adapter.notifyDataSetChanged()
                assertEquals(3, reader.getCurContainerPageIndex())
                assertTrue(reader.pageEffect.needDrawChild())
            }
            instrumentation.waitForIdleSync()
            main {
                assertTrue(reader.flipToNextPage(false))
                reader.navigatePage(2)
                assertEquals(2, reader.getCurContainerPageIndex())
                assertTrue(reader.pageEffect.needDrawChild())
            }
        }

    @Test
    fun paddingThemeSamplingAndHostTapCallbacksUsePageCoordinates() = withReader { reader ->
        var clicked = 0
        main {
            reader.setPadding(25, 40, 15, 30)
            reader.setOnClickRegionListener { x, y ->
                assertTrue(x in 45..55 && y in 45..55)
                clicked++
                true
            }
        }
        instrumentation.waitForIdleSync()
        val time = SystemClock.uptimeMillis()
        main {
            send(reader, time, 0, MotionEvent.ACTION_DOWN, 0.5f, 0.5f)
            send(reader, time, 50, MotionEvent.ACTION_UP, 0.5f, 0.5f)
            assertEquals(1, clicked)
            assertEquals(3, reader.getCurContainerPageIndex())
        }
        val gesture = drag(reader, true)
        assertBothPagesVisible(reader)
        gesture.end(MotionEvent.ACTION_CANCEL)
        await { reader.pageEffect.needDrawChild() }
    }

    private fun withReader(block: (PageContainer) -> Unit) {
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as TestActivity
        lateinit var reader: PageContainer
        main {
            reader =
                PageContainer(activity).apply {
                    pageEffect = IosCurlEffect().also { it.minPageTurnInterval = 0 }
                    initPageIndex(3)
                }
            activity.setContentView(reader)
            reader.adapter =
                object : AbstractPageContainer.Adapter<Holder>() {
                    override fun getItemCount() = 6

                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                        Holder(View(parent.context))

                    override fun onBindViewHolder(holder: Holder, position: Int) {
                        holder.itemView.setBackgroundColor(if (position % 2 == 0) RED else BLUE)
                    }
                }
        }
        try {
            await { reader.width > 0 && reader.getCurPage()!!.width > 0 }
            block(reader)
        } finally {
            main {
                reader.pageEffect.forceNotInLayoutOrScroll()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private class Holder(view: View) : AbstractPageContainer.ViewHolder(view)

    private inner class Gesture(val reader: PageContainer, val down: Long, var x: Float) {
        fun move(x: Float, y: Float, offset: Long) {
            this.x = x
            main { send(reader, down, offset, MotionEvent.ACTION_MOVE, x, y) }
        }

        fun end(action: Int) = main { send(reader, down, 1200, action, x, 0.58f) }
    }

    private fun drag(reader: PageContainer, forward: Boolean): Gesture {
        val time = SystemClock.uptimeMillis()
        val start = if (forward) 0.94f else 0.06f
        val middle = if (forward) 0.72f else 0.28f
        val end = if (forward) 0.35f else 0.65f
        main { send(reader, time, 0, MotionEvent.ACTION_DOWN, start, 0.58f) }
        val gesture = Gesture(reader, time, start)
        gesture.move(middle, 0.58f, 100)
        Thread.sleep(300)
        gesture.move(end, 0.58f, 450)
        return gesture
    }

    private fun send(
        reader: PageContainer,
        down: Long,
        offset: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val page = reader.getCurPage()!!
        val event =
            MotionEvent.obtain(
                down,
                down + offset,
                action,
                page.left + x * page.width,
                page.top + y * page.height,
                0,
            )
        try {
            reader.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun sendTwo(reader: PageContainer, down: Long, offset: Long, action: Int, x: Float) {
        val page = reader.getCurPage()!!
        val properties =
            Array(2) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coordinates =
            Array(2) { index ->
                MotionEvent.PointerCoords().apply {
                    this.x = page.left + (if (index == 0) x else 0.8f) * page.width
                    y = page.top + 0.58f * page.height
                    pressure = 1f
                    size = 1f
                }
            }
        val event =
            MotionEvent.obtain(
                down,
                down + offset,
                action,
                2,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            reader.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun assertBothPagesVisible(reader: PageContainer) = main {
        assertFalse("real mesh must remain active", reader.pageEffect.needDrawChild())
        val bitmap = Bitmap.createBitmap(reader.width, reader.height, Bitmap.Config.ARGB_8888)
        try {
            reader.draw(Canvas(bitmap))
            var red = 0
            var blue = 0
            for (y in 0 until bitmap.height step 5) for (x in 0 until bitmap.width step 5) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) < 100) continue
                if (Color.red(c) > Color.blue(c) * 1.4) red++
                if (Color.blue(c) > Color.red(c) * 1.4) blue++
            }
            assertTrue("turning red page disappeared: red=$red blue=$blue", red > 30)
            assertTrue("blue underlay disappeared: red=$red blue=$blue", blue > 30)
        } finally {
            bitmap.recycle()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            Thread.sleep(35)
        }
        fail("Timed out waiting for reader state")
    }

    companion object {
        val RED = Color.rgb(210, 45, 55)
        val BLUE = Color.rgb(35, 80, 215)
    }
}
