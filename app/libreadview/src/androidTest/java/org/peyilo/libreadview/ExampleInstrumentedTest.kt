package org.peyilo.libreadview

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.libreadview.util.DisplayUtil

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    companion object {
        private const val TAG = "ExampleInstrumentedTest"
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.peyilo.libreadview.test", appContext.packageName)
    }

    fun getPageRange(index: Int, maxPages: Int, numPages: Int): List<Int> {
        val half = maxPages / 2
        var start = index - half
        var end = start + maxPages - 1

        // 调整 start 和 end 使其在合法范围内
        if (start < 1) {
            start = 1
            end = minOf(maxPages, numPages)
        } else if (end > numPages) {
            end = numPages
            start = maxOf(1, end - maxPages + 1)
        }

        return (start..end).toList()
    }

    @Test
    fun testGetPageRange() {
        var pageRange = getPageRange(5000, 3, 10000)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 4999)
        assert(pageRange[1] == 5000)
        assert(pageRange[2] == 5001)

        pageRange = getPageRange(5, 5, 10)
        assert(pageRange.size == 5)
        assert(pageRange[0] == 3)
        assert(pageRange[1] == 4)
        assert(pageRange[2] == 5)
        assert(pageRange[3] == 6)
        assert(pageRange[4] == 7)

        pageRange = getPageRange(1, 5, 10)
        assert(pageRange.size == 5)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)
        assert(pageRange[3] == 4)
        assert(pageRange[4] == 5)

        pageRange = getPageRange(2, 5, 10)
        assert(pageRange.size == 5)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)
        assert(pageRange[3] == 4)
        assert(pageRange[4] == 5)

        pageRange = getPageRange(9, 5, 10)
        assert(pageRange.size == 5)
        assert(pageRange[0] == 6)
        assert(pageRange[1] == 7)
        assert(pageRange[2] == 8)
        assert(pageRange[3] == 9)
        assert(pageRange[4] == 10)

        pageRange = getPageRange(1, 5, 3)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)

        pageRange = getPageRange(1, 3, 2)
        assert(pageRange.size == 2)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)

        pageRange = getPageRange(1, 3, 1)
        assert(pageRange.size == 1)
        assert(pageRange[0] == 1)

        pageRange = getPageRange(1, 3, 3)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)

        pageRange = getPageRange(1, 3, 5)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)

        pageRange = getPageRange(2, 3, 10)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 1)
        assert(pageRange[1] == 2)
        assert(pageRange[2] == 3)

        pageRange = getPageRange(6, 3, 10)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 5)
        assert(pageRange[1] == 6)
        assert(pageRange[2] == 7)

        pageRange = getPageRange(9, 3, 10)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 8)
        assert(pageRange[1] == 9)
        assert(pageRange[2] == 10)

        pageRange = getPageRange(10, 3, 10)
        assert(pageRange.size == 3)
        assert(pageRange[0] == 8)
        assert(pageRange[1] == 9)
        assert(pageRange[2] == 10)
    }

    private fun assertRegexMatchs(str: String, regex: String) {
        assert(Regex(regex).matches( str))
    }

    @Test
    fun testRegex() {
        assertRegexMatchs("序·故事开始前的故事", "序·故事开始前的故事")
        assertRegexMatchs("3.摔倒的惩罚", "^\\d+\\..+")
    }

    @Test
    fun testDimen() {
        val spToPx =
            DisplayUtil.spToPx(InstrumentationRegistry.getInstrumentation().targetContext, 16f)
        Log.d(TAG, "testDimen: $spToPx")
    }

}