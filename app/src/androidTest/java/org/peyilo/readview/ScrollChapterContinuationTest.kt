package org.peyilo.readview

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.libreadview.basic.page.ReadPage
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
import org.peyilo.libreadview.load.BookLoader
import org.peyilo.libreadview.turning.NoAnimEffects
import org.peyilo.libreadview.turning.ScrollEffect
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ScrollChapterContinuationTest {

    @Test
    fun startingAtLaterChapterCanPrependPreviousChapterWithoutLosingCurrentText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(targetContext, org.peyilo.readview.test.TestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as org.peyilo.readview.test.TestActivity

        var readView: BasicReadView? = null
        instrumentation.runOnMainSync {
            readView = BasicReadView(activity).apply {
                pageEffect = ScrollEffect()
                preprocessBefore = 0
                preprocessBehind = 0
            }
            activity.setContentView(readView)
            readView!!.openBook(ImmediateBookLoader(), chapIndex = 2)
        }

        try {
            waitUntil(instrumentation) {
                runCatching {
                    (readView?.getCurPage() as? ReadPage)?.body?.isScrollMode == true
                }.getOrDefault(false)
            }
            var currentPage: ReadPage? = null
            instrumentation.runOnMainSync {
                currentPage = readView!!.getCurPage() as ReadPage
                assertTrue(currentPage!!.body.isScrollMode)
                assertEquals(2, readView!!.getCurChapIndex())
            }

            waitUntil(instrumentation) {
                currentPage?.body?.scrollPageSegments?.firstOrNull()?.chapterIndex == 1
            }
            instrumentation.runOnMainSync {
                assertTrue("the current chapter should stay visible while the previous one is prepended", currentPage!!.body.isScrollMode)
                assertEquals(2, readView!!.getCurChapIndex())
                assertTrue("prepending should preserve the visible chapter's scroll position", currentPage!!.body.scrollY > 0)

                currentPage!!.body.scrollTo(0, 0)
                assertEquals("scrolling above the chapter boundary should reveal the previous chapter", 1, readView!!.getCurChapIndex())
                assertEquals(1, readView!!.getCurChapPageIndex())

                readView!!.pageEffect = NoAnimEffects.Horizontal()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(1, readView!!.getCurChapIndex())
                assertEquals(1, readView!!.getCurChapPageIndex())
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun delayedChapterLoadShowsRetryThenContinuesAndRestoresPagedPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(targetContext, org.peyilo.readview.test.TestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as org.peyilo.readview.test.TestActivity
        val loader = RetryOnceBookLoader()

        var readView: BasicReadView? = null
        instrumentation.runOnMainSync {
            readView = BasicReadView(activity).apply {
                pageEffect = ScrollEffect()
                preprocessBefore = 0
                preprocessBehind = 0
            }
            activity.setContentView(readView)
            readView!!.openBook(loader)
        }

        try {
            waitUntil(instrumentation) { readView?.book != null && readView?.getCurPage() is ReadPage }
            assertTrue("the next chapter should start loading in the background", loader.secondChapterLoadStarted.await(5, TimeUnit.SECONDS))

            var page: ReadPage? = null
            instrumentation.runOnMainSync {
                page = readView!!.getCurPage() as ReadPage
                page!!.body.scrollTo(0, page!!.body.getMaxScrollY())
                assertTrue(page!!.body.isScrollMode)
                assertEquals(1, page!!.body.scrollPageSegments.size)
                assertEquals("加载下一章…", page!!.progress.text.toString())
            }

            loader.releaseFirstSecondChapterAttempt.countDown()
            waitUntil(instrumentation) {
                page?.progress?.text?.toString() == "加载失败·点击重试"
            }
            instrumentation.runOnMainSync {
                assertEquals(1, readView!!.getCurChapIndex())
                assertTrue(page!!.progress.isClickable)
                assertTrue(page!!.progress.performClick())
            }

            waitUntil(instrumentation) { page?.body?.scrollPageSegments?.size == 2 }
            instrumentation.runOnMainSync {
                val secondSegment = page!!.body.scrollPageSegments[1]
                page!!.body.scrollTo(0, (secondSegment.offsetY + page!!.body.height * 2).toInt())
                assertEquals(
                    "visible chapter should advance: scrollY=${page!!.body.scrollY}, max=${page!!.body.getMaxScrollY()}, " +
                        "secondOffset=${secondSegment.offsetY}, secondBottom=${secondSegment.contentBottom}, " +
                        "progress=${page!!.progress.text}",
                    2,
                    readView!!.getCurChapIndex()
                )
                assertTrue("chapter progress should follow the visible text", readView!!.getCurChapPageIndex() > 1)
                assertEquals(readView!!.getChapTitle(2), page!!.chapTitle.text.toString())
            }

            instrumentation.runOnMainSync {
                readView!!.pageEffect = NoAnimEffects.Horizontal()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("switching to paged mode should map to the visible chapter", 2, readView!!.getCurChapIndex())
                assertFalse((readView!!.getCurPage() as ReadPage).body.isScrollMode)
                assertEquals(0, (readView!!.getCurPage() as ReadPage).body.scrollY)
            }
        } finally {
            loader.releaseFirstSecondChapterAttempt.countDown()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private class RetryOnceBookLoader : BookLoader {
        val secondChapterLoadStarted = CountDownLatch(1)
        val releaseFirstSecondChapterAttempt = CountDownLatch(1)
        private val secondChapterAttempts = AtomicInteger()

        override fun initToc(): Book = Book("滚动章节测试书").apply {
            addBookNode(makeChapter("第一章"))
            addBookNode(makeChapter("第二章"))
        }

        override fun loadChap(chapter: Chapter): Chapter {
            if (chapter.title == "第二章" && secondChapterAttempts.getAndIncrement() == 0) {
                secondChapterLoadStarted.countDown()
                if (!releaseFirstSecondChapterAttempt.await(10, TimeUnit.SECONDS)) {
                    throw IOException("Timed out waiting to simulate a slow chapter")
                }
                throw IOException("Simulated transient chapter load failure")
            }
            return chapter
        }

        private fun makeChapter(title: String) = Chapter(title).apply {
            repeat(120) { index ->
                addParagraph("第${index + 1}段连续滚动测试正文，用于验证章节加载完成后内容能够接在前一章末尾，并保持阅读位置稳定。")
            }
        }
    }

    private class ImmediateBookLoader : BookLoader {
        override fun initToc(): Book = Book("章节回滚测试书").apply {
            addBookNode(makeChapter("第一章"))
            addBookNode(makeChapter("第二章"))
        }

        override fun loadChap(chapter: Chapter): Chapter = chapter

        private fun makeChapter(title: String) = Chapter(title).apply {
            repeat(80) { index ->
                addParagraph("第${index + 1}段章节连续滚动正文，检查按需加载前一章时当前文字位置不会跳动。")
            }
        }
    }

    private fun waitUntil(instrumentation: android.app.Instrumentation, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Scroll chapter continuation did not reach the expected state")
    }
}
