package org.peyilo.readview

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.basic.page.ReadPage
import org.peyilo.libreadview.turning.NoAnimEffects
import org.peyilo.libreadview.turning.ScrollEffect

@RunWith(AndroidJUnit4::class)
class ScrollModeHandoffTest {

    @Test
    fun scrollingKeepsCurrentPageAndSwitchingBackRestoresPagedContentAtVisibleText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(targetContext, org.peyilo.readview.test.TestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as org.peyilo.readview.test.TestActivity

        var readView: BasicReadView? = null
        instrumentation.runOnMainSync {
            readView = BasicReadView(activity).apply {
                pageEffect = NoAnimEffects.Horizontal()
            }
            activity.setContentView(readView)
            readView!!.openAssetFile("txts/妖精之诗 作者：尼希维尔特.txt")
        }

        try {
            waitUntil(instrumentation) {
                runCatching {
                    readView?.book != null && readView?.getCurPage() is ReadPage
                        && readView!!.getCurChapPageCount() > 1
                }.getOrDefault(false)
            }

            var initialPageIndex = 0
            var pagedElementCount = 0
            var page: ReadPage? = null
            instrumentation.runOnMainSync {
                readView!!.navigateBook(1, 2)
                page = readView!!.getCurPage() as ReadPage
                initialPageIndex = readView!!.getCurContainerPageIndex()
                pagedElementCount = page!!.body.content!!.elements.size
            }
            instrumentation.waitForIdleSync()

            instrumentation.runOnMainSync {
                readView!!.pageEffect = ScrollEffect()
            }
            instrumentation.waitForIdleSync()

            var scrollEffect: ScrollEffect? = null
            var longContentElementCount = 0
            var bodyMaxScroll = 0
            var headerTop = 0
            var footerBottom = 0
            var headerScreenTop = 0
            var footerScreenBottom = 0
            var bodyScreenTop = 0
            instrumentation.runOnMainSync {
                scrollEffect = readView!!.pageEffect as ScrollEffect
                assertEquals(initialPageIndex, readView!!.getCurContainerPageIndex())
                assertSame(page, readView!!.getCurPage())
                longContentElementCount = page!!.body.content!!.elements.size
                bodyMaxScroll = page!!.body.getMaxScrollY()
                headerTop = page!!.header.top
                footerBottom = page!!.footer.bottom
                val headerLocation = IntArray(2)
                val footerLocation = IntArray(2)
                val bodyLocation = IntArray(2)
                page!!.header.getLocationOnScreen(headerLocation)
                page!!.footer.getLocationOnScreen(footerLocation)
                page!!.body.getLocationOnScreen(bodyLocation)
                headerScreenTop = headerLocation[1]
                footerScreenBottom = footerLocation[1] + page!!.footer.height
                bodyScreenTop = bodyLocation[1]
                assertEquals(3, readView!!.getPageChildCount())
                for (index in 0 until readView!!.getPageChildCount()) {
                    val child = readView!!.getPageChildAt(index)
                    if (child !== page) assertEquals(View.INVISIBLE, child.visibility)
                }
                assertTrue("scroll layout must contain more than one page", longContentElementCount > pagedElementCount)
                assertTrue("chapter content must extend beyond the body viewport", bodyMaxScroll > 0)
                assertTrue("header and body must not overlap", page!!.header.bottom <= page!!.body.top)
                assertTrue("body and footer must not overlap", page!!.body.bottom <= page!!.footer.top)
            }

            instrumentation.runOnMainSync {
                val body = page!!.body
                val downTime = SystemClock.uptimeMillis()
                scrollEffect!!.onTouchEvent(touch(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 650f))
                val endY = 650f - bodyMaxScroll - 1
                scrollEffect!!.onTouchEvent(touch(downTime, downTime + 500, MotionEvent.ACTION_MOVE, 200f, endY))
                scrollEffect!!.onTouchEvent(touch(downTime, downTime + 1000, MotionEvent.ACTION_UP, 200f, endY))
                assertTrue("body should scroll without moving the page shell", body.scrollY > 0)
                assertEquals(bodyMaxScroll, body.scrollY)
                assertEquals(initialPageIndex, readView!!.getCurContainerPageIndex())
                assertEquals(0f, page!!.translationY)
                assertEquals(headerTop, page!!.header.top)
                assertEquals(footerBottom, page!!.footer.bottom)
                val headerLocation = IntArray(2)
                val footerLocation = IntArray(2)
                val bodyLocation = IntArray(2)
                page!!.header.getLocationOnScreen(headerLocation)
                page!!.footer.getLocationOnScreen(footerLocation)
                page!!.body.getLocationOnScreen(bodyLocation)
                assertEquals(headerScreenTop, headerLocation[1])
                assertEquals(footerScreenBottom, footerLocation[1] + page!!.footer.height)
                assertEquals(bodyScreenTop, bodyLocation[1])
            }

            instrumentation.runOnMainSync {
                readView!!.pageEffect = NoAnimEffects.Horizontal()
            }
            instrumentation.waitForIdleSync()

            var pageAfterExit: ReadPage? = null
            var pageIndexAfterExit = 0
            var pagedCountAfterExit = 0
            instrumentation.runOnMainSync {
                assertTrue(
                    "leaving scroll mode should map visible text to its page: initial=$initialPageIndex, " +
                        "current=${readView!!.getCurContainerPageIndex()}, scrollY=${pageAfterExit?.body?.scrollY}, " +
                        "progress=${page?.progress?.text}",
                    readView!!.getCurContainerPageIndex() > initialPageIndex
                )
                pageAfterExit = readView!!.getCurPage() as ReadPage
                pageIndexAfterExit = readView!!.getCurContainerPageIndex()
                pagedCountAfterExit = pageAfterExit!!.body.content!!.elements.size
                assertFalse(pageAfterExit!!.body.isScrollMode)
                assertEquals(0, pageAfterExit!!.body.scrollY)
                assertTrue("paged content should be restored", pagedCountAfterExit < longContentElementCount)
                for (index in 0 until readView!!.getPageChildCount()) {
                    assertEquals(View.VISIBLE, readView!!.getPageChildAt(index).visibility)
                }
            }

            instrumentation.runOnMainSync {
                readView!!.pageEffect = ScrollEffect()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { pageAfterExit!!.body.scrollTo(0, 0) }
            waitUntil(instrumentation) {
                pageAfterExit?.body?.scrollPageSegments?.firstOrNull()?.chapterIndex == 1
            }
            instrumentation.runOnMainSync {
                assertEquals(pageIndexAfterExit, readView!!.getCurContainerPageIndex())
                assertSame(pageAfterExit, readView!!.getCurPage())
                assertNotNull(pageAfterExit!!.body.content)
                assertTrue(pageAfterExit!!.body.content!!.elements.size > pagedCountAfterExit)

                val effect = readView!!.pageEffect as ScrollEffect
                val body = pageAfterExit!!.body
                val startY = 250f
                val endY = startY + body.scrollY + 1
                val downTime = SystemClock.uptimeMillis()
                effect.onTouchEvent(touch(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, startY))
                effect.onTouchEvent(touch(downTime, downTime + 500, MotionEvent.ACTION_MOVE, 200f, endY))
                effect.onTouchEvent(touch(downTime, downTime + 1000, MotionEvent.ACTION_UP, 200f, endY))
                assertEquals("scrolling back to the beginning should affect only the body", 0, body.scrollY)
                assertEquals("visible chapter should return to the first chapter at the top", 1, readView!!.getCurChapIndex())
                assertEquals(pageIndexAfterExit, readView!!.getCurContainerPageIndex())
            }

            instrumentation.runOnMainSync {
                readView!!.pageEffect = NoAnimEffects.Horizontal()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("switching out at the top should restore the first page", 1, readView!!.getCurContainerPageIndex())
                assertFalse((readView!!.getCurPage() as ReadPage).body.isScrollMode)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun touch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0)

    private fun waitUntil(instrumentation: android.app.Instrumentation, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("ReadView did not finish loading within 15 seconds")
    }
}
