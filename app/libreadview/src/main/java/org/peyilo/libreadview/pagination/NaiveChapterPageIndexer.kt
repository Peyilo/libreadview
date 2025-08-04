package org.peyilo.libreadview.pagination

class NaiveChapterPageIndexer(private val chapCount: Int) : ChapterPageIndexer {

    private val pageCounts = IntArray(chapCount + 1)  // 1-based
    private var totalPageCount = 0

    override fun getAllChapPageCount(): Int = totalPageCount

    override fun updateChapterPageCount(chapIndex: Int, newPageCount: Int) {
        require(chapIndex in 1..chapCount)
        val delta = newPageCount - pageCounts[chapIndex]
        pageCounts[chapIndex] = newPageCount
        totalPageCount += delta
    }

    override fun getChapterPageRange(chapIndex: Int): IntRange {
        require(chapIndex in 1..chapCount)
        var start = 1
        for (i in 1 until chapIndex) {
            start += pageCounts[i]
        }
        val end = start + pageCounts[chapIndex] - 1
        return start..end
    }

    override fun findChapterForPage(pageIndex: Int): Pair<Int, Int> {
        require(pageIndex >= 1 && pageIndex <= totalPageCount)

        var acc = 0
        for (i in 1..chapCount) {
            val next = acc + pageCounts[i]
            if (pageIndex <= next) {
                val pageInChapter = pageIndex - acc
                return Pair(i, pageInChapter)
            }
            acc = next
        }
        throw IllegalStateException("PageIndex $pageIndex out of range")
    }
}
